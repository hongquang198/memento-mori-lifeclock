package dev.mementomori.lifeclock.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * M0 spike (T0.2) — proves we can render a **determinate** progress ring.
 *
 * Why this exists: Glance's `CircularProgressIndicator` is indeterminate-only, so a real
 * progress arc cannot be expressed with Glance composables. Instead we rasterize the arc
 * ourselves into a [Bitmap] and hand that bitmap to the widget as an image.
 *
 * This is throwaway spike code; the production renderer (M2) reuses the same technique but
 * with the full `RingStyle` contract, anti-aliased track/progress and an LRU cache.
 */
fun renderSpikeRing(
    fraction: Float,
    sizePx: Int = 240,
    strokeRatio: Float = 0.12f,
    color: Int = 0xFFE5484D.toInt(),
    trackColor: Int = 0xFF2A2A2E.toInt(),
): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val stroke = sizePx * strokeRatio
    val inset = stroke / 2f
    val arcBounds = RectF(inset, inset, sizePx - inset, sizePx - inset)

    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        this.color = trackColor
    }
    val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        this.color = color
    }

    // Full track, then the determinate progress arc starting at 12 o'clock.
    canvas.drawArc(arcBounds, 0f, 360f, false, track)
    canvas.drawArc(arcBounds, -90f, 360f * fraction.coerceIn(0f, 1f), false, progress)

    return bitmap
}
