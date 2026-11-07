package pl.local.liberateus

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withContext

import pl.local.liberateus.data.LoginResult
import pl.local.liberateus.data.MessageFetchSource
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.local.liberateus.data.remote.LibrusConnection
import pl.local.liberateus.data.remote.SessionManager
import pl.local.liberateus.data.remote.MessagesRemoteDataSource
import pl.local.liberateus.data.repository.MessageRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import pl.local.liberateus.ui.login.LoginScreen
import pl.local.liberateus.ui.inbox.InboxContent
import pl.local.liberateus.ui.message.DirectMessageScreen
import pl.local.liberateus.ui.message.MessageLoadingScreen
import pl.local.liberateus.ui.message.MessageErrorScreen
import pl.local.liberateus.ui.MainUiViewModel
import pl.local.liberateus.data.remote.SessionAccess
import pl.local.liberateus.domain.model.InboxItem
import pl.local.liberateus.domain.model.Message
import androidx.activity.result.contract.ActivityResultContracts
import pl.local.liberateus.background.cancelPeriodicMessageChecks
import pl.local.liberateus.background.clearMessageNotifications
import pl.local.liberateus.background.schedulePeriodicMessageChecks
import androidx.compose.foundation.layout.Box
import pl.local.liberateus.data.local.LiberateDatabase
import pl.local.liberateus.data.remote.DraftsRemoteDataSource
import kotlinx.coroutines.CancellationException
import pl.local.liberateus.data.remote.dto.DraftWritePayload
import pl.local.liberateus.data.remote.DraftRecipientDirectoryRemoteDataSource
import pl.local.liberateus.data.remote.DraftWriteRemoteDataSource
import pl.local.liberateus.data.repository.DraftRepository
import pl.local.liberateus.ui.drafts.DraftsContent
import pl.local.liberateus.ui.drafts.ReplyDraftPrefill
import pl.local.liberateus.ui.mailbox.MailboxSwitcher
import pl.local.liberateus.ui.mailbox.MailboxTab
import pl.local.liberateus.data.remote.SentMessagesRemoteDataSource
import pl.local.liberateus.data.repository.SentMessageRepository
import pl.local.liberateus.ui.sent.SentContent
import pl.local.liberateus.ui.mailbox.MailboxScaffold


private val LiberateUsColorScheme =
    androidx.compose.material3.lightColorScheme(
        primary =
            androidx.compose.ui.graphics.Color(0xFF4E9FE8),

        onPrimary =
            androidx.compose.ui.graphics.Color(0xFFFFFFFF),

        primaryContainer =
            androidx.compose.ui.graphics.Color(0xFFDCEEFF),

        onPrimaryContainer =
            androidx.compose.ui.graphics.Color(0xFF12344F),

        secondary =
            androidx.compose.ui.graphics.Color(0xFF72B7F2),

        onSecondary =
            androidx.compose.ui.graphics.Color(0xFFFFFFFF),

        secondaryContainer =
            androidx.compose.ui.graphics.Color(0xFFE7F4FF),

        onSecondaryContainer =
            androidx.compose.ui.graphics.Color(0xFF183A56),

        background =
            androidx.compose.ui.graphics.Color(0xFFF5FAFF),

        onBackground =
            androidx.compose.ui.graphics.Color(0xFF17232D),

        surface =
            androidx.compose.ui.graphics.Color(0xFFFFFFFF),

        onSurface =
            androidx.compose.ui.graphics.Color(0xFF17232D),

        surfaceVariant =
            androidx.compose.ui.graphics.Color(0xFFEAF4FD),

        onSurfaceVariant =
            androidx.compose.ui.graphics.Color(0xFF465D70),

        outline =
            androidx.compose.ui.graphics.Color(0xFFB5CADC)
    )

class MainActivity : ComponentActivity() {

