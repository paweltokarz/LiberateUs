package pl.local.liberateus.data.repository

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.liberateus.data.MessageFetchSource
import pl.local.liberateus.data.local.MessageContentEntity
import pl.local.liberateus.data.local.MessageEntity
import pl.local.liberateus.data.local.SyncStateEntity
import pl.local.liberateus.data.remote.MessagesRemoteBackend
import pl.local.liberateus.data.remote.MessagesRemoteDataSource
import pl.local.liberateus.data.remote.SessionIdentityProvider
import pl.local.liberateus.data.remote.dto.RemoteFullMessage
import pl.local.liberateus.data.remote.dto.RemoteInboxMessage

class MessageRepositoryTest {

    private class FakeIdentity(
        private val username:
            String? = "user-A"
    ) : SessionIdentityProvider {

        override fun savedUsername():
            String? =
            username
    }

    private class FakeStore :
        MessageRepositoryStore {

        private val messages =
            linkedMapOf<
                Pair<String, String>,
                MessageEntity
            >()

        private val contents =
            linkedMapOf<
                Pair<String, String>,
                MessageContentEntity
            >()

        private val flows =
            mutableMapOf<
                String,
                MutableStateFlow<
                    List<MessageEntity>
                >
            >()

        private val syncStates =
            mutableMapOf<
                String,
                SyncStateEntity
            >()

        var markReadCalls =
            0

        override fun observeInbox(
            accountId: String
        ): Flow<List<MessageEntity>> =
            flowFor(
                accountId
            )

        override suspend fun getMessage(
            accountId: String,
            messageId: String
        ): MessageEntity? =
            messages[
                accountId to messageId
            ]

        override suspend fun upsertMessages(
            messages:
                List<MessageEntity>
        ) {
            for (message in messages) {
                this.messages[
                    message.accountId to
                        message.messageId
                ] = message
            }

            messages
                .map {
                    it.accountId
                }
                .distinct()
                .forEach {
                    publish(it)
                }
        }

        override suspend fun countMessages(
            accountId: String
        ): Int =
            count(
                accountId
            )

        override suspend fun markRead(
            accountId: String,
            messageId: String
        ) {
            markReadCalls += 1

            val key =
                accountId to
                    messageId

            val existing =
                messages[key]
                    ?: return

            messages[key] =
                existing.copy(
                    unread = false
                )

            publish(
                accountId
            )
        }

        override suspend fun getContent(
            accountId: String,
            messageId: String
        ): MessageContentEntity? =
            contents[
                accountId to messageId
            ]

        override suspend fun upsertContent(
            content:
                MessageContentEntity
        ) {
            contents[
                content.accountId to
                    content.messageId
            ] = content
        }

        override suspend fun getSyncState(
            accountId: String
        ): SyncStateEntity? =
            syncStates[
                accountId
            ]

        override suspend fun upsertSyncState(
            state: SyncStateEntity
        ) {
            syncStates[
                state.accountId
            ] = state
        }

        suspend fun insertMessage(
            message: MessageEntity
        ) {
            upsertMessages(
                listOf(
                    message
                )
            )
        }

        suspend fun insertContent(
            content:
                MessageContentEntity
        ) {
            upsertContent(
                content
            )
        }

        fun content(
            accountId: String,
            messageId: String
        ): MessageContentEntity? =
            contents[
                accountId to messageId
            ]

        fun state(
            accountId: String
        ): SyncStateEntity? =
            syncStates[
                accountId
            ]

        fun count(
            accountId: String
        ): Int =
            messages
                .values
                .count {
                    it.accountId ==
                        accountId
                }

        private fun flowFor(
            accountId: String
        ): MutableStateFlow<
            List<MessageEntity>
        > =
            flows.getOrPut(
                accountId
            ) {
                MutableStateFlow(
                    snapshot(
                        accountId
                    )
                )
            }

        private fun publish(
            accountId: String
        ) {
            flowFor(
                accountId
            ).value =
                snapshot(
                    accountId
                )
        }

        private fun snapshot(
            accountId: String
        ): List<MessageEntity> =
            messages
                .values
                .filter {
                    it.accountId ==
                        accountId
                }
                .sortedWith(
                    compareByDescending<
                        MessageEntity
                    > {
                        it.lastSeenAt
                    }.thenBy {
                        it.remoteOrder
                    }
                )
    }

