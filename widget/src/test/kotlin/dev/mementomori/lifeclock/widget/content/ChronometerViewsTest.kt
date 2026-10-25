package dev.mementomori.lifeclock.widget.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T2.8 (clock half) - the arithmetic that makes a Chronometer count down from the right instant. */
class ChronometerViewsTest {

    @Test
    fun `the base is now plus what is left`() {
        assertEquals(6_000L, ChronometerViews.baseFor(nowElapsedRealtime = 1_000L, remainingMillis = 5_000L))
    }

    @Test
    fun `a zero remainder starts the clock at now`() {
        assertEquals(1_000L, ChronometerViews.baseFor(1_000L, 0L))
    }

    @Test
    fun `nesting is only attempted where it is verified`() {
        assertFalse(ChronometerViews.canEmbed(sdkInt = 30))
        assertTrue(ChronometerViews.canEmbed(sdkInt = 31))
        assertTrue(ChronometerViews.canEmbed(sdkInt = 36))
    }
}
