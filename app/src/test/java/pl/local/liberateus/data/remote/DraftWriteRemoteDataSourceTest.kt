package pl.local.liberateus.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.local.liberateus.data.remote.dto.DraftWritePayload
import pl.local.liberateus.data.remote.dto.DraftWriteResult

class DraftWriteRemoteDataSourceTest {

    private class FakeBackend : DraftWriteBackend {
        var creates = 0
        var updates = 0
        var deletes = 0
        var lastDeletedId: String? = null
        var lastId: String? = null
        var lastPayload: DraftWritePayload? = null

        override fun createDraft(
            payload: DraftWritePayload
        ): DraftWriteResult {
            creates++
            lastPayload = payload

            return DraftWriteResult(
                identifier = "101",
                httpCode = 201
            )
        }

        override fun updateDraft(
            draftId: String,
            payload: DraftWritePayload
        ): DraftWriteResult {
            updates++
            lastId = draftId
            lastPayload = payload

            return DraftWriteResult(
                identifier = draftId,
                httpCode = 200
            )
        }

        override fun deleteDraft(draftId: String) {
            deletes++
            lastDeletedId = draftId
        }
    }

    @Test
    fun createDelegatesExactlyOnce() {
        val fake = FakeBackend()
        val source = DraftWriteRemoteDataSource(fake)

        val payload = DraftWritePayload(
            topic = "Test",
            content = "Draft body"
        )

        val result = source.createDraft(payload)

        assertEquals(1, fake.creates)
        assertEquals(0, fake.updates)
        assertEquals(payload, fake.lastPayload)
        assertEquals("101", result.identifier)
        assertEquals(201, result.httpCode)
    }

    @Test
    fun updatePreservesIdentifier() {
        val fake = FakeBackend()
        val source = DraftWriteRemoteDataSource(fake)

        val payload = DraftWritePayload(
            topic = "Updated",
            content = "Updated body"
        )

        val result = source.updateDraft(
            "789",
            payload
        )

        assertEquals(0, fake.creates)
        assertEquals(1, fake.updates)
        assertEquals("789", fake.lastId)
        assertEquals(payload, fake.lastPayload)
        assertEquals("789", result.identifier)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateRejectsInvalidIdentifier() {
        val fake = FakeBackend()
        val source = DraftWriteRemoteDataSource(fake)

        source.updateDraft(
            "../messages",
            DraftWritePayload(
                topic = "",
                content = ""
            )
        )
    }

    @Test
    fun defaultPayloadHasNoStorageId() {
        val payload = DraftWritePayload(
            topic = "Test",
            content = "Body"
        )

        assertNull(payload.storageId)
        assertEquals(
            """{"schoolReceivers":[]}""",
            payload.receiversJson
        )
    }

    @Test
    fun deleteIsDelegatedOnce() {
        val fake = FakeBackend()
        val source = DraftWriteRemoteDataSource(fake)

        source.deleteDraft("23143")

        assertEquals(1, fake.deletes)
        assertEquals("23143", fake.lastDeletedId)
        assertEquals(0, fake.creates)
        assertEquals(0, fake.updates)
    }

    @Test(expected = IllegalArgumentException::class)
    fun deleteRejectsEmptyId() {
        DraftWriteRemoteDataSource(FakeBackend())
            .deleteDraft("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun deleteRejectsPathTraversal() {
        DraftWriteRemoteDataSource(FakeBackend())
            .deleteDraft("../23143")
    }
}