    private class FakeRemoteBackend :
        MessagesRemoteBackend {

        var inboxCalls =
            0

        var messageCalls =
            0

        var failInbox:
            Throwable? = null

        var failMessage:
            Throwable? = null

        var inbox =
            listOf(
                RemoteInboxMessage(
                    id = "1",
                    sender = "A",
                    topic = "First",
                    sendDate = "date-1",
                    unread = true
                ),
                RemoteInboxMessage(
                    id = "2",
                    sender = "B",
                    topic = "Second",
                    sendDate = "date-2",
                    unread = false
                )
            )

        override fun getInbox(
            limit: Int,
            source: MessageFetchSource
        ): List<RemoteInboxMessage> {

            inboxCalls += 1

            failInbox?.let {
                throw it
            }

            return inbox.take(
                limit
            )
        }

        override fun getMessage(
            messageId: String
        ): RemoteFullMessage {

            messageCalls += 1

            failMessage?.let {
                throw it
            }

            return RemoteFullMessage(
                id = messageId,
                sender = "Remote Sender",
                topic = "Remote Topic",
                sendDate = "remote-date",
                content = "BODY-$messageId"
            )
        }
    }

    private class Clock(
        var value: Long
    ) {
        fun now():
            Long {
            val result =
                value

            value += 1

            return result
        }
    }

    private fun repository(
        store: FakeStore,
        backend:
            FakeRemoteBackend,
        clock: Clock
    ): MessageRepository =
        MessageRepository(
            store = store,
            remote =
                MessagesRemoteDataSource(
                    backend
                ),
            sessionIdentity =
                FakeIdentity(),
            clock = {
                clock.now()
            }
        )

    private fun message(
        id: String = "1",
        unread: Boolean = true,
        firstSeenAt: Long = 50L
    ) =
        MessageEntity(
            accountId = "user-A",
            messageId = id,
            sender = "Local Sender",
            topic = "Local Topic",
            sendDateRaw = "local-date",
            sentAtEpochMillis = null,
            unread = unread,
            remoteOrder = 0,
            firstSeenAt = firstSeenAt,
            lastSeenAt = 60L
        )

    @Test
    fun currentAccountId_comesFromSession() {

        val repo =
            MessageRepository(
                store =
                    FakeStore(),
                remote =
                    MessagesRemoteDataSource(
                        FakeRemoteBackend()
                    ),
                sessionIdentity =
                    FakeIdentity(
                        "abc"
                    ),
                clock = {
                    1L
                }
            )

        assertEquals(
            "abc",
            repo.currentAccountId()
        )
    }

    @Test
    fun syncInbox_insertsMetadataWithoutBody() =
        runBlocking {

            val store =
                FakeStore()

            val backend =
                FakeRemoteBackend()

            val repo =
                repository(
                    store,
                    backend,
                    Clock(100L)
                )

            val result =
                repo.syncInbox(
                    "user-A"
                )

            assertEquals(
                2,
                result.fetchedCount
            )

            assertEquals(
                2,
                result.insertedCount
            )

            assertEquals(
                0,
                backend.messageCalls
            )

            assertNull(
                store.content(
                    "user-A",
                    "1"
                )
            )
        }

