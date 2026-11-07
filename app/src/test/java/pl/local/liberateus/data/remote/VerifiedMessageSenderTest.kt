package pl.local.liberateus.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedMessageSenderTest {
    private fun matches(
        old: Set<String> = setOf("old"),
        id: String = "new",
        topic: String = "Temat",
        body: String = "Treść",
        receiver: String? = "123",
        recipients: Set<String> = setOf("123")
    ) = matchesNewSentMessage(
        old, id, topic, body, receiver,
        "Temat", "Treść", recipients
    )

    @Test fun newMatchingMessage() {
        assertTrue(matches())
    }

    @Test fun previousMessageIsNotConfirmation() {
        assertFalse(matches(id = "old"))
    }

    @Test fun mismatchedTopic() {
        assertFalse(matches(topic = "Inny temat"))
    }

    @Test fun mismatchedBody() {
        assertFalse(matches(body = "Inna treść"))
    }

    @Test fun mismatchedRecipient() {
        assertFalse(matches(receiver = "999"))
    }

    @Test fun emptyRecipients() {
        assertFalse(matches(recipients = emptySet()))
    }

    @Test fun missingId() {
        assertFalse(matches(id = ""))
    }

    @Test fun unavailableRecipientMetadata() {
        assertTrue(matches(receiver = null))
    }
    @Test fun trailingTopicWhitespaceDoesNotBlockConfirmation() {
        assertTrue(matches(topic = "Temat "))
    }

    @Test fun sentFolderBodyPreviewIsAccepted() {
        val full = "Dzień dobry,\\n" + "Przykładowa wiadomość. ".repeat(15)
        assertTrue(matchesNewSentMessage(
            setOf("old"), "new", "Temat ", full.take(95),
            "123", "Temat", full, setOf("123")
        ))
    }

    @Test fun unrelatedPreviewDoesNotConfirm() {
        val full = "Przykładowa wiadomość. ".repeat(15)
        assertFalse(matchesNewSentMessage(
            setOf("old"), "new", "Temat",
            "Zupełnie inna wiadomość. ".repeat(5),
            "123", "Temat", full, setOf("123")
        ))
    }

    @Test fun veryShortPreviewIsNotEnough() {
        val full = "Przykładowa wiadomość. ".repeat(15)
        assertFalse(matchesNewSentMessage(
            setOf("old"), "new", "Temat", "Przykładowa",
            "123", "Temat", full, setOf("123")
        ))
    }

}
