package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.model.WidgetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T4.3 (decision half) - which authored rings a layout actually draws, and at what size. */
class LayoutPlanTest {

    @Test
    fun `single draws the first ring only`() {
        assertEquals(listOf(PlannedRing(0, 1f)), LayoutPlan.plan(WidgetLayout.SINGLE, 3))
    }

    @Test
    fun `stack and grid draw every ring at its authored size`() {
        val expected = listOf(PlannedRing(0, 1f), PlannedRing(1, 1f), PlannedRing(2, 1f))
        assertEquals(expected, LayoutPlan.plan(WidgetLayout.STACK, 3))
        assertEquals(expected, LayoutPlan.plan(WidgetLayout.GRID, 3))
    }

    @Test
    fun `concentric nests rings and never shrinks one into a dot`() {
        val plan = LayoutPlan.plan(WidgetLayout.CONCENTRIC, 4)

        assertEquals(4, plan.size)
        assertTrue(plan.zipWithNext().all { (outer, inner) -> outer.scale > inner.scale })
        assertTrue("the smallest ring must still be drawable", plan.last().scale >= 0.3f)
    }

    @Test
    fun `a config with no rings plans nothing`() {
        WidgetLayout.entries.forEach { layout ->
            assertTrue(LayoutPlan.plan(layout, 0).isEmpty())
        }
    }
}
