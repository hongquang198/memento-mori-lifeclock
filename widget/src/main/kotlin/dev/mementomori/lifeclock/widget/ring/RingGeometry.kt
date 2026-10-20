package dev.mementomori.lifeclock.widget.ring

/** Result of fitting an arc inside a square bitmap. A plain data class so this file needs no Android. */
data class ArcBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Arc maths, deliberately free of Android types so it can be tested on the JVM. */
object RingGeometry {
    const val MIN_STROKE_RATIO = 0.05
    const val MAX_STROKE_RATIO = 0.5

    fun clampStrokeRatio(value: Double): Double = value.coerceIn(MIN_STROKE_RATIO, MAX_STROKE_RATIO)

    /** [strokeRatio] is relative to the radius. A ratio of 0.5 makes the ring fill the disc. */
    fun strokeWidthPx(diameterPx: Int, strokeRatio: Double): Float =
        (diameterPx / 2.0 * clampStrokeRatio(strokeRatio)).toFloat()

    /** Centreline radius: the stroke straddles it, so it stays inside the bitmap. */
    fun radiusPx(diameterPx: Int, strokeWidthPx: Float): Float = diameterPx / 2f - strokeWidthPx / 2f

    /** Inset by half the stroke, otherwise the stroke is clipped by the bitmap edges. */
    fun arcBounds(diameterPx: Int, strokeWidthPx: Float): ArcBounds {
        val inset = strokeWidthPx / 2f
        return ArcBounds(inset, inset, diameterPx - inset, diameterPx - inset)
    }

    fun sweepDegrees(fraction: Double): Float = (360.0 * fraction.coerceIn(0.0, 1.0)).toFloat()
}