    @Test
    fun syncInbox_preservesFirstSeenAt() =
        runBlocking {

            val store =
                FakeStore()

            store.insertMessage(
                message(
                    firstSeenAt = 50L
                )
            )

            val backend =
                FakeRemoteBackend().apply {
                    inbox =
                        listOf(
                            RemoteInboxMessage(
                                id = "1",
                                sender = "New",
                                topic = "New",
                                sendDate = "new",
                                unread = false
                            )
                        )
                }

            val repo =
                repository(
                    store,
                    backend,
                    Clock(100L)
                )

            repo.syncInbox(
                "user-A"
            )

            val item =
                repo
                    .observeInbox(
                        "user-A"
                    )
                    .first()
                    .single()

            assertEquals(
                50L,
                item.firstSeenAt
            )

            assertFalse(
                item.unread
            )
        }

    @Test
    fun syncInbox_doesNotDeleteHistory() =
        runBlocking {

            val store =
                FakeStore()

            store.insertMessage(
                message(
                    id = "historical"
                )
            )

            val repo =
                repository(
                    store,
                    FakeRemoteBackend(),
                    Clock(100L)
                )

            repo.syncInbox(
                "user-A"
            )

            assertEquals(
                3,
                store.count(
                    "user-A"
                )
            )
        }

    @Test
    fun successfulSync_updatesState() =
        runBlocking {

            val store =
                FakeStore()

            val repo =
                repository(
                    store,
                    FakeRemoteBackend(),
                    Clock(100L)
                )

            repo.syncInbox(
                "user-A"
            )

            val state =
                store.state(
                    "user-A"
                )

            assertEquals(
                100L,
                state?.lastAttemptAt
            )

            assertEquals(
                101L,
                state?.lastSuccessAt
            )

            assertNull(
                state?.lastError
            )
        }

    @Test
    fun failedSync_recordsError() =
        runBlocking {

            val store =
                FakeStore()

            val backend =
                FakeRemoteBackend().apply {
                    failInbox =
                        IOException(
                            "network down"
                        )
                }

            val repo =
                repository(
                    store,
                    backend,
                    Clock(100L)
                )

            try {
                repo.syncInbox(
                    "user-A"
                )
            } catch (
                expected: IOException
            ) {
                // expected
            }

            assertEquals(
                "network down",
                store
                    .state(
                        "user-A"
                    )
                    ?.lastError
            )
        }

    @Test
    fun firstOpen_fetchesBodyAndCachesIt() =
        runBlocking {

            val store =
                FakeStore()

            store.insertMessage(
                message()
            )

            val backend =
                FakeRemoteBackend()

            val repo =
                repository(
                    store,
                    backend,
                    Clock(200L)
                )

            val result =
                repo.getMessage(
                    accountId =
                        "user-A",
                    messageId =
                        "1"
                )

            assertEquals(
                1,
                backend.messageCalls
            )

            assertEquals(
                "BODY-1",
                result.content?.content
            )

            assertEquals(
                "BODY-1",
                store
                    .content(
                        "user-A",
                        "1"
                    )
                    ?.content
            )

            assertFalse(
                result.metadata.unread
            )
        }

    @Test
    fun secondOpen_usesRoomAndDoesNotHitNetworkAgain() =
        runBlocking {

            val store =
                FakeStore()

            store.insertMessage(
                message()
            )

            val backend =
                FakeRemoteBackend()

            val repo =
                repository(
                    store,
                    backend,
                    Clock(200L)
                )

            val first =
                repo.getMessage(
                    "user-A",
                    "1"
                )

            backend.failMessage =
                IOException(
                    "network must not be used"
                )

            val second =
                repo.getMessage(
                    "user-A",
                    "1"
                )

            assertEquals(
                1,
                backend.messageCalls
            )

            assertEquals(
                first.content,
                second.content
            )

            assertEquals(
                "BODY-1",
                second.content?.content
            )
        }

