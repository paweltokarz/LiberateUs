package pl.local.liberateus.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.liberateus.data.MessageFetchSource
import pl.local.liberateus.data.mapper.toContentDomain
import pl.local.liberateus.data.mapper.toDomain
import pl.local.liberateus.data.mapper.toInboxDomain
import pl.local.liberateus.data.remote.dto.RemoteFullMessage
import pl.local.liberateus.data.remote.dto.RemoteInboxMessage

class MessagesRemoteDataSourceTest {

    private class FakeBackend :
        MessagesRemoteBackend {

        var inboxCalls =
            0

        var messageCalls =
            0

        var lastLimit:
            Int? = null

        var lastSource:
            MessageFetchSource? = null

        var lastMessageId:
            String? = null

        val inbox =
            listOf(
                RemoteInboxMessage(
                    id = "101",
                    sender = "Teacher",
                    topic = "Test",
                    sendDate =
                        "2026-10-27 12:00",
                    unread = true
                )
            )

        val full =
            RemoteFullMessage(
                id = "101",
                sender = "Teacher",
                topic = "Test",
                sendDate =
                    "2026-10-27 12:00",
                content = "Body"
            )

        override fun getInbox(
            limit: Int,
            source: MessageFetchSource
        ): List<RemoteInboxMessage> {

            inboxCalls += 1
            lastLimit = limit
            lastSource = source

            return inbox
        }

        override fun getMessage(
            messageId: String
        ): RemoteFullMessage {

            messageCalls += 1
            lastMessageId =
                messageId

            return full
        }
    }

    @Test
    fun inbox_defaults_areCorrect() {

        val backend =
            FakeBackend()

        val remote =
            MessagesRemoteDataSource(
                backend
            )

        val result =
            remote.getInbox()

        assertSame(
            backend.inbox,
            result
        )

        assertEquals(
            1,
            backend.inboxCalls
        )

        assertEquals(
            0,
            backend.messageCalls
        )

        assertEquals(
            10,
            backend.lastLimit
        )

        assertEquals(
            MessageFetchSource.UNKNOWN,
            backend.lastSource
        )
    }

    @Test
    fun inbox_doesNotFetchFullMessage() {

        val backend =
            FakeBackend()

        val remote =
            MessagesRemoteDataSource(
                backend
            )

        remote.getInbox(
            limit = 10,
            source =
                MessageFetchSource.MANUAL
        )

        assertEquals(
            1,
            backend.inboxCalls
        )

        assertEquals(
            0,
            backend.messageCalls
        )
    }

    @Test
    fun fullMessage_requiresExplicitCall() {

        val backend =
            FakeBackend()

        val remote =
            MessagesRemoteDataSource(
                backend
            )

        val result =
            remote.getMessage(
                "101"
            )

        assertSame(
            backend.full,
            result
        )

        assertEquals(
            1,
            backend.messageCalls
        )

        assertEquals(
            "101",
            backend.lastMessageId
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun blankMessageId_isRejected() {

        MessagesRemoteDataSource(
            FakeBackend()
        ).getMessage(
            ""
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun invalidInboxLimit_isRejected() {

        MessagesRemoteDataSource(
            FakeBackend()
        ).getInbox(
            limit = 0
        )
    }

    @Test
    fun inboxMapper_preservesMetadata() {

        val item =
            RemoteInboxMessage(
                id = "7",
                sender = "Sender",
                topic = "Topic",
                sendDate = "Date",
                unread = true
            ).toDomain(
                remoteOrder = 3,
                observedAt = 200L,
                firstSeenAt = 100L
            )

        assertEquals(
            "7",
            item.id
        )

        assertEquals(
            "Sender",
            item.sender
        )

        assertEquals(
            "Topic",
            item.topic
        )

        assertTrue(
            item.unread
        )

        assertEquals(
            3,
            item.remoteOrder
        )

        assertEquals(
            100L,
            item.firstSeenAt
        )

        assertEquals(
            200L,
            item.lastSeenAt
        )
    }

    @Test
    fun fullMessageMapper_marksLocalMetadataRead() {

        val remote =
            RemoteFullMessage(
                id = "7",
                sender = "Sender",
                topic = "Topic",
                sendDate = "Date",
                content = "Body"
            )

        val metadata =
            remote.toInboxDomain(
                remoteOrder = 2,
                observedAt = 300L,
                firstSeenAt = 100L
            )

        val content =
            remote.toContentDomain(
                fetchedAt = 300L
            )

        assertFalse(
            metadata.unread
        )

        assertEquals(
            "7",
            content.messageId
        )

        assertEquals(
            "Body",
            content.content
        )

        assertEquals(
            300L,
            content.fetchedAt
        )
    }
}
