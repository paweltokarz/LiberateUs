package pl.local.liberateus.ui.drafts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftRecipientPayloadTest {

    @Test
    fun emptySelectionProducesEmptyArray() {
        assertEquals(
            """{"schoolReceivers":[]}""",
            buildSchoolRecipientsJson(
                kotlin.collections.emptyList()
            )
        )
    }

    @Test
    fun oneRecipientIsSerialized() {
        assertEquals(
            """{"schoolReceivers":[{"accountId":"123"}]}""",
            buildSchoolRecipientsJson(listOf("123"))
        )
    }

    @Test
    fun duplicateRecipientsAreRemoved() {
        assertEquals(
            """{"schoolReceivers":[{"accountId":"123"},{"accountId":"456"}]}""",
            buildSchoolRecipientsJson(
                listOf("123", "456", "123")
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun pathAsRecipientIdIsRejected() {
        buildSchoolRecipientsJson(
            listOf("../123")
        )
    }

    @Test
    fun recipientNamesAreJoinedWithCommas() {
        assertEquals(
            "Jan Kowalski, Anna Nowak",
            selectedRecipientNames(
                ids = listOf("101", "102"),
                knownNames = mapOf(
                    "101" to "Jan Kowalski",
                    "102" to "Anna Nowak"
                )
            )
        )
    }

    @Test
    fun unknownRecipientUsesIdAsFallback() {
        assertEquals(
            "Jan Kowalski, ID 999",
            selectedRecipientNames(
                ids = listOf("101", "999"),
                knownNames = mapOf(
                    "101" to "Jan Kowalski"
                )
            )
        )
    }

    @Test
    fun recipientSummaryUpdatesAfterDeselection() {
        assertEquals(
            "Anna Nowak",
            selectedRecipientNames(
                ids = listOf("102"),
                knownNames = mapOf(
                    "101" to "Jan Kowalski",
                    "102" to "Anna Nowak"
                )
            )
        )
    }

    @Test
    fun serverIdentifierCanBeNormalized() {
        assertEquals(
            "123456",
            normalizeSchoolRecipientId(" 123456 ")
        )
    }

    @Test
    fun missingOrInvalidIdentifierIsRejected() {
        assertNull(normalizeSchoolRecipientId(null))
        assertNull(normalizeSchoolRecipientId(""))
        assertNull(normalizeSchoolRecipientId("../123"))
        assertNull(normalizeSchoolRecipientId("abc"))
    }
}
