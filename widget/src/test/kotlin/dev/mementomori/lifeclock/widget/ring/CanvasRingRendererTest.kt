package dev.mementomori.lifeclock.widget.ring

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T2.4 (drawing half). With native graphics Robolectric renders real pixels, so these assertions are
 * about what the widget actually shows rather than about the code path being called.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanvasRingRendererTest {

    private val fill = 0xFFFF0000.toInt()
    private val track = 0xFF0000FF.toInt()
    private val renderer = CanvasRingRenderer()

    private fun style(diameter: Int = 120) = RingStyle(
        diameterPx = diameter,
        strokeRatio = 0.12,
        color = fill,
        trackColor = track,
    )

    /** Exact-colour pixel count; anti-aliased edge pixels are blends and are ignored. */
    private fun count(bitmap: Bitmap, argb: Int): Int {
        var found = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (bitmap.getPixel(x, y) == argb) found++
            }
        }
        return found
    }

    @Test
    fun `bitmap matches the requested diameter`() {
        val bitmap = renderer.render(style(120), 0.5)
        assertEquals(120, bitmap.width)
        assertEquals(120, bitmap.height)
    }

    @Test
    fun `the middle is empty, because this is a ring not a disc`() {
        assertEquals(0, renderer.render(style(120), 0.5).getPixel(60, 60))
    }

    @Test
    fun `fraction zero draws the track and no progress at all`() {
        val bitmap = renderer.render(style(), 0.0)
        assertEquals(0, count(bitmap, fill))
        assertTrue(count(bitmap, track) > 0)
    }

    @Test
    fun `fraction half draws progress over part of the track`() {
        val bitmap = renderer.render(style(), 0.5)
        assertTrue(count(bitmap, fill) > 0)
        assertTrue(count(bitmap, track) > 0)
    }

    @Test
    fun `fraction one covers almost all of the track`() {
        val half = renderer.render(style(), 0.5)
        val full = renderer.render(style(), 1.0)

        assertTrue(count(full, fill) > count(half, fill))
        assertTrue(count(full, track) < count(half, track))
    }

    @Test
    fun `stroke width is respected, a thicker ring covers more pixels`() {
        val thin = renderer.render(style().copy(strokeRatio = 0.05), 1.0)
        val thick = renderer.render(style().copy(strokeRatio = 0.4), 1.0)
        assertTrue(count(thick, fill) > count(thin, fill))
    }

    @Test
    fun `cached renderer returns the identical bitmap for the same bucket`() {
        val cached = CachedRingRenderer(CanvasRingRenderer(), buckets = 10)
        val first = cached.render(style(), 0.51)
        val second = cached.render(style(), 0.59)
        assertTrue(first === second)
    }
}