    @Test
    fun deepLinkWithoutMetadata_createsMetadataBeforeContent() =
        runBlocking {

            val store =
                FakeStore()

            val backend =
                FakeRemoteBackend()

            val repo =
                repository(
                    store,
                    backend,
                    Clock(500L)
                )

            val result =
                repo.getMessage(
                    accountId =
                        "user-A",
                    messageId =
                        "777"
                )

            assertEquals(
                1,
                backend.messageCalls
            )

            assertEquals(
                "777",
                result.id
            )

            assertEquals(
                "Remote Sender",
                result.metadata.sender
            )

            assertEquals(
                "BODY-777",
                result.content?.content
            )

            assertEquals(
                1,
                store.count(
                    "user-A"
                )
            )

            assertEquals(
                "BODY-777",
                store
                    .content(
                        "user-A",
                        "777"
                    )
                    ?.content
            )
        }

    @Test
    fun firstSuccessfulSync_establishesBaselineWithoutNewMessages() =
        runBlocking {

            val store =
                FakeStore()

            val backend =
                FakeRemoteBackend()

            val repo =
                repository(
                    store,
                    backend,
                    Clock(1000L)
                )

            val result =
                repo.syncInbox(
                    "user-A"
                )

            assertTrue(
                result.wasBaselineSync
            )

            assertEquals(
                2,
                result.insertedCount
            )

            assertEquals(
                0,
                result.newMessageCount
            )

            assertTrue(
                result.newMessages
                    .isEmpty()
            )

            assertTrue(
                store
                    .state(
                        "user-A"
                    )
                    ?.baselineInitialized
                    == true
            )

            /*
             * Baseline synchronization is metadata-only.
             */
            assertEquals(
                0,
                backend.messageCalls
            )
        }

    @Test
    fun laterSync_reportsOnlyPreviouslyUnknownRoomIds() =
        runBlocking {

            val store =
                FakeStore()

            val backend =
                FakeRemoteBackend()

            val repo =
                repository(
                    store,
                    backend,
                    Clock(2000L)
                )

            val baseline =
                repo.syncInbox(
                    "user-A"
                )

            assertTrue(
                baseline.wasBaselineSync
            )

            assertEquals(
                0,
                baseline.newMessageCount
            )

            backend.inbox =
                listOf(
                    RemoteInboxMessage(
                        id = "3",
                        sender = "C",
                        topic = "Actually new",
                        sendDate = "date-3",
                        unread = true
                    )
                ) +
                    backend.inbox

            val second =
                repo.syncInbox(
                    "user-A"
                )

            assertFalse(
                second.wasBaselineSync
            )

            assertEquals(
                3,
                second.fetchedCount
            )

            assertEquals(
                1,
                second.insertedCount
            )

            assertEquals(
                2,
                second.updatedCount
            )

            assertEquals(
                1,
                second.newMessageCount
            )

            assertEquals(
                listOf(
                    "3"
                ),
                second
                    .newMessages
                    .map {
                        it.id
                    }
            )

            assertEquals(
                "Actually new",
                second
                    .newMessages
                    .single()
                    .topic
            )

            /*
             * Detection must not fetch the body because that
             * would mark the Librus message as read.
             */
            assertEquals(
                0,
                backend.messageCalls
            )
        }

    @Test
    fun sameMessage_isNeverReportedNewTwice() =
        runBlocking {

            val store =
                FakeStore()

            val backend =
                FakeRemoteBackend()

            val repo =
                repository(
                    store,
                    backend,
                    Clock(3000L)
                )

            repo.syncInbox(
                "user-A"
            )

            backend.inbox =
                listOf(
                    RemoteInboxMessage(
                        id = "3",
                        sender = "C",
                        topic = "New",
                        sendDate = "date-3",
                        unread = true
                    )
                ) +
                    backend.inbox

            val second =
                repo.syncInbox(
                    "user-A"
                )

            val third =
                repo.syncInbox(
                    "user-A"
                )

            assertEquals(
                listOf(
                    "3"
                ),
                second
                    .newMessages
                    .map {
                        it.id
                    }
            )

            assertEquals(
                0,
                third.newMessageCount
            )

            assertEquals(
                0,
                third.insertedCount
            )

            assertEquals(
                3,
                third.updatedCount
            )

            assertEquals(
                0,
                backend.messageCalls
            )
        }

