package pl.local.liberateus.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import pl.local.liberateus.EXTRA_MESSAGE_ID
import pl.local.liberateus.MainActivity
import pl.local.liberateus.data.MessageCheckLog
import pl.local.liberateus.data.MessageFetchSource
import pl.local.liberateus.data.remote.LibrusConnection
import pl.local.liberateus.data.remote.MessagesRemoteDataSource
import pl.local.liberateus.data.remote.SessionAccess
import pl.local.liberateus.data.remote.SessionManager
import pl.local.liberateus.data.repository.InboxSyncResult
import pl.local.liberateus.data.repository.MessageRepository
import pl.local.liberateus.domain.model.InboxItem

private const val TAG =
    "LiberatePeriodic"

private const val UNIQUE_WORK =
    "liberate_background_message_sync_v03"

private const val LEGACY_UNIQUE_WORK =
    "librus_random_periodic_message_check"

private const val WORK_TAG =
    "liberate_background_messages"

private const val CHANNEL_ID =
    "liberate_messages"

private const val CHANNEL_NAME =
    "Wiadomości Librus"

/*
 * WorkManager executes a periodic request inside the flex
 * window at the end of each repeat interval.
 *
 * repeat = 45 min
 * flex   = 15 min
 *
 * Therefore the execution window is 30–45 minutes.
 *
 * Android may still defer work because of Doze/battery
 * restrictions. The application itself never requests a
 * longer interval.
 */
internal const val BACKGROUND_REPEAT_MINUTES =
    45L

internal const val BACKGROUND_FLEX_MINUTES =
    15L

internal const val BACKGROUND_EARLIEST_MINUTES =
    BACKGROUND_REPEAT_MINUTES -
        BACKGROUND_FLEX_MINUTES

/**
 * Liberate Us 0.3 background synchronization.
 *
 * Architecture:
 *
 * WorkManager
 *     -> SessionManager
 *     -> MessagesRemoteDataSource
 *     -> MessageRepository
 *     -> Room
 *
 * Important:
 *
 * - inbox metadata only;
 * - never downloads message bodies;
 * - never marks a message read remotely;
 * - repository owns baseline/new-message semantics;
 * - notifications come ONLY from InboxSyncResult.newMessages.
 */
class PeriodicMessageCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(
    appContext,
    params
) {

    override suspend fun doWork():
        Result {

        val context =
            applicationContext

        val log =
            MessageCheckLog(
                context
            )

        log.write(
            source =
                MessageFetchSource.PERIODIC,
            event =
                "WORKER_START",
            details =
                "attempt=$runAttemptCount"
        )

        /*
         * Logout removes saved credentials and also cancels
         * this work. This additional check protects against
         * a worker that was already dispatched while logout
         * was happening.
         */
        val connection =
            LibrusConnection(
                context
            )

        val sessionManager =
            SessionManager(
                connection
            )

        val accountId =
            sessionManager
                .savedUsername()
                ?.trim()
                .orEmpty()

        if (
            accountId.isBlank()
            ||
            !sessionManager
                .hasSavedCredentials()
        ) {

            log.write(
                source =
                    MessageFetchSource.PERIODIC,
                event =
                    "SKIP_NO_ACCOUNT"
            )

            Log.i(
                TAG,
                "Background sync skipped: no saved account"
            )

            return Result.success()
        }

        val remote =
            MessagesRemoteDataSource(
                connection
            )

        val repository =
            MessageRepository(
                context =
                    context,
                remote =
                    remote,
                sessionManager =
                    sessionManager
            )

        val hasLocalAccount =
            try {
                repository
                    .localInboxCount(
                        accountId
                    ) > 0
            } catch (
                error: Exception
            ) {
                false
            }

        /*
         * The worker does not implement OAuth itself.
         * SessionManager is the one authoritative recovery
         * path shared with the foreground application.
         */
        val recovery =
            sessionManager
                .recoverSavedSession(
                    hasLocalAccount =
                        hasLocalAccount
                )

        if (
            recovery.access !=
            SessionAccess.ONLINE
        ) {

            log.write(
                source =
                    MessageFetchSource.PERIODIC,
                event =
                    "SESSION_NOT_ONLINE",
                details =
                    "access=${recovery.access} " +
                        "source=${recovery.source}"
            )

            /*
             * Periodic WorkManager will try again in its next
             * 30–45 minute execution window.
             *
             * No Result.retry(): retries could violate the
             * requested polling cadence.
             */
            return Result.success()
        }

        if (
            sessionManager
                .savedUsername()
                ?.trim()
            != accountId
        ) {

            log.write(
                source =
                    MessageFetchSource.PERIODIC,
                event =
                    "SKIP_ACCOUNT_CHANGED"
            )

            return Result.success()
        }

        val syncResult =
            syncWithSingleRecoveryRetry(
                repository =
                    repository,
                sessionManager =
                    sessionManager,
                accountId =
                    accountId,
                hasLocalAccount =
                    hasLocalAccount,
                log =
                    log
            )
                ?: return Result.success()

        log.write(
            source =
                MessageFetchSource.PERIODIC,
            event =
                "SYNC_OK",
            details =
                "fetched=${syncResult.fetchedCount} " +
                    "inserted=${syncResult.insertedCount} " +
                    "updated=${syncResult.updatedCount} " +
                    "baseline=${syncResult.wasBaselineSync} " +
                    "new=${syncResult.newMessageCount}"
        )

        /*
         * Repository already guarantees that first baseline
         * synchronization has an empty newMessages list.
         *
         * The explicit wasBaselineSync guard is retained here
         * as an additional notification invariant.
         */
        if (
            !syncResult.wasBaselineSync
            &&
            syncResult.newMessages.isNotEmpty()
        ) {

            ensureNotificationChannel(
                context
            )

            /*
             * Remote inbox is newest-first.
             *
             * Notify older newly discovered messages first so
             * the newest one is the last notification posted.
             */
            syncResult
                .newMessages
                .asReversed()
                .forEach { message ->

                    showMessageNotification(
                        context =
                            context,
                        accountId =
                            accountId,
                        message =
                            message,
                        log =
                            log
                    )
                }
        }

        return Result.success()
    }

    private suspend fun syncWithSingleRecoveryRetry(
        repository: MessageRepository,
        sessionManager: SessionManager,
        accountId: String,
        hasLocalAccount: Boolean,
        log: MessageCheckLog
    ): InboxSyncResult? {

        try {

            return repository
                .syncInbox(
                    accountId =
                        accountId,
                    limit =
                        10,
                    source =
                        MessageFetchSource.PERIODIC
                )

        } catch (
            cancelled: CancellationException
        ) {

            throw cancelled

        } catch (
            first: Exception
        ) {

            log.write(
                source =
                    MessageFetchSource.PERIODIC,
                event =
                    "SYNC_FIRST_FAILURE",
                details =
                    first.message
                        ?: first
                            .javaClass
                            .simpleName
            )
        }

        /*
         * A session that looked restored may still have expired
         * server-side. Use the same post-request-failure recovery
         * path as the foreground application and retry ONCE.
         */
        val recovery =
            sessionManager
                .recoverAfterRequestFailure(
                    hasLocalAccount =
                        hasLocalAccount
                )

        if (
            recovery.access !=
            SessionAccess.ONLINE
        ) {

            log.write(
                source =
                    MessageFetchSource.PERIODIC,
                event =
                    "SYNC_RECOVERY_FAILED",
                details =
                    "access=${recovery.access} " +
                        "source=${recovery.source}"
            )

            return null
        }

        if (
            sessionManager
                .savedUsername()
                ?.trim()
            != accountId
        ) {

            log.write(
                source =
                    MessageFetchSource.PERIODIC,
                event =
                    "SYNC_ACCOUNT_CHANGED"
            )

            return null
        }

        return try {

            repository
                .syncInbox(
                    accountId =
                        accountId,
                    limit =
                        10,
                    source =
                        MessageFetchSource.PERIODIC
                )

        } catch (
            cancelled: CancellationException
        ) {

            throw cancelled

        } catch (
            second: Exception
        ) {

            log.write(
                source =
                    MessageFetchSource.PERIODIC,
                event =
                    "SYNC_FINAL_FAILURE",
                details =
                    second.message
                        ?: second
                            .javaClass
                            .simpleName
            )

            null
        }
    }
}

