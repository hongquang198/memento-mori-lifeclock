package dev.mementomori.lifeclock.widget.ring

import dev.mementomori.lifeclock.model.LabelPlacement
import dev.mementomori.lifeclock.model.RingStyleRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/** T2.7 - the boundary where a stored config becomes something safe to draw. */
class RingStyleResolverTest {

    private val palette = ThemePalette { role -> if (role == "primary") 0xFF00FF00.toInt() else null }
    private val fallback = 0xFF222222.toInt()
    private val resolver = RingStyleResolver(density = 2f, palette = palette, fallbackArgb = fallback)

    @Test
    fun `dp becomes px using the compose density`() {
        val style = resolver.resolve(RingStyleRecord(diameterDp = 80))
        assertEquals(160, style.diameterPx)
    }

    @Test
    fun `a zero diameter still yields a drawable bitmap`() {
        assertEquals(1, resolver.resolve(RingStyleRecord(diameterDp = 0)).diameterPx)
    }

    @Test
    fun `out-of-range stroke ratios are clamped, not rejected`() {
        assertEquals(0.5, resolver.resolve(RingStyleRecord(strokeRatio = 0.95)).strokeRatio, 0.0)
        assertEquals(0.05, resolver.resolve(RingStyleRecord(strokeRatio = 0.001)).strokeRatio, 0.0)
    }

    @Test
    fun `theme colours are materialised`() {
        val style = resolver.resolve(RingStyleRecord(color = "theme:primary", trackColor = "#010203"))
        assertEquals(0xFF00FF00.toInt(), style.color)
        assertEquals(0xFF010203.toInt(), style.trackColor)
    }

    @Test
    fun `unknown colours use the fallback`() {
        val style = resolver.resolve(RingStyleRecord(color = "theme:nope", trackColor = "wat"))
        assertEquals(fallback, style.color)
        assertEquals(fallback, style.trackColor)
    }

    @Test
    fun `label, placement and start angle are carried through untouched`() {
        val record = RingStyleRecord(
            label = "Days left",
            labelPlacement = LabelPlacement.INSIDE,
            startAngleDeg = 45f,
        )
        val style = resolver.resolve(record)
        assertEquals("Days left", style.label)
        assertEquals(LabelPlacement.INSIDE, style.labelPlacement)
        assertEquals(45f, style.startAngleDeg, 0.0f)
    }
}