    @Test
    fun failedFirstSync_doesNotInitializeBaseline() =
        runBlocking {

            val store =
                FakeStore()

            val backend =
                FakeRemoteBackend().apply {
                    failInbox =
                        IOException(
                            "offline"
                        )
                }

            val repo =
                repository(
                    store,
                    backend,
                    Clock(4000L)
                )

            try {

                repo.syncInbox(
                    "user-A"
                )

            } catch (
                expected: IOException
            ) {
                // expected
            }

            assertFalse(
                store
                    .state(
                        "user-A"
                    )
                    ?.baselineInitialized
                    ?: false
            )

            backend.failInbox =
                null

            val firstSuccessful =
                repo.syncInbox(
                    "user-A"
                )

            /*
             * The successful retry is still the baseline.
             */
            assertTrue(
                firstSuccessful
                    .wasBaselineSync
            )

            assertEquals(
                0,
                firstSuccessful
                    .newMessageCount
            )

            assertTrue(
                store
                    .state(
                        "user-A"
                    )
                    ?.baselineInitialized
                    == true
            )
        }



    @Test
    fun getMessage_preservesExistingInboxOrderingMetadata() =
        runBlocking {

            val store =
                FakeStore()

            val backend =
                FakeRemoteBackend()

            val existing =
                message(
                    id = "old-1",
                    unread = true,
                    firstSeenAt = 111L
                )
                    .copy(
                        remoteOrder = 7,
                        lastSeenAt = 222L
                    )

            store.upsertMessages(
                listOf(
                    existing
                )
            )

            val repo =
                repository(
                    store =
                        store,
                    backend =
                        backend,
                    clock =
                        Clock(
                            9999L
                        )
                )

            val result =
                repo.getMessage(
                    accountId =
                        "user-A",
                    messageId =
                        "old-1"
                )

            val stored =
                requireNotNull(
                    store.getMessage(
                        accountId =
                            "user-A",
                        messageId =
                            "old-1"
                    )
                )

            /*
             * Remote full-message metadata may refresh
             * descriptive fields...
             */
            assertEquals(
                "Remote Sender",
                stored.sender
            )

            assertEquals(
                "Remote Topic",
                stored.topic
            )

            assertEquals(
                "remote-date",
                stored.sendDateRaw
            )

            /*
             * ...but opening must not change inbox position.
             */
            assertEquals(
                7,
                stored.remoteOrder
            )

            assertEquals(
                111L,
                stored.firstSeenAt
            )

            assertEquals(
                222L,
                stored.lastSeenAt
            )

            assertFalse(
                stored.unread
            )

            assertEquals(
                7,
                result.metadata.remoteOrder
            )

            assertEquals(
                111L,
                result.metadata.firstSeenAt
            )

            assertEquals(
                222L,
                result.metadata.lastSeenAt
            )

            assertEquals(
                "BODY-old-1",
                result.content?.content
            )

            assertEquals(
                1,
                backend.messageCalls
            )
        }

