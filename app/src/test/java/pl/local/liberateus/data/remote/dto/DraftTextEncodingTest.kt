package pl.local.liberateus.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class DraftTextEncodingTest {

    private fun encode(value: String): String =
        encodeDraftTextWith(value) { bytes ->
            Base64.getEncoder().encodeToString(bytes)
        }

    @Test
    fun testDraftTitleUsesBase64() {
        assertEquals(
            "TFVfRFJBRlRfVEVTVF8yMDI2MTAzMF8yMzE5MDU=",
            encode("LU_DRAFT_TEST_20261030_231905")
        )
    }

    @Test
    fun testDraftBodyUsesBase64() {
        assertEquals(
            "TGliZXJhdGVVc19zaW5nbGVfd3JpdGVfdGVzdA==",
            encode("LiberateUs_single_write_test")
        )
    }

    @Test
    fun polishCharactersUseUtf8BeforeBase64() {
        assertEquals(
            "WmHFvMOzxYLEhyBnxJnFm2zEhSBqYcW6xYQ=",
            encode("Zażółć gęślą jaźń")
        )
    }

    @Test
    fun htmlAndEmptyStringsArePreserved() {
        assertEquals(
            "PHA+QWxhICZhbXA7IEtvdDwvcD4=",
            encode("<p>Ala &amp; Kot</p>")
        )
        assertEquals("", encode(""))
    }
}
