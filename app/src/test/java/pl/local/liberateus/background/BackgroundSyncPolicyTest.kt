package pl.local.liberateus.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSyncPolicyTest {

    @Test
    fun backgroundSyncWindow_isThirtyToFortyFiveMinutes() {

        assertEquals(
            45L,
            BACKGROUND_REPEAT_MINUTES
        )

        assertEquals(
            15L,
            BACKGROUND_FLEX_MINUTES
        )

        assertEquals(
            30L,
            BACKGROUND_EARLIEST_MINUTES
        )
    }

    @Test
    fun backgroundSyncWindow_isOrderedCorrectly() {

        assertTrue(
            BACKGROUND_EARLIEST_MINUTES <
                BACKGROUND_REPEAT_MINUTES
        )

        assertEquals(
            BACKGROUND_REPEAT_MINUTES,
            BACKGROUND_EARLIEST_MINUTES +
                BACKGROUND_FLEX_MINUTES
        )
    }
}
