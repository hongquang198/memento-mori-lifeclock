package dev.mementomori.lifeclock.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** T2.6 in the widget: the roles a stored config may name actually resolve. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResourceThemePaletteTest {

    private val context = RuntimeEnvironment.getApplication()
    private val palette = ResourceThemePalette(context)

    @Test
    fun `every theme role resolves to its declared colour`() {
        assertEquals(context.getColor(R.color.widget_primary), palette.argbFor("primary"))
        assertEquals(context.getColor(R.color.widget_secondary), palette.argbFor("secondary"))
        assertEquals(context.getColor(R.color.widget_outline), palette.argbFor("outline"))
        assertEquals(context.getColor(R.color.widget_surface), palette.argbFor("surface"))
        assertEquals(context.getColor(R.color.widget_on_surface), palette.argbFor("on_surface"))
        assertEquals(context.getColor(R.color.widget_error), palette.argbFor("error"))
    }

    @Test
    fun `an unknown role resolves to null so the caller can fall back`() {
        assertNull(palette.argbFor("tertiary"))
    }
}
