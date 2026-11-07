package pl.local.liberateus.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipientNameNullTest {
    @Test fun jsonNullTextIsMissing() {
        assertNull(normalizeRecipientNamePart("null"))
        assertNull(normalizeRecipientNamePart(" NULL "))
    }

    @Test fun emptyNameIsMissing() {
        assertNull(normalizeRecipientNamePart(""))
        assertNull(normalizeRecipientNamePart("  "))
        assertNull(normalizeRecipientNamePart(null))
    }

    @Test fun secretaryNameIsPreserved() {
        assertEquals(
            "sekretariat",
            normalizeRecipientNamePart(" sekretariat ")
        )
    }

    @Test fun ordinaryNamesArePreserved() {
        assertEquals(
            "Anna Nowak",
            normalizeRecipientNamePart("Anna Nowak")
        )
    }
}
