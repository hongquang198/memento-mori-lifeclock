package dev.mementomori.lifeclock.widget.ring

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Rasterizes one arc. Returns an Android bitmap because that is what Glance's `ImageProvider` takes. */
fun interface RingRenderer {
    /** Callers must treat the returned bitmap as immutable. */
    fun render(style: RingStyle, fraction: Double): Bitmap
}

/**
 * The determinate arc Glance cannot draw itself (`CircularProgressIndicator` is indeterminate-only).
 *
 * Track is a full circle; progress is `360 × fraction` from [RingStyle.startAngleDeg].
 */
class CanvasRingRenderer : RingRenderer {

    override fun render(style: RingStyle, fraction: Double): Bitmap {
        val size = style.diameterPx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val stroke = RingGeometry.strokeWidthPx(size, style.strokeRatio)
        val bounds = RingGeometry.arcBounds(size, stroke)
        val rect = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val centre = size / 2f
        val radius = RingGeometry.radiusPx(size, stroke)

        // A full circle is drawCircle's job: a 360 degree arc seam lets the round cap double-draw.
        canvas.drawCircle(centre, centre, radius, paint(style.trackColor, stroke))

        when {
            // At exactly one, the arc's caps would overlap at the seam.
            fraction >= 1.0 -> canvas.drawCircle(centre, centre, radius, paint(style.color, stroke))
            // A round cap at fraction 0 would draw a dot, which reads as "a little progress".
            fraction > 0.0 -> canvas.drawArc(
                rect,
                style.startAngleDeg,
                RingGeometry.sweepDegrees(fraction),
                false,
                paint(style.color, stroke),
            )
        }

        return bitmap
    }

    private fun paint(argb: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
    }
}

/** Decorator: identical frames are drawn once. A 1/120 step is well below what a launcher shows. */
class CachedRingRenderer(
    private val delegate: RingRenderer = CanvasRingRenderer(),
    private val buckets: Int = 120,
    maxEntries: Int = 32,
) : RingRenderer {

    private val cache = LruCache<RingCacheKey, Bitmap>(maxEntries)

    override fun render(style: RingStyle, fraction: Double): Bitmap {
        val key = RingCacheKey.of(style, fraction, buckets)
        return cache.getOrPut(key) { delegate.render(style, key.fraction) }
    }
}
