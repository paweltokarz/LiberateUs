package pl.local.liberateus.ui.drafts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftEditingPolicyTest {

    @Test
    fun allowsOrdinaryEmptyDraft() {
        assertTrue(
            canEditDraftSafely(
                draftId = "23143",
                type = "NEWONE",
                recipientCount = 0,
                hasAttachment = false
            )
        )
    }

    @Test
    fun rejectsReplyAndForward() {
        for (type in listOf("REPLY", "FORWARD", null)) {
            assertFalse(
                canEditDraftSafely(
                    "23143", type, 0, false
                )
            )
        }
    }

    @Test
    fun rejectsDraftWithRecipients() {
        assertFalse(
            canEditDraftSafely(
                "23143", "NEWONE", 1, false
            )
        )
    }

    @Test
    fun rejectsDraftWithAttachments() {
        assertFalse(
            canEditDraftSafely(
                "23143", "NEWONE", 0, true
            )
        )
    }

    @Test
    fun rejectsInvalidIdentifiers() {
        for (id in listOf("", "-1", "abc", "23/143")) {
            assertFalse(
                canEditDraftSafely(
                    id, "NEWONE", 0, false
                )
            )
        }
    }
}
