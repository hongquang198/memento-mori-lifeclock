package dev.mementomori.lifeclock.widget.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T2.6 - config strings to concrete ARGB. */
class RingColorTest {

    private val primary = 0xFF3366FF.toInt()
    private val palette = ThemePalette { role -> if (role == "primary") primary else null }
    private val fallback = 0xFF999999.toInt()

    @Test
    fun `six digits are opaque RRGGBB`() {
        assertEquals(RingColor.Fixed(0xFF112233.toInt()), RingColor.parse("#112233"))
    }

    @Test
    fun `eight digits carry their own alpha`() {
        assertEquals(RingColor.Fixed(0x80112233.toInt()), RingColor.parse("#80112233"))
    }

    @Test
    fun `theme roles are normalised`() {
        assertEquals(RingColor.Theme("primary"), RingColor.parse("theme:PRIMARY"))
        assertEquals(RingColor.Theme("primary"), RingColor.parse("  theme:primary  "))
    }

    @Test
    fun `garbage parses to null instead of throwing`() {
        assertNull(RingColor.parse(""))
        assertNull(RingColor.parse("#12345"))
        assertNull(RingColor.parse("#GGGGGG"))
        assertNull(RingColor.parse("theme:"))
        assertNull(RingColor.parse("red"))
    }

    @Test
    fun `theme roles resolve through the palette`() {
        assertEquals(primary, RingColors.resolve("theme:primary", palette, fallback))
    }

    @Test
    fun `an unknown role falls back rather than rendering black`() {
        assertEquals(fallback, RingColors.resolve("theme:tertiary", palette, fallback))
    }

    @Test
    fun `unparseable strings fall back`() {
        assertEquals(fallback, RingColors.resolve("not-a-colour", palette, fallback))
    }
}
