package dev.mementomori.lifeclock.widget.render

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Glance's `update()` returns before it composes, and on the Pixel 8a (2026-10-27) the hand-off
 * was dropped at random: eight draws 15 s apart produced five compositions, sometimes for only one
 * of the two placed widgets. A draw therefore has to be observed and retried - these tests pin that
 * contract without Glance.
 */
class ComposedDrawTest {

    @Test
    fun `a draw that composed is not repeated`() = runBlocking {
        val probe = CompositionProbe()
        var updates = 0

        val composed = ComposedDraw(probe, attempts = 3, settleMs = 50).run(22) {
            updates++
            probe.mark(22)
        }

        assertTrue(composed)
        assertEquals(1, updates)
    }

    @Test
    fun `a dropped draw is retried and reported`() = runBlocking {
        val probe = CompositionProbe()
        var updates = 0

        val composed = ComposedDraw(probe, attempts = 3, settleMs = 10).run(22) { updates++ }

        assertFalse(composed)
        assertEquals(3, updates)
    }

    @Test
    fun `a later attempt can be the one that composes`() = runBlocking {
        val probe = CompositionProbe()
        var updates = 0

        val composed = ComposedDraw(probe, attempts = 3, settleMs = 10).run(22) {
            updates++
            if (updates == 2) probe.mark(22)
        }

        assertTrue(composed)
        assertEquals(2, updates)
    }

    @Test
    fun `the probe counts per widget, so one widget's draw cannot satisfy another's`() = runBlocking {
        val probe = CompositionProbe()
        probe.mark(22)
        probe.mark(22)
        probe.mark(23)

        assertEquals(2, probe.count(22))
        assertEquals(1, probe.count(23))
        assertFalse(probe.awaitIncrease(23, above = 1, withinMs = 20))
        assertFalse(probe.awaitIncrease(24, above = 0, withinMs = 20))
    }

    @Test
    fun `the retry hook runs only between attempts, never before the first`() = runBlocking {
        val probe = CompositionProbe()
        var retries = 0
        var updates = 0

        ComposedDraw(probe, attempts = 3, settleMs = 10).run(
            appWidgetId = 22,
            beforeRetry = { retries++ },
        ) { updates++ }

        assertEquals(3, updates)
        assertEquals(2, retries)
    }
}