/**
 * Enables the Liberate Us 0.3 periodic metadata sync.
 *
 * 45 minute repeat interval + 15 minute flex means a normal
 * WorkManager execution window of 30–45 minutes.
 */
fun schedulePeriodicMessageChecks(
    context: Context
) {

    val appContext =
        context.applicationContext

    ensureNotificationChannel(
        appContext
    )

    val constraints =
        Constraints
            .Builder()
            .setRequiredNetworkType(
                NetworkType.CONNECTED
            )
            .build()

    val request =
        PeriodicWorkRequestBuilder<
            PeriodicMessageCheckWorker
        >(
            BACKGROUND_REPEAT_MINUTES,
            TimeUnit.MINUTES,
            BACKGROUND_FLEX_MINUTES,
            TimeUnit.MINUTES
        )
            /*
             * Do not run immediately after login/startup.
             * Earliest intended first run is 30 minutes later.
             */
            .setInitialDelay(
                BACKGROUND_EARLIEST_MINUTES,
                TimeUnit.MINUTES
            )
            .setConstraints(
                constraints
            )
            .addTag(
                WORK_TAG
            )
            .build()

    val workManager =
        WorkManager
            .getInstance(
                appContext
            )

    /*
     * Remove a possible serialized development-era schedule.
     * It used a different scheduling architecture.
     */
    workManager
        .cancelUniqueWork(
            LEGACY_UNIQUE_WORK
        )

    workManager
        .enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

    Log.i(
        TAG,
        "Background sync scheduled: " +
            "$BACKGROUND_EARLIEST_MINUTES-" +
            "$BACKGROUND_REPEAT_MINUTES min"
    )
}

fun cancelPeriodicMessageChecks(
    context: Context
) {

    val workManager =
        WorkManager
            .getInstance(
                context.applicationContext
            )

    workManager
        .cancelUniqueWork(
            UNIQUE_WORK
        )

    workManager
        .cancelUniqueWork(
            LEGACY_UNIQUE_WORK
        )

    Log.i(
        TAG,
        "Background sync cancelled"
    )
}

fun clearMessageNotifications(
    context: Context
) {

    val manager =
        context
            .applicationContext
            .getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

    manager.cancelAll()
}

private fun ensureNotificationChannel(
    context: Context
) {

    val manager =
        context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

    val channel =
        NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {

            description =
                "Powiadomienia o nowych wiadomościach z Librusa"
        }

    manager.createNotificationChannel(
        channel
    )
}

private fun showMessageNotification(
    context: Context,
    accountId: String,
    message: InboxItem,
    log: MessageCheckLog
) {

    if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.TIRAMISU
        &&
        ContextCompat
            .checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
        != PackageManager.PERMISSION_GRANTED
    ) {

        log.write(
            source =
                MessageFetchSource.PERIODIC,
            event =
                "NOTIFICATION_PERMISSION_MISSING",
            details =
                "messageId=${message.id}"
        )

        return
    }

    val manager =
        context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

    val requestCode =
        (
            accountId
            + ":"
            + message.id
        )
            .hashCode()

    val intent =
        Intent(
            context,
            MainActivity::class.java
        ).apply {

            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(
                EXTRA_MESSAGE_ID,
                message.id
            )
        }

    val pendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

    val title =
        message.sender
            .ifBlank {
                "Nowa wiadomość z Librusa"
            }

    val text =
        message.topic
            .ifBlank {
                "Nowa wiadomość"
            }

    val notification =
        Notification
            .Builder(
                context,
                CHANNEL_ID
            )
            .setSmallIcon(
                android.R.drawable.ic_dialog_email
            )
            .setContentTitle(
                title
            )
            .setContentText(
                text
            )
            .setStyle(
                Notification
                    .BigTextStyle()
                    .bigText(
                        text
                    )
            )
            .setContentIntent(
                pendingIntent
            )
            .setAutoCancel(
                true
            )
            .setCategory(
                Notification.CATEGORY_MESSAGE
            )
            .build()

    manager.notify(
        requestCode,
        notification
    )

    log.write(
        source =
            MessageFetchSource.PERIODIC,
        event =
            "NOTIFICATION_POSTED",
        details =
            "messageId=${message.id}"
    )
}
