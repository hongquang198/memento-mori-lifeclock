package dev.mementomori.lifeclock.widget.refresh

import dev.mementomori.lifeclock.widget.R

import android.appwidget.AppWidgetManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import dev.mementomori.lifeclock.widget.CountdownWidgetReceiver

/**
 * The worker must draw from the platform's list of placed widgets, not from Glance's own registry.
 *
 * Found on a Pixel 8a: `GlanceAppWidget.updateAll` updated zero widgets while the gate reported
 * `drew=true`, because Glance's registry is filled by the receiver's `onUpdate` - the callback M0
 * measured as unreliable on a cold start. These tests pin the replacement source of truth.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlacedWidgetsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private fun place() = Shadows.shadowOf(manager)
        .createWidget(CountdownWidgetReceiver::class.java, R.layout.widget_initial)

    @Test
    fun `nothing placed means nothing to update`() {
        assertEquals(0, placedWidgetIds(context).size)
    }

    @Test
    fun `every placed instance is enumerated`() {
        val first = place()
        val second = place()

        assertEquals(listOf(first, second), placedWidgetIds(context).toList())
    }
}
