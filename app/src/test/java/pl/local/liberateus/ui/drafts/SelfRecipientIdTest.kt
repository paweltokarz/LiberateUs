package pl.local.liberateus.ui.drafts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelfRecipientIdTest {
    @Test fun singleOwner() {
        assertEquals("3778472",
            inferSelfRecipientId(listOf("3778472")))
    }

    @Test fun repeatedOwner() {
        assertEquals("3778472",
            inferSelfRecipientId(listOf("3778472", "3778472")))
    }

    @Test fun noOwner() {
        assertNull(inferSelfRecipientId(listOf(null, "", " ")))
    }

    @Test fun conflictingOwners() {
        assertNull(inferSelfRecipientId(
            listOf("3778472", "3778438")))
    }

    @Test fun invalidOwner() {
        assertNull(inferSelfRecipientId(listOf("abc123")))
    }
}
