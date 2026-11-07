package pl.local.liberateus.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.liberateus.data.local.MessageContentEntity
import pl.local.liberateus.data.local.MessageEntity
import pl.local.liberateus.domain.model.InboxItem
import pl.local.liberateus.domain.model.MessageContent

class MessageMappersTest {

    private val account =
        "account-A"

    private val entity =
        MessageEntity(
            accountId = account,
            messageId = "12345",
            sender = "Jan Kowalski",
            topic = "Test message",
            sendDateRaw =
                "2026-10-27 12:34",
            sentAtEpochMillis =
                1793100840000L,
            unread = true,
            remoteOrder = 3,
            firstSeenAt =
                1793101000000L,
            lastSeenAt =
                1793102000000L
        )

    @Test
    fun messageEntity_roundTripsThroughDomain() {

        val domain =
            entity.toDomain()

        assertEquals(
            entity.messageId,
            domain.id
        )

        assertEquals(
            entity.sender,
            domain.sender
        )

        assertEquals(
            entity.topic,
            domain.topic
        )

        assertEquals(
            entity.sendDateRaw,
            domain.sendDateRaw
        )

        assertEquals(
            entity.sentAtEpochMillis,
            domain.sentAtEpochMillis
        )

        assertEquals(
            entity.unread,
            domain.unread
        )

        assertEquals(
            entity.remoteOrder,
            domain.remoteOrder
        )

        assertEquals(
            entity.firstSeenAt,
            domain.firstSeenAt
        )

        assertEquals(
            entity.lastSeenAt,
            domain.lastSeenAt
        )

        assertEquals(
            entity,
            domain.toEntity(account)
        )
    }

    @Test
    fun messageContent_roundTripsThroughDomain() {

        val entity =
            MessageContentEntity(
                accountId = account,
                messageId = "12345",
                content =
                    "Treść wiadomości",
                fetchedAt =
                    1793103000000L
            )

        val domain =
            entity.toDomain()

        assertEquals(
            entity.messageId,
            domain.messageId
        )

        assertEquals(
            entity.content,
            domain.content
        )

        assertEquals(
            entity.fetchedAt,
            domain.fetchedAt
        )

        assertEquals(
            entity,
            domain.toEntity(account)
        )
    }

    @Test
    fun messageAggregate_withoutContent_isValid() {

        val message =
            entity.toDomainMessage(
                null
            )

        assertEquals(
            "12345",
            message.id
        )

        assertNull(
            message.content
        )

        assertFalse(
            message.hasCachedContent
        )
    }

    @Test
    fun messageAggregate_withMatchingContent_isValid() {

        val content =
            MessageContentEntity(
                accountId = account,
                messageId =
                    entity.messageId,
                content =
                    "Cached body",
                fetchedAt = 123L
            )

        val message =
            entity.toDomainMessage(
                content
            )

        assertTrue(
            message.hasCachedContent
        )

        assertEquals(
            "Cached body",
            message.content?.content
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun messageAggregate_rejectsAnotherMessageBody() {

        val wrong =
            MessageContentEntity(
                accountId = account,
                messageId =
                    "different-message",
                content = "Wrong",
                fetchedAt = 123L
            )

        entity.toDomainMessage(
            wrong
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun messageAggregate_rejectsAnotherAccountBody() {

        val wrong =
            MessageContentEntity(
                accountId =
                    "account-B",
                messageId =
                    entity.messageId,
                content = "Wrong",
                fetchedAt = 123L
            )

        entity.toDomainMessage(
            wrong
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun inboxItem_rejectsBlankAccountWhenPersisting() {

        InboxItem(
            id = "1",
            sender = "A",
            topic = "B",
            sendDateRaw = "C",
            sentAtEpochMillis = null,
            unread = false,
            remoteOrder = 0,
            firstSeenAt = 1L,
            lastSeenAt = 1L
        ).toEntity(
            accountId = ""
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun content_rejectsBlankAccountWhenPersisting() {

        MessageContent(
            messageId = "1",
            content = "Body",
            fetchedAt = 1L
        ).toEntity(
            accountId = ""
        )
    }
}
