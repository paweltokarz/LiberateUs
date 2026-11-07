package pl.local.liberateus.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.local.liberateus.data.MessageFetchSource
import pl.local.liberateus.data.local.LiberateDatabase
import pl.local.liberateus.data.local.SyncStateEntity
import pl.local.liberateus.data.mapper.toContentDomain
import pl.local.liberateus.data.mapper.toDomain
import pl.local.liberateus.data.mapper.toEntity
import pl.local.liberateus.data.remote.MessagesRemoteDataSource
import pl.local.liberateus.data.remote.SessionIdentityProvider
import pl.local.liberateus.data.remote.SessionManager
import pl.local.liberateus.domain.model.InboxItem
import pl.local.liberateus.data.mapper.toDomainMessage
import pl.local.liberateus.data.mapper.toInboxDomain
import pl.local.liberateus.domain.model.Message

/**
 * Central message data coordinator.
 *
 * Step 7 responsibilities:
 *
 * - remote inbox metadata -> Room,
 * - Room -> observable domain inbox,
 * - preserve local history,
 * - maintain technical sync state,
 * - explicit full-message fetch.
 *
 * NOT Step 7:
 *
 * - UI switching to Room (Step 8),
 * - body cache (Step 9),
 * - session recovery chain (Step 12),
 * - baseline/new-message notifications (Step 13).
 */
