package dev.mementomori.lifeclock.widget.ring

import dev.mementomori.lifeclock.model.RingStyleRecord
import kotlin.math.roundToInt

/**
 * Config -> drawable. The single place where clamping, dp-to-px and theme resolution happen, so a
 * bad stored value can never reach the bitmap.
 */
class RingStyleResolver(
    private val density: Float,
    private val palette: ThemePalette,
    private val fallbackArgb: Int,
) {
    fun resolve(record: RingStyleRecord): RingStyle = RingStyle(
        diameterPx = (record.diameterDp * density).roundToInt().coerceAtLeast(1),
        strokeRatio = RingGeometry.clampStrokeRatio(record.strokeRatio),
        color = RingColors.resolve(record.color, palette, fallbackArgb),
        trackColor = RingColors.resolve(record.trackColor, palette, fallbackArgb),
        label = record.label,
        labelPlacement = record.labelPlacement,
        startAngleDeg = record.startAngleDeg,
    )
}
