package pl.local.liberateus.data

import org.junit.Assert.*
import org.junit.Test

class SendRejectionTest {
    @Test fun rejectedRequests() {
        assertTrue(isDefinitiveSendRejection(400))
        assertTrue(isDefinitiveSendRejection(403))
        assertTrue(isDefinitiveSendRejection(422))
    }

    @Test fun uncertainRequests() {
        for (code in listOf(200, 408, 425, 429, 500, 503)) {
            assertFalse(isDefinitiveSendRejection(code))
        }
    }

    @Test fun extractsServerReason() {
        assertEquals(
            "Recipient not allowed",
            readableSendRejection(
                """{"message":"Recipient not allowed"}"""
            )
        )
    }

    @Test fun rejectionIsClear() {
        val message = LibrusSendRejectedException(
            422, "Recipient not allowed"
        ).message.orEmpty()
        assertTrue(message.contains("odrzucił"))
        assertTrue(message.contains("HTTP 422"))
        assertTrue(message.contains("Recipient not allowed"))
    }
}
