package dev.mementomori.lifeclock.widget.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A 2-column cell on a Pixel 8a is ~114 dp; the default ring is 160 dp, so the arc was clipped on
 * both sides (2026-10-25). The cap must shrink a too-big ring, never grow a small one.
 */
class RingFitTest {

    @Test
    fun `a ring wider than its cell is capped to the cell`() {
        assertEquals(98, fitDiameterDp(configuredDp = 160, cellWidthDp = 114))
    }

    @Test
    fun `a ring that already fits keeps its configured size`() {
        assertEquals(80, fitDiameterDp(configuredDp = 80, cellWidthDp = 114))
    }

    @Test
    fun `an unknown cell width leaves the config alone`() {
        assertEquals(160, fitDiameterDp(configuredDp = 160, cellWidthDp = null))
    }

    @Test
    fun `a very narrow cell still leaves a drawable ring`() {
        assertEquals(MIN_RING_DP, fitDiameterDp(configuredDp = 160, cellWidthDp = 40))
    }
}
