package dev.mementomori.lifeclock.widget.ring

import org.junit.Assert.assertEquals
import org.junit.Test

/** T2.4 (math half) - the arc's numbers, with no Android in sight. */
class RingGeometryTest {

    @Test
    fun `stroke width is a fraction of the radius`() {
        // 240 px -> radius 120 -> 12% of it.
        assertEquals(14.4f, RingGeometry.strokeWidthPx(240, 0.12), 0.001f)
    }

    @Test
    fun `stroke ratio clamps into the supported range`() {
        assertEquals(0.05, RingGeometry.clampStrokeRatio(0.0), 0.0)
        assertEquals(0.5, RingGeometry.clampStrokeRatio(0.9), 0.0)
        assertEquals(0.2, RingGeometry.clampStrokeRatio(0.2), 0.0)
    }

    @Test
    fun `the stroke straddles the centreline radius`() {
        // 240 px disc, 20 px stroke -> 110 px centreline, leaving 10 px inside and outside.
        assertEquals(110f, RingGeometry.radiusPx(240, 20f), 0.001f)
    }

    @Test
    fun `bounds are inset by half the stroke so it is not clipped`() {
        val bounds = RingGeometry.arcBounds(240, 20f)
        assertEquals(10f, bounds.left, 0.001f)
        assertEquals(10f, bounds.top, 0.001f)
        assertEquals(230f, bounds.right, 0.001f)
        assertEquals(230f, bounds.bottom, 0.001f)
    }

    @Test
    fun `sweep is proportional to the fraction and clamped`() {
        assertEquals(0f, RingGeometry.sweepDegrees(0.0), 0.001f)
        assertEquals(90f, RingGeometry.sweepDegrees(0.25), 0.001f)
        assertEquals(180f, RingGeometry.sweepDegrees(0.5), 0.001f)
        assertEquals(360f, RingGeometry.sweepDegrees(1.0), 0.001f)
        assertEquals(360f, RingGeometry.sweepDegrees(1.7), 0.001f)
        assertEquals(0f, RingGeometry.sweepDegrees(-2.0), 0.001f)
    }
}
