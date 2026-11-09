package dev.mementomori.lifeclock.widget.refresh

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.mementomori.lifeclock.widget.CountdownWidgetReceiver
import dev.mementomori.lifeclock.widget.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The regression guard for 2026-10-25: the worker drew nothing while the gate logged `drew=true`.
 *
 * The draw used `GlanceAppWidget.updateAll`, which iterates **Glance's own id registry** — written
 * by the receiver's `onUpdate`, the callback M0 measured losing on a cold start. On the Pixel the
 * registry was empty, so the loop ran zero times and nothing reached the launcher.
 *
 * The assertion is "one draw call per id the platform reports", which is the fix itself. It cannot
 * be asserted through `getViewFor`: Glance hands composition to a session scope, so Robolectric
 * never observes the apply. The platform receipt stays a device observation (screenshots + logcat).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdatePlacedWidgetsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private fun place(): Int = Shadows.shadowOf(manager)
        .createWidget(CountdownWidgetReceiver::class.java, R.layout.widget_initial)

    @Test
    fun `every placed instance is drawn`() = runBlocking {
        val first = place()
        val second = place()
        val drawn = mutableListOf<Int>()

        assertEquals(2, updatePlacedWidgets(context) { drawn += it.appWidgetId })
        assertEquals(listOf(first, second), drawn)
    }

    @Test
    fun `nothing placed means no draw calls`() = runBlocking {
        var calls = 0

        assertEquals(0, updatePlacedWidgets(context) { calls++ })
        assertEquals(0, calls)
    }
}
