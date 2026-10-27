package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.model.WidgetLayout

/** One ring as an arrangement will draw it: [scale] shrinks it for nested layouts. */
data class PlannedRing(val index: Int, val scale: Float)

/**
 * How a layout turns N authored rings into what is actually drawn.
 *
 * Kept out of the composable so the arrangement rules are plain Kotlin: a Glance test can prove a
 * widget composes, but it cannot easily say *which* ring went where.
 */
object LayoutPlan {

    /** Concentric rings shrink by this step; the fourth ring is the smallest that still reads. */
    private const val CONCENTRIC_STEP = 0.22f
    private const val MIN_SCALE = 0.3f

    fun plan(layout: WidgetLayout, ringCount: Int): List<PlannedRing> {
        if (ringCount <= 0) return emptyList()

        return when (layout) {
            // SINGLE shows the first ring only: the user authored several, this layout shows one.
            WidgetLayout.SINGLE -> listOf(PlannedRing(0, 1f))

            WidgetLayout.CONCENTRIC -> (0 until ringCount).map { index ->
                PlannedRing(index, (1f - CONCENTRIC_STEP * index).coerceAtLeast(MIN_SCALE))
            }

            // Stack and grid draw every ring at its authored size; the container decides the axis.
            WidgetLayout.STACK, WidgetLayout.GRID -> (0 until ringCount).map { PlannedRing(it, 1f) }
        }
    }
}