class MessageRepository internal constructor(
    private val store:
        MessageRepositoryStore,
    private val remote:
        MessagesRemoteDataSource,
    private val sessionIdentity:
        SessionIdentityProvider,
    private val clock:
        () -> Long
) {

    constructor(
        context: Context,
        remote:
            MessagesRemoteDataSource,
        sessionManager:
            SessionManager
    ) : this(
        store =
            RoomMessageRepositoryStore(
                LiberateDatabase.getInstance(
                    context.applicationContext
                )
            ),
        remote = remote,
        sessionIdentity =
            sessionManager,
        clock = {
            System.currentTimeMillis()
        }
    )

    /**
     * Convenience for future ViewModels.
     *
     * Repository methods still receive accountId explicitly
     * so account boundaries remain visible in API.
     */
    fun currentAccountId():
        String? =
        sessionIdentity
            .savedUsername()
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }

    fun observeInbox(
        accountId: String
    ): Flow<List<InboxItem>> {

        requireAccountId(
            accountId
        )

        return store
            .observeInbox(
                accountId
            )
            .map { entities ->
                entities.map {
                    it.toDomain()
                }
            }
    }

    /**
     * Number of locally persisted inbox metadata rows.
     *
     * Used to decide whether the initial 10-row view needs
     * a one-time larger history backfill.
     */
    suspend fun localInboxCount(
        accountId: String
    ): Int {

        requireAccountId(
            accountId
        )

        return store.countMessages(
            accountId
        )
    }

    /**
     * Synchronizes metadata only.
     *
     * IMPORTANT:
     * this method calls remote.getInbox() only.
     * It never calls remote.getMessage().
     *
     * Existing local rows absent from the current remote page
     * are deliberately NOT deleted.
     */
    suspend fun syncInbox(
        accountId: String,
        limit: Int = 10,
        source:
            MessageFetchSource =
            MessageFetchSource.MANUAL
    ): InboxSyncResult {

        requireAccountId(
            accountId
        )

        require(
            limit > 0
        ) {
            "limit must be greater than zero"
        }

        val attemptAt =
            clock()

        val previousState =
            store.getSyncState(
                accountId
            )

        /*
         * Baseline status is captured BEFORE this sync.
         *
         * That makes the first successful sync special:
         * all rows may technically be inserts, but none of
         * them are semantically "new".
         */
        val baselineWasInitialized =
            (
                previousState
                    ?.baselineInitialized
                    ?: false
            )

        val stateBeforeAttempt =
            previousState
                ?: SyncStateEntity(
                    accountId =
                        accountId
                )

        store.upsertSyncState(
            stateBeforeAttempt.copy(
                lastAttemptAt =
                    attemptAt,
                lastError =
                    null
            )
        )

        try {

            /*
             * Metadata only.
             *
             * Full message GET marks a Librus message as read,
             * so syncInbox must never fetch bodies.
             */
            val remoteMessages =
                withContext(
                    Dispatchers.IO
                ) {
                    remote.getInbox(
                        limit =
                            limit,
                        source =
                            source
                    )
                }

            var inserted =
                0

            var updated =
                0

            val newMessages =
                mutableListOf<
                    InboxItem
                >()

            val entities =
                remoteMessages
                    .mapIndexed {
                        index,
                        remoteMessage ->

                        val existing =
                            store.getMessage(
                                accountId =
                                    accountId,
                                messageId =
                                    remoteMessage.id
                            )

                        val domain =
                            remoteMessage
                                .toDomain(
                                    remoteOrder =
                                        index,
                                    observedAt =
                                        attemptAt,
                                    firstSeenAt =
                                        existing
                                            ?.firstSeenAt
                                            ?: attemptAt
                                )

                        if (
                            existing == null
                        ) {

                            inserted += 1

                            /*
                             * New-message semantics begin only
                             * AFTER the baseline exists.
                             *
                             * Blank IDs are not useful for later
                             * notification/deep-link handling.
                             */
                            if (
                                baselineWasInitialized
                                &&
                                domain.id.isNotBlank()
                            ) {
                                newMessages +=
                                    domain
                            }

                        } else {

                            updated += 1
                        }

                        domain.toEntity(
                            accountId
                        )
                    }

            /*
             * UPSERT ONLY.
             *
             * Rows absent from the current remote page are
             * deliberately retained as local history.
             */
            store.upsertMessages(
                entities
            )

            val successAt =
                clock()

            /*
             * Baseline becomes initialized ONLY after the
             * entire metadata synchronization succeeds.
             *
             * A failed first sync must therefore remain
             * baselineInitialized=false.
             */
            store.upsertSyncState(
                stateBeforeAttempt.copy(
                    baselineInitialized =
                        true,
                    lastAttemptAt =
                        attemptAt,
                    lastSuccessAt =
                        successAt,
                    lastError =
                        null
                )
            )

            return InboxSyncResult(
                fetchedCount =
                    remoteMessages.size,
                insertedCount =
                    inserted,
                updatedCount =
                    updated,
                wasBaselineSync =
                    !baselineWasInitialized,
                newMessages =
                    newMessages.toList(),
                syncedAt =
                    successAt
            )

        } catch (
            error: Throwable
        ) {

                    if (
                error is CancellationException
            ) {
                throw error
            }

            /*
             * Preserve the baseline state that existed before
             * this attempt.
             *
             * In particular, a failed first synchronization
             * must NOT create a baseline.
             */
            store.upsertSyncState(
                stateBeforeAttempt.copy(
                    lastAttemptAt =
                        attemptAt,
                    lastError =
                        error.message
                            ?: error
                                .javaClass
                                .simpleName
                )
            )

            throw error
        }
    }

    /**
     * Step 7 deliberately does NOT cache body yet.
     *
     * Full message cache-first behavior belongs to Step 9.
     *
     * We do mark metadata as read locally because successful
     * full GET represents opening the message on the server.
     */
    /**
     * Expands local metadata history without participating
     * in new-message notification semantics.
     *
     * This deliberately does NOT touch SyncStateEntity and
     * does NOT return InboxSyncResult.newMessages.
     *
     * The Librus metadata endpoint is safe here: no message
     * body is fetched and no server-side read state is changed.
     */
    suspend fun backfillInboxHistory(
        accountId: String,
        limit: Int = 100,
        source:
            MessageFetchSource =
            MessageFetchSource.MANUAL
    ): HistoryBackfillResult {

        requireAccountId(
            accountId
        )

        require(
            limit > 0
        ) {
            "limit must be greater than zero"
        }

        val observedAt =
            clock()

        val remoteMessages =
            withContext(
                Dispatchers.IO
            ) {
                remote.getInbox(
                    limit =
                        limit,
                    source =
                        source
                )
            }

        var inserted =
            0

        var updated =
            0

        val entities =
            remoteMessages
                .mapIndexed {
                    index,
                    remoteMessage ->

                    val existing =
                        store.getMessage(
                            accountId =
                                accountId,
                            messageId =
                                remoteMessage.id
                        )

                    if (
                        existing == null
                    ) {
                        inserted += 1
                    } else {
                        updated += 1
                    }

                    remoteMessage
                        .toDomain(
                            remoteOrder =
                                index,
                            observedAt =
                                observedAt,
                            firstSeenAt =
                                existing
                                    ?.firstSeenAt
                                    ?: observedAt
                        )
                        .toEntity(
                            accountId
                        )
                }

        /*
         * UPSERT ONLY.
         *
         * Rows not returned by this request remain in Room.
         * This is local history enrichment, never replacement.
         */
        store.upsertMessages(
            entities
        )

        val successAt =
            clock()

        return HistoryBackfillResult(
            fetchedCount =
                remoteMessages.size,
            insertedCount =
                inserted,
            updatedCount =
                updated,
            syncedAt =
                successAt
        )
    }

    /**
     * Cache-first full-message access.
     *
     * First open:
     *   Room miss -> full GET -> Room cache -> local read.
     *
     * Later opens:
     *   Room hit -> no network request.
     *
     * If a deep link opens a message before inbox metadata exists
     * locally, metadata from the full response is inserted first so
     * the MessageContentEntity foreign key remains valid.
     */
    suspend fun getMessage(
        accountId: String,
        messageId: String
    ): Message {

        requireAccountId(
            accountId
        )

        require(
            messageId.isNotBlank()
        ) {
            "messageId must not be blank"
        }

        val existingMetadata =
            store.getMessage(
                accountId =
                    accountId,
                messageId =
                    messageId
            )

        val cachedContent =
            store.getContent(
                accountId =
                    accountId,
                messageId =
                    messageId
            )

        if (
            existingMetadata != null
            &&
            cachedContent != null
        ) {

            /*
             * Cached body means this message has already
             * been explicitly opened before.
             */
            store.markRead(
                accountId =
                    accountId,
                messageId =
                    messageId
            )

            return existingMetadata
                .copy(
                    unread = false
                )
                .toDomainMessage(
                    cachedContent
                )
        }

        val fetchedAt =
            clock()

        val full =
            withContext(
                Dispatchers.IO
            ) {
                remote.getMessage(
                    messageId
                )
            }

        val metadata =
            full
                .toInboxDomain(
                    remoteOrder =
                        existingMetadata
                            ?.remoteOrder
                            ?: 0,
                    observedAt =
                        fetchedAt,
                    firstSeenAt =
                        existingMetadata
                            ?.firstSeenAt
                            ?: fetchedAt
                )
                .toEntity(
                    accountId
                )
                .let {
                    mapped ->

                    /*
                     * Opening a message is not an inbox-list
                     * observation.
                     *
                     * For an existing row preserve the fields
                     * that determine its historical position.
                     * Otherwise an old message would jump in
                     * the inbox merely because its body was
                     * opened for the first time.
                     */
                    if (
                        existingMetadata == null
                    ) {
                        mapped
                    } else {
                        mapped.copy(
                            remoteOrder =
                                existingMetadata.remoteOrder,
                            firstSeenAt =
                                existingMetadata.firstSeenAt,
                            lastSeenAt =
                                existingMetadata.lastSeenAt,
                            unread =
                                false
                        )
                    }
                }

        val content =
            full
                .toContentDomain(
                    fetchedAt
                )
                .toEntity(
                    accountId
                )

        /*
         * Metadata must exist before content because
         * message_contents has a foreign key to messages.
         */
        store.upsertMessages(
            listOf(
                metadata
            )
        )

        store.upsertContent(
            content
        )

        store.markRead(
            accountId =
                accountId,
            messageId =
                messageId
        )

        return metadata
            .copy(
                unread = false
            )
            .toDomainMessage(
                content
            )
    }

    private fun requireAccountId(
        accountId: String
    ) {
        require(
            accountId.isNotBlank()
        ) {
            "accountId must not be blank"
        }
    }
}