    @Test
    fun syncInbox_cancellationIsNotPersistedAsFailure() =
        runBlocking {

            val store =
                FakeStore()

            store.upsertSyncState(
                SyncStateEntity(
                    accountId =
                        "user-A",
                    baselineInitialized =
                        true,
                    lastAttemptAt =
                        10L,
                    lastSuccessAt =
                        20L,
                    lastError =
                        null
                )
            )

            val backend =
                FakeRemoteBackend()
                    .apply {
                        failInbox =
                            CancellationException(
                                "test cancellation"
                            )
                    }

            val repo =
                repository(
                    store =
                        store,
                    backend =
                        backend,
                    clock =
                        Clock(
                            100L
                        )
                )

            var thrown:
                Throwable? =
                null

            try {

                repo.syncInbox(
                    accountId =
                        "user-A"
                )

            } catch (
                error: Throwable
            ) {

                thrown =
                    error
            }

            assertTrue(
                thrown is
                    CancellationException
            )

            val state =
                requireNotNull(
                    store.getSyncState(
                        "user-A"
                    )
                )

            /*
             * Cancellation may record that an attempt began,
             * but it is not a repository/network failure.
             */
            assertTrue(
                state.baselineInitialized
            )

            assertEquals(
                20L,
                state.lastSuccessAt
            )

            assertNull(
                state.lastError
            )
        }



    @Test
    fun backfillInboxHistory_insertsOlderHistorySilently() =
        runBlocking {

            val store =
                FakeStore()

            store.upsertSyncState(
                SyncStateEntity(
                    accountId =
                        "user-A",
                    baselineInitialized =
                        true,
                    lastAttemptAt =
                        10L,
                    lastSuccessAt =
                        20L,
                    lastError =
                        null
                )
            )

            val stateBefore =
                store.state(
                    "user-A"
                )

            val backend =
                FakeRemoteBackend()
                    .apply {
                        inbox =
                            (1..15)
                                .map {
                                    index ->

                                    RemoteInboxMessage(
                                        id =
                                            "msg-$index",
                                        sender =
                                            "Sender $index",
                                        topic =
                                            "Topic $index",
                                        sendDate =
                                            "date-$index",
                                        unread =
                                            index % 2 == 0
                                    )
                                }
                    }

            val repo =
                repository(
                    store =
                        store,
                    backend =
                        backend,
                    clock =
                        Clock(
                            1000L
                        )
                )

            val result =
                repo.backfillInboxHistory(
                    accountId =
                        "user-A",
                    limit =
                        100,
                    source =
                        MessageFetchSource.MANUAL
                )

            assertEquals(
                15,
                result.fetchedCount
            )

            assertEquals(
                15,
                result.insertedCount
            )

            assertEquals(
                0,
                result.updatedCount
            )

            assertEquals(
                15,
                repo.localInboxCount(
                    "user-A"
                )
            )

            assertEquals(
                1,
                backend.inboxCalls
            )

            assertEquals(
                0,
                backend.messageCalls
            )

            /*
             * History import has no baseline/new-message side
             * effects. Sync state must remain untouched.
             */
            assertEquals(
                stateBefore,
                store.state(
                    "user-A"
                )
            )

            val snapshot =
                repo.observeInbox(
                    "user-A"
                )
                    .first()

            assertEquals(
                15,
                snapshot.size
            )

            assertEquals(
                "msg-1",
                snapshot.first().id
            )

            assertEquals(
                "msg-15",
                snapshot.last().id
            )
        }

