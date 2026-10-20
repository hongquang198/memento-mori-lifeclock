package dev.mementomori.lifeclock.widget.ring

import dev.mementomori.lifeclock.model.LabelPlacement

/**
 * Concrete appearance of one arc, after resolution: dp already converted to px, colours already
 * ARGB. The renderer needs this shape because a [android.graphics.Bitmap] cannot hold a theme role.
 */
data class RingStyle(
    val diameterPx: Int,
    /** Stroke width as a fraction of the **radius** (0.05..0.5). */
    val strokeRatio: Double,
    val color: Int,
    val trackColor: Int,
    val label: String = "",
    val labelPlacement: LabelPlacement = LabelPlacement.NONE,
    val startAngleDeg: Float = -90f,
)