    private val notificationMessageId =
        mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            /*
             * Synchronization remains active even if the user
             * declines notifications.
             */
        }

    private fun requestNotificationPermissionIfNeeded() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
            &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * Notification opened while the process was dead.
         * onNewIntent() only covers an already running Activity.
         */
        notificationMessageId.value =
            intent.getStringExtra(
                EXTRA_MESSAGE_ID
            )

        requestNotificationPermissionIfNeeded()


        setContent {
            MaterialTheme(
                colorScheme = LiberateUsColorScheme
            ) {
                LibrusApp(
                    notificationMessageId =
                        notificationMessageId.value,
                    onNotificationConsumed = {
                        notificationMessageId.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        notificationMessageId.value =
            intent.getStringExtra(EXTRA_MESSAGE_ID)
    }
}

@Composable
private fun LibrusApp(
    notificationMessageId: String?,
    onNotificationConsumed: () -> Unit
) {
    val context = LocalContext.current

    /*
     * Jedno połączenie współdzielone przez warstwę sesji
     * oraz — tymczasowo — legacy message API.
     *
     * Dzięki temu oba używają dokładnie tego samego
     * CookieJar i oauth_token.
     */
    val connection =
        remember {
            LibrusConnection(
                context.applicationContext
            )
        }

    val sessionManager =
        remember(
            connection
        ) {
            SessionManager(
                connection
            )
        }

    /*
     * STEP 6 remote message boundary.
     *
     * MainActivity no longer accesses
     * LibrusAuthClient message methods directly.
     */
    val messagesRemote =
        remember(
            connection
        ) {
            MessagesRemoteDataSource(
                connection
            )
        }

    val messageRepository =
        remember(
            context,
            messagesRemote,
            sessionManager
        ) {
            MessageRepository(
                context =
                    context.applicationContext,
                remote =
                    messagesRemote,
                sessionManager =
                    sessionManager
            )
        }


    val draftsRemote =
        remember(
            connection
        ) {
            DraftsRemoteDataSource(
                connection
            )
        }


    val draftRecipientDirectory =
        remember(connection) {
            DraftRecipientDirectoryRemoteDataSource(
                connection
            )
        }

    val draftWriter =
        remember(connection) {
            DraftWriteRemoteDataSource(
                connection
            )
        }

    val draftRepository =
        remember(
            context,
            draftsRemote
        ) {
            DraftRepository(
                remote =
                    draftsRemote,
                dao =
                    LiberateDatabase
                        .getInstance(
                            context.applicationContext
                        )
                        .draftDao()
            )
        }


    val sentMessagesRemote =
        remember(
            connection
        ) {
            SentMessagesRemoteDataSource(
                connection
            )
        }

    val sentMessageRepository =
        remember(
            context,
            sentMessagesRemote
        ) {
            SentMessageRepository(
                remote =
                    sentMessagesRemote,

                dao =
                    LiberateDatabase
                        .getInstance(
                            context.applicationContext
                        )
                        .sentMessageDao(),
                contentDao = LiberateDatabase
                    .getInstance(context.applicationContext)
                    .sentMessageContentDao()
            )
        }


    val scope = rememberCoroutineScope()

    val uiViewModel =
        androidx.lifecycle.viewmodel.compose
            .viewModel<
                MainUiViewModel
            >()

    var login by
        uiViewModel.login

    var password by
        uiViewModel.password

    /*
     * STEP 10 TRUE OFFLINE
     *
     * `connected` means the account UI may be entered.
     * It no longer means that the Librus network session
     * is currently alive.
     *
     * `onlineSession` tracks remote-session availability.
     */
    /*
     * This must be evaluated when recovery is requested.
     *
     * A successful manual login may create saved credentials
     * after the first composition, so a remembered Boolean
     * would become stale.
     */
    fun hasLocalAccountNow():
        Boolean =
        sessionManager
            .hasSavedCredentials()
            &&
            !sessionManager
                .savedUsername()
                .isNullOrBlank()

    /*
     * STEP 11B PRESENTATION VIEWMODEL
     *
     * Initial values preserve Step 10 behavior.
     * initialize() is idempotent across recompositions.
     */
    uiViewModel.initialize(
        savedUsername =
            sessionManager
                .savedUsername()
                .orEmpty(),
        localAccountAvailable =
            hasLocalAccountNow())


    var connected by
        uiViewModel.connected

    var onlineSession by
        uiViewModel.onlineSession


    var busy by
        uiViewModel.busy

    var status by
        uiViewModel.status

    var messages by
        uiViewModel.messages



    var selectedMailbox by
        uiViewModel.selectedMailbox

    var drafts by
        uiViewModel.drafts

    var expandedDraftId by
        uiViewModel.expandedDraftId

    var replyPrefill by remember {
        mutableStateOf<ReplyDraftPrefill?>(null)
    }

    var replyLoading by remember {
        mutableStateOf(false)
    }


    var sentMessages by
        uiViewModel.sentMessages

    var expandedSentMessageId by
        uiViewModel.expandedSentMessageId
    /*
     * STEP 8 ROOM INBOX SOURCE
     *
     * Network synchronizes metadata into Room.
     * UI receives inbox metadata only from Room.
     */
    val roomInboxAccountId =
        sessionManager
            .savedUsername()
            ?.trim()
            .orEmpty()

    var sentFullBodies by remember(roomInboxAccountId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var sentLoadingId by remember(roomInboxAccountId) {
        mutableStateOf<String?>(null)
    }
    var sentDetailError by remember(roomInboxAccountId) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(
        roomInboxAccountId,
        messageRepository
    ) {

        if (
            roomInboxAccountId.isBlank()
        ) {
            messages =
                emptyList()

            return@LaunchedEffect
        }

        messageRepository
            .observeInbox(
                roomInboxAccountId
            )
            .collectLatest {
                localMessages ->

                /*
                 * Presentation consumes the application
                 * domain model directly.
                 */
                messages =
                    localMessages
            }
    }

    /*
     * STEP 17 ACCOUNT BOUNDARY
     *
     * A network/cache operation may finish after logout or
     * after another account has logged in.
     *
     * Such a result must never mutate presentation state for
     * the new account.
     */
    fun isCurrentAccountOperation(
        expectedAccountId: String
    ): Boolean =
        expectedAccountId.isNotBlank() &&
            connected &&
            sessionManager
                .savedUsername()
                ?.trim() ==
            expectedAccountId

    /*
     * STEP 9 FULL MESSAGE CACHE
     *
     * MessageRepository decides whether body comes from
     * Room cache or from the remote full-message endpoint.
     */

    LaunchedEffect(
        roomInboxAccountId,
        draftRepository
    ) {
        if (
            roomInboxAccountId.isBlank()
        ) {
            drafts =
                emptyList()

            return@LaunchedEffect
        }

        draftRepository
            .observeDrafts(
                roomInboxAccountId
            )
            .collectLatest {
                localDrafts ->

                drafts =
                    localDrafts
            }
    }

    suspend fun syncDraftsThroughRepository(
        limit: Int = 100
    ) {
        val accountId =
            sessionManager
                .savedUsername()
                ?.trim()
                .orEmpty()

        check(
            accountId.isNotBlank()
        ) {
            "No active account"
        }

        try {
            draftRepository
                .syncDrafts(
                    accountId =
                        accountId,
                    limit =
                        limit
                )
        } catch (
            firstFailure:
                Exception
        ) {
            val recovery =
                sessionManager
                    .recoverAfterRequestFailure(
                        hasLocalAccount =
                            hasLocalAccountNow()
                    )

            if (
                recovery.access ==
                SessionAccess.ONLINE
            ) {
                draftRepository
                    .syncDrafts(
                        accountId =
                            accountId,
                        limit =
                            limit
                    )
            } else {
                throw firstFailure
            }
        }
    }



    LaunchedEffect(
        roomInboxAccountId,
        sentMessageRepository
    ) {
        if (
            roomInboxAccountId.isBlank()
        ) {
            sentMessages =
                emptyList()

            return@LaunchedEffect
        }

        sentMessageRepository
            .observeSentMessages(
                roomInboxAccountId
            )
            .collectLatest {
                localSent ->

                sentMessages =
                    localSent
            }
    }

    suspend fun syncSentThroughRepository(
        limit: Int = 100
    ) {
        val accountId =
            sessionManager
                .savedUsername()
                ?.trim()
                .orEmpty()

        check(
            accountId.isNotBlank()
        ) {
            "No active account"
        }

        try {
            sentMessageRepository
                .syncSentMessages(
                    accountId =
                        accountId,

                    limit =
                        limit
                )

        } catch (
            firstFailure:
                Exception
        ) {
            val recovery =
                sessionManager
                    .recoverAfterRequestFailure(
                        hasLocalAccount =
                            hasLocalAccountNow()
                    )

            if (
                recovery.access ==
                SessionAccess.ONLINE
            ) {
                sentMessageRepository
                    .syncSentMessages(
                        accountId =
                            accountId,

                        limit =
                            limit
                    )
            } else {
                throw firstFailure
            }
        }
    }


    suspend fun getMessageThroughRepository(
        messageId: String,
        accountIdOverride: String? = null
    ): Message {


        val accountId =
            accountIdOverride
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: sessionManager
                    .savedUsername()
                    ?.trim()
                    .orEmpty()

        require(
            accountId.isNotBlank()
        ) {
            "No active account"
        }

        val message =
            messageRepository
                .getMessage(
                    accountId =
                        accountId,
                    messageId =
                        messageId
                )

        /*
         * Explicit open must resolve to a complete message.
         *
         * Repository may obtain the body either from Room
         * cache or from the remote endpoint.
         */
        requireNotNull(
            message.content
        ) {
            "Message content missing after repository load"
        }

        return message
    }

    suspend fun syncInboxThroughRepository(

        limit: Int = 10,
        source:
            MessageFetchSource =
            MessageFetchSource.UNKNOWN,
        accountIdOverride: String? = null
    ): List<InboxItem> {


        val accountId =
            accountIdOverride
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: sessionManager
                    .savedUsername()
                    ?.trim()
                    .orEmpty()

        if (
            accountId.isBlank()
        ) {
            return emptyList()
        }

        /*
         * Normal synchronization remains deliberately small.
         *
         * This is the path that owns baseline/new-message
         * semantics.
         */
        val syncResult =
            messageRepository
                .syncInbox(
                    accountId =
                        accountId,
                    limit =
                        limit,
                    source =
                        source
                )

        /*
         * STEP 15 HISTORY BACKFILL
         *
         * Probe on the real Librus endpoint confirmed:
         *
         * limit=10  -> 10
         * limit=25  -> 25
         * limit=50  -> 50
         * limit=100 -> 51
         *
         * A larger request therefore exposes older metadata.
         *
         * Do it only while the local database still looks like
         * the original 10-row inbox and the normal endpoint
         * returned a full page.
         *
         * Crucially this uses backfillInboxHistory(), not a
         * second syncInbox(), so old historical rows can never
         * become InboxSyncResult.newMessages.
         */
        if (
            limit == 10
            &&
            syncResult.fetchedCount == 10
        ) {

            val localCount =
                messageRepository
                    .localInboxCount(
                        accountId
                    )

            if (
                localCount <= 10
            ) {
                messageRepository
                    .backfillInboxHistory(
                        accountId =
                            accountId,
                        limit =
                            100,
                        source =
                            source
                    )
            }
        }

        /*
         * Room remains the single presentation source.
         */
        return messageRepository
            .observeInbox(
                accountId
            )
            .first()
    }


    var expandedMessage by
        uiViewModel.expandedMessage

    var directMessage by
        uiViewModel.directMessage

    var directMessageLoading by
        uiViewModel.directMessageLoading

    var directMessageError by
        uiViewModel.directMessageError

    var openingMessageId by
        uiViewModel.openingMessageId

LaunchedEffect(Unit) {

        if (
            sessionManager
                .hasSavedCredentials()
        ) {

            busy = true

            status =
                if (
                    hasLocalAccountNow()
                ) {
                    "Tryb lokalny — sprawdzanie połączenia…"
                } else {
                    "Przywracanie sesji…"
                }

            val recovery =
                withContext(
                    Dispatchers.IO
                ) {
                    sessionManager
                        .recoverSavedSession(
                            hasLocalAccount =
                                hasLocalAccountNow()
                        )
                }

            when (
                recovery.access
            ) {

                SessionAccess.ONLINE -> {

                    connected = true
                    onlineSession = true

                    login =
                        sessionManager
                            .savedUsername()
                            .orEmpty()

                    status =
                        "Połączono"
                }

                SessionAccess.LOCAL -> {

                    connected = true
                    onlineSession = false

                    login =
                        sessionManager
                            .savedUsername()
                            .orEmpty()

                    status =
                        when (
                            recovery.result
                        ) {

                            LoginResult.CaptchaRequired ->
                                "Tryb lokalny — Librus wymaga CAPTCHA"

                            is LoginResult.Failure ->
                                "Tryb lokalny — synchronizacja niedostępna"

                            else ->
                                "Tryb lokalny"
                        }
                }

                SessionAccess.LOGIN_REQUIRED -> {

                    connected = false
                    onlineSession = false

                    status =
                        when (
                            recovery.result
                        ) {

                            LoginResult.InvalidCredentials ->
                                "Zapisane dane logowania wygasły"

                            LoginResult.CaptchaRequired ->
                                "Librus wymaga CAPTCHA"

                            is LoginResult.Failure ->
                                "Nie udało się przywrócić sesji: " +
                                    recovery.result.message

                            LoginResult.Success ->
                                "Niepołączono"
                        }
                }
            }

            /*
             * 0.3 background synchronization remains enabled
             * both while online and while temporarily offline.
             *
             * In LOCAL mode WorkManager will retry on a later
             * 30–45 minute window.
             */
            if (
                recovery.access ==
                    SessionAccess.ONLINE
                ||
                recovery.access ==
                    SessionAccess.LOCAL
            ) {

                schedulePeriodicMessageChecks(
                    context.applicationContext
                )

            } else {

                cancelPeriodicMessageChecks(
                    context.applicationContext
                )
            }

            busy = false
        }
    }

    LaunchedEffect(
        notificationMessageId,
        connected
    ) {
        val messageId = notificationMessageId

        if (
            connected &&
            !messageId.isNullOrBlank()
        ) {

            val notificationAccountId =
                sessionManager
                    .savedUsername()
                    ?.trim()
                    .orEmpty()

            if (
                notificationAccountId.isBlank()
            ) {
                return@LaunchedEffect
            }

            directMessageLoading = true
            directMessageError = null

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    try {
                        getMessageThroughRepository(
                                messageId,
                                accountIdOverride =
                                    notificationAccountId
                            )
                    } catch (error: Exception) {

                        if (!onlineSession) {
                            throw error
                        }

                        if (
                            sessionManager
                                .recoverAfterRequestFailure(
                                    hasLocalAccount =
                                        hasLocalAccountNow()
                                )
                                .access ==
                                SessionAccess.ONLINE
                        ) {
                            getMessageThroughRepository(
                                messageId,
                                accountIdOverride =
                                    notificationAccountId
                            )
                        } else {
                            throw IllegalStateException(
                                "Nie udało się odnowić sesji"
                            )
                        }
                    }
                }
            }

            if (
                !isCurrentAccountOperation(
                    notificationAccountId
                )
            ) {
                return@LaunchedEffect
            }

            result.onSuccess {
                directMessage = it
                status =
                    if (onlineSession) {
                        "Połączono"
                    } else {
                        "Tryb lokalny"
                    }

                
            }.onFailure {
                directMessageError =
                    it.message ?: "Nie udało się otworzyć wiadomości"
            }

            directMessageLoading = false
            onNotificationConsumed()
        }
    }

    if (directMessage != null) {
        BackHandler {
            directMessage = null
        }
    }


    LaunchedEffect(
        connected
    ) {
        if (
            !connected
        ) {
            selectedMailbox =
                MailboxTab.INBOX

            drafts =
                emptyList()

            sentMessages =
                emptyList()

            expandedDraftId =
                null

            expandedSentMessageId =
                null
        }
    }


    fun requestInboxSync() {
        if (
            !onlineSession
        ) {
            status =
                "Tryb lokalny — odebrane z pamięci"

            return
        }

        busy =
            true

        status =
            "Pobieranie wiadomości…"

        scope.launch {
            val result =
                runCatching {
                    withContext(
                        Dispatchers.IO
                    ) {
                        syncInboxThroughRepository(
                            10,
                            MessageFetchSource.MANUAL
                        )
                    }
                }

            result.onSuccess {
                refreshed ->

                messages =
                    refreshed

                onlineSession =
                    true

                status =
                    "Połączono"
            }

            result.onFailure {
                error ->

                onlineSession =
                    false

                status =
                    "Tryb lokalny — nie udało się odświeżyć: " +
                        (
                            error.message
                                ?: "brak połączenia"
                        )
            }

            busy =
                false
        }
    }


    fun requestDraftSync() {
        if (
            !onlineSession
        ) {
            status =
                "Tryb lokalny — robocze z pamięci"

            return
        }

        busy = true

        status =
            "Pobieranie roboczych…"

        scope.launch {
            val result =
                runCatching {
                    withContext(
                        Dispatchers.IO
                    ) {
                        syncDraftsThroughRepository(
                            limit = 100
                        )
                    }
                }

            result.onSuccess {
                onlineSession =
                    true

                status =
                    "Połączono"
            }

            result.onFailure {
                onlineSession =
                    false

                status =
                    "Tryb lokalny — nie udało się pobrać roboczych: " +
                        (
                            it.message
                                ?: "brak połączenia"
                        )
            }

            busy = false
        }
    }



    fun requestSentSync() {
        if (
            !onlineSession
        ) {
            status =
                "Tryb lokalny — wysłane z pamięci"

            return
        }

        busy = true

        status =
            "Pobieranie wysłanych…"

        scope.launch {
            val result =
                runCatching {
                    withContext(
                        Dispatchers.IO
                    ) {
                        syncSentThroughRepository(
                            limit = 100
                        )
                    }
                }

            result.onSuccess {
                onlineSession =
                    true

                status =
                    "Połączono"
            }

            result.onFailure {
                onlineSession =
                    false

                status =
                    "Tryb lokalny — nie udało się pobrać wysłanych: " +
                        (
                            it.message
                                ?: "brak połączenia"
                        )
            }

            busy = false
        }
    }


    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        if (directMessage != null) {
            DirectMessageScreen(
                message = directMessage!!,
                onBack = {
                    directMessage = null
                }
            )
        } else if (
            directMessageLoading &&
            connected
        ) {
            MessageLoadingScreen()
        } else if (
            directMessageError != null &&
            connected
        ) {
            MessageErrorScreen(
                message = directMessageError!!,
                onBack = {
                    directMessageError = null
                }
            )
        } else if (connected) {
            MailboxScaffold(
                username =
                    login,

                status =
                    status,

                busy =
                    busy,

                selectedMailbox =
                    selectedMailbox,

                onMailboxSelected = {
                    mailbox ->

                    if (
                        selectedMailbox !=
                        mailbox
                    ) {
                        selectedMailbox =
                            mailbox

                        expandedMessage =
                            null

                        expandedDraftId =
                            null

                        expandedSentMessageId =
                            null

                        if (
                            onlineSession
                        ) {
                            when (
                                mailbox
                            ) {
                                MailboxTab.INBOX -> {
                                    /*
                                     * No automatic inbox refresh.
                                     * The common refresh button handles it.
                                     */
                                }

                                MailboxTab.SENT -> {
                                    requestSentSync()
                                }

                                MailboxTab.DRAFTS -> {
                                    requestDraftSync()
                                }
                            }
                        }
                    }
                },

                onRefresh = {
                    when (
                        selectedMailbox
                    ) {
                        MailboxTab.INBOX -> {
                            requestInboxSync()
                        }

                        MailboxTab.SENT -> {
                            requestSentSync()
                        }

                        MailboxTab.DRAFTS -> {
                            requestDraftSync()
                        }
                    }
                },

                onLogout = {

                    cancelPeriodicMessageChecks(
                        context.applicationContext
                    )

                    sessionManager
                        .logout()

                    uiViewModel
                        .resetForLogout()
                }
            ) {
                pl.local.liberateus.ui.mailbox.LuPullRefresh(
                    isRefreshing = busy,
                    onRefresh = {
                        if (!busy) {
                            when (selectedMailbox) {
                                MailboxTab.INBOX -> {
                                    requestInboxSync()
                                }
                                MailboxTab.SENT -> {
                                    requestSentSync()
                                }
                                MailboxTab.DRAFTS -> {
                                    requestDraftSync()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {

                when (
                    selectedMailbox
                ) {
                    MailboxTab.INBOX -> {
                        if (replyLoading) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = {},
                                title = {
                                    Text("Przygotowywanie odpowiedzi")
                                },
                                text = {
                                    Row(
                                        horizontalArrangement =
                                            Arrangement.spacedBy(16.dp),
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text("Wyszukiwanie adresata…")
                                    }
                                },
                                confirmButton = {}
                            )
                        }

                        InboxContent(
                            messages =
                                messages,

                            expandedMessage =
                                expandedMessage,

                            openingMessageId =
                                openingMessageId,

                            onReply = { original ->
                                if (!replyLoading) {
                                    replyLoading = true
                                val metadata = original.metadata
                                val sender = metadata.sender.trim()
                                val topic = metadata.topic.trim()
                                val contentHtml = original.content?.content.orEmpty()
                                val plainContent = Html.fromHtml(
                                    contentHtml,
                                    Html.FROM_HTML_MODE_LEGACY
                                ).toString().trim()

                                    scope.launch {
                                        try {
                                    val matchingRecipients = runCatching {
                                        if (!onlineSession) emptyList()
                                        else withContext(Dispatchers.IO) {
                                            draftRecipientDirectory
                                                .getRecipientTypes()
                                                .flatMap { group ->
                                                    draftRecipientDirectory
                                                        .getRecipients(group)
                                                }
                                                .filter { person ->
                                                    person.name.trim().equals(
                                                        sender,
                                                        ignoreCase = true
                                                    )
                                                }
                                                .distinctBy { it.accountId }
                                        }
                                    }.getOrDefault(emptyList())

                                    val match = matchingRecipients
                                        .singleOrNull()
                                    val id = match?.accountId
                                    replyPrefill = ReplyDraftPrefill(
                                        messageId = metadata.id,
                                        senderName = sender,
                                        topic = if (
                                            topic.startsWith("Re:", ignoreCase = true)
                                        ) topic else "Re: $topic",
                                        content = "\n\n\n-----------------------------------\n" +
                                            plainContent,
                                        receiversJson = if (id != null) {
                                            """{"schoolReceivers":[{"accountId":"$id"}]}"""
                                        } else {
                                            """{"schoolReceivers":[]}"""
                                        },
                                        recipientNames = if (id != null) {
                                            mapOf(id to match.name)
                                        } else emptyMap()
                                    )
                                    selectedMailbox = MailboxTab.DRAFTS
                                    expandedMessage = null
                                        } finally {
                                            replyLoading = false
                                        }
                                    }
                                }
                            },

                            onOpenMessage = {
                                message ->

                                if (
                                    expandedMessage
                                        ?.id ==
                                    message.id
                                ) {
                                    expandedMessage =
                                        null

                                } else {
                                    openingMessageId =
                                        message.id

                                    status =
                                        "Otwieranie wiadomości…"

                                    scope.launch {
                                        val result =
                                            runCatching {
                                                withContext(
                                                    Dispatchers.IO
                                                ) {
                                                    getMessageThroughRepository(
                                                        message.id
                                                    )
                                                }
                                            }

                                        result.onSuccess {
                                            full ->

                                            expandedMessage =
                                                full

                                            messages =
                                                messages.map {
                                                    item ->

                                                    if (
                                                        item.id ==
                                                        message.id
                                                    ) {
                                                        item.copy(
                                                            unread =
                                                                false
                                                        )
                                                    } else {
                                                        item
                                                    }
                                                }

                                            status =
                                                if (
                                                    onlineSession
                                                ) {
                                                    "Połączono"
                                                } else {
                                                    "Tryb lokalny"
                                                }
                                        }

                                        result.onFailure {
                                            error ->

                                            status =
                                                if (
                                                    onlineSession
                                                ) {
                                                    "Błąd otwierania: " +
                                                        (
                                                            error.message
                                                                ?: "nieznany błąd"
                                                        )
                                                } else {
                                                    "Tryb lokalny — brak zapisanej treści"
                                                }
                                        }

                                        openingMessageId =
                                            null
                                    }
                                }
                            }
                        )
                    }

                    MailboxTab.SENT -> {
                        SentContent(
                            messages = sentMessages,
                            expandedMessageId = expandedSentMessageId,
                            fullBodies = sentFullBodies,
                            loadingMessageId = sentLoadingId,
                            detailError = sentDetailError,
                            onToggleMessage = { messageId ->
                                if (sentLoadingId == messageId) {
                                    // Prevent duplicate requests.
                                } else if (
                                    expandedSentMessageId == messageId &&
                                    sentDetailError == null
                                ) {
                                    expandedSentMessageId = null
                                } else {
                                    expandedSentMessageId = messageId
                                    sentDetailError = null
                                    if (!sentFullBodies.containsKey(messageId)) {
                                        val expectedAccountId =
                                            roomInboxAccountId
                                        if (expectedAccountId.isBlank()) {
                                            sentDetailError =
                                                "Brak aktywnego konta."
                                        } else {
                                            sentLoadingId = messageId
                                            scope.launch {
                                                try {
                                                    val fullBody =
                                                        sentMessageRepository
                                                            .getFullSentBody(
                                                                expectedAccountId,
                                                                messageId
                                                            )
                                                    if (
                                                        isCurrentAccountOperation(
                                                            expectedAccountId
                                                        )
                                                    ) {
                                                        sentFullBodies =
                                                            sentFullBodies +
                                                                (messageId to fullBody)
                                                    }
                                                } catch (
                                                    error: CancellationException
                                                ) {
                                                    throw error
                                                } catch (error: Exception) {
                                                    if (
                                                        isCurrentAccountOperation(
                                                            expectedAccountId
                                                        )
                                                    ) {
                                                        sentDetailError =
                                                            "Nie udało się pobrać pełnej treści: " +
                                                                (error.message
                                                                    ?: "błąd połączenia")
                                                    }
                                                } finally {
                                                    if (
                                                        isCurrentAccountOperation(
                                                            expectedAccountId
                                                        ) &&
                                                        sentLoadingId == messageId
                                                    ) {
                                                        sentLoadingId = null
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }

                    MailboxTab.DRAFTS -> {
                        DraftsContent(
                            replyPrefill = replyPrefill,
                            onDismissReply = {
                                replyPrefill = null
                            },
                            drafts =
                                drafts,

                            expandedDraftId =
                                expandedDraftId,

                            onToggleDraft = {
                                draftId ->

                                expandedDraftId =
                                    if (
                                        expandedDraftId ==
                                        draftId
                                    ) {
                                        null
                                    } else {
                                        draftId
                                    }
                            },

                        onLoadRecipientTypes = {
                            withContext(Dispatchers.IO) {
                                draftRecipientDirectory
                                    .getRecipientTypes()
                            }
                        },
                        onLoadRecipients = { group ->
                            withContext(Dispatchers.IO) {
                                draftRecipientDirectory
                                    .getRecipients(group)
                            }
                        },
                        onSaveNewDraft = {
                            topic, content, receiversJson ->
                            withContext(Dispatchers.IO) {
                                draftWriter.createDraft(
                                    DraftWritePayload(
                                        topic = topic,
                                        content = content,
                                        receiversJson = receiversJson
                                    )
                                )
                            }

                            /*
                             * Once POST succeeded, a failed refresh
                             * must not make UI retry the POST.
                             */
                            try {
                                syncDraftsThroughRepository()
                            } catch (
                                cancelled: CancellationException
                            ) {
                                throw cancelled
                            } catch (
                                ignored: Exception
                            ) {
                                // The server write already succeeded.
                            }
                        },
                        onUpdateDraft = {
                            draftId, topic, content, receiversJson ->
                            /*
                             * Recheck the local draft before PUT.
                             * Never overwrite unsupported metadata.
                             */
                            val accountId =
                                sessionManager.savedUsername()
                                    ?.trim()
                                    .orEmpty()

                            check(accountId.isNotBlank()) {
                                "Brak aktywnego konta"
                            }

                            val existing =
                                draftRepository.getLocalDraft(
                                    accountId,
                                    draftId
                                )

                            check(
                                existing != null &&
                                    existing.type.equals(
                                        "NEWONE",
                                        ignoreCase = true
                                    ) &&
                                    pl.local.liberateus.ui.drafts.editableSchoolRecipientIds(
                                        existing.receiversJson
                                    ) != null &&
                                    !existing.hasAttachment
                            ) {
                                "Nie można bezpiecznie edytować " +
                                    "tej roboczej"
                            }

                            withContext(Dispatchers.IO) {
                                draftWriter.updateDraft(
                                    draftId,
                                    DraftWritePayload(
                                        topic = topic,
                                        content = content,
                                        receiversJson = receiversJson
                                    )
                                )
                            }

                            /*
                             * PUT already succeeded. A failed GET
                             * must not trigger another PUT.
                             */
                            try {
                                syncDraftsThroughRepository()
                            } catch (
                                cancelled: CancellationException
                            ) {
                                throw cancelled
                            } catch (
                                ignored: Exception
                            ) {
                                // Keep the successful write.
                            }
                        },

                        onSendMessage = { topic, content, receiversJson, draftId ->
                    // Confirmation checks the Sent folder before and
                    // after exactly one POST. Never retry that POST.
                    pl.local.liberateus.data.remote
                        .VerifiedMessageSender(connection)
                        .send(topic, content, receiversJson)

                    // Only a verified send permits draft deletion.
                    if (draftId != null) {
                        try {
                            withContext(Dispatchers.IO) {
                                draftWriter.deleteDraft(draftId)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (ignored: Exception) {
                            // The message is already confirmed as sent.
                        }
                    }

                    try {
                        syncSentThroughRepository()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (ignored: Exception) {
                        // A refresh failure is not a send failure.
                    }

                    try {
                        syncDraftsThroughRepository()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (ignored: Exception) {
                        // A refresh failure is not a send failure.
                    }
                },
                onDeleteDraft = { draftId ->
                            val accountId =
                                sessionManager.savedUsername()
                                    ?.trim()
                                    .orEmpty()

                            check(accountId.isNotBlank()) {
                                "Brak aktywnego konta"
                            }

                            val existing =
                                draftRepository.getLocalDraft(
                                    accountId,
                                    draftId
                                )

                            check(
                                existing != null &&
                                    !existing.deleted &&
                                    existing.id == draftId
                            ) {
                                "Robocza nie istnieje " +
                                    "na aktywnym koncie"
                            }

                            /*
                             * Exactly one DELETE.
                             * Never retry due to a failed refresh.
                             */
                            withContext(Dispatchers.IO) {
                                draftWriter.deleteDraft(draftId)
                            }

                            try {
                                syncDraftsThroughRepository()
                            } catch (
                                cancelled: CancellationException
                            ) {
                                throw cancelled
                            } catch (
                                ignored: Exception
                            ) {
                                // DELETE has already succeeded.
                            }
                        }

)
                    }
                }
            
                }
}

        } else {
            LoginScreen(
                login = login,
                password = password,
                status = status,
                busy = busy,
                onLoginChanged = { login = it },
                onPasswordChanged = { password = it },
                onLogin = {
                    busy = true
                    status = "Logowanie…"

                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            sessionManager.login(login, password)
                        }

                        when (result) {
                            LoginResult.Success -> {
                                connected = true
                                onlineSession = true
                                password = ""
                                status = "Połączono"

                                schedulePeriodicMessageChecks(
                                    context.applicationContext
                                )
                            }

                            LoginResult.InvalidCredentials -> {
                                status = "Nieprawidłowy login lub hasło"
                            }

                            LoginResult.CaptchaRequired -> {
                                status = "Librus wymaga CAPTCHA"
                            }

                            is LoginResult.Failure -> {
                                status = "Błąd: ${result.message}"
                            }
                        }

                        busy = false
                    }
                }
            )
        }
    }
}