    @Test
    fun backfillInboxHistory_preservesFirstSeenAndRemoteOrdering() =
        runBlocking {

            val store =
                FakeStore()

            store.insertMessage(
                message(
                    id =
                        "msg-3",
                    unread =
                        true,
                    firstSeenAt =
                        55L
                )
                    .copy(
                        remoteOrder =
                            7,
                        lastSeenAt =
                            60L
                    )
            )

            val backend =
                FakeRemoteBackend()
                    .apply {
                        inbox =
                            listOf(
                                RemoteInboxMessage(
                                    id = "msg-1",
                                    sender = "A",
                                    topic = "1",
                                    sendDate = "d1",
                                    unread = true
                                ),
                                RemoteInboxMessage(
                                    id = "msg-2",
                                    sender = "B",
                                    topic = "2",
                                    sendDate = "d2",
                                    unread = true
                                ),
                                RemoteInboxMessage(
                                    id = "msg-3",
                                    sender = "C",
                                    topic = "3",
                                    sendDate = "d3",
                                    unread = false
                                )
                            )
                    }

            val repo =
                repository(
                    store =
                        store,
                    backend =
                        backend,
                    clock =
                        Clock(
                            500L
                        )
                )

            val result =
                repo.backfillInboxHistory(
                    accountId =
                        "user-A",
                    limit =
                        100
                )

            assertEquals(
                3,
                result.fetchedCount
            )

            assertEquals(
                2,
                result.insertedCount
            )

            assertEquals(
                1,
                result.updatedCount
            )

            val stored =
                requireNotNull(
                    store.getMessage(
                        accountId =
                            "user-A",
                        messageId =
                            "msg-3"
                    )
                )

            /*
             * Existing identity/history timestamp survives.
             */
            assertEquals(
                55L,
                stored.firstSeenAt
            )

            /*
             * But the larger metadata response is a genuine
             * server observation, so it refreshes ordering.
             */
            assertEquals(
                2,
                stored.remoteOrder
            )

            assertEquals(
                500L,
                stored.lastSeenAt
            )

            assertFalse(
                stored.unread
            )

            assertEquals(
                0,
                backend.messageCalls
            )

            val ids =
                repo.observeInbox(
                    "user-A"
                )
                    .first()
                    .map {
                        it.id
                    }

            assertEquals(
                listOf(
                    "msg-1",
                    "msg-2",
                    "msg-3"
                ),
                ids
            )
        }



    @Test
    fun observeInbox_sameMessageId_isIsolatedBetweenAccounts() =
        runBlocking {

            val store =
                FakeStore()

            /*
             * Same Librus message ID deliberately exists under
             * two different accounts.
             */
            store.insertMessage(
                message(
                    id =
                        "shared-id"
                )
                    .copy(
                        accountId =
                            "user-A",
                        sender =
                            "Sender A",
                        topic =
                            "Topic A"
                    )
            )

            store.insertMessage(
                message(
                    id =
                        "shared-id"
                )
                    .copy(
                        accountId =
                            "user-B",
                        sender =
                            "Sender B",
                        topic =
                            "Topic B"
                    )
            )

            store.upsertSyncState(
                SyncStateEntity(
                    accountId =
                        "user-A",
                    baselineInitialized =
                        true,
                    lastAttemptAt =
                        10L,
                    lastSuccessAt =
                        11L,
                    lastError =
                        null
                )
            )

            store.upsertSyncState(
                SyncStateEntity(
                    accountId =
                        "user-B",
                    baselineInitialized =
                        false,
                    lastAttemptAt =
                        20L,
                    lastSuccessAt =
                        null,
                    lastError =
                        "B-only"
                )
            )

            val repo =
                repository(
                    store =
                        store,
                    backend =
                        FakeRemoteBackend(),
                    clock =
                        Clock(
                            100L
                        )
                )

            val accountA =
                repo.observeInbox(
                    "user-A"
                )
                    .first()

            val accountB =
                repo.observeInbox(
                    "user-B"
                )
                    .first()

            assertEquals(
                1,
                accountA.size
            )

            assertEquals(
                1,
                accountB.size
            )

            assertEquals(
                "Sender A",
                accountA.single().sender
            )

            assertEquals(
                "Topic A",
                accountA.single().topic
            )

            assertEquals(
                "Sender B",
                accountB.single().sender
            )

            assertEquals(
                "Topic B",
                accountB.single().topic
            )

            assertEquals(
                1,
                store.count(
                    "user-A"
                )
            )

            assertEquals(
                1,
                store.count(
                    "user-B"
                )
            )

            assertEquals(
                true,
                store
                    .state(
                        "user-A"
                    )
                    ?.baselineInitialized
            )

            assertEquals(
                false,
                store
                    .state(
                        "user-B"
                    )
                    ?.baselineInitialized
            )

            assertEquals(
                "B-only",
                store
                    .state(
                        "user-B"
                    )
                    ?.lastError
            )
        }

}
