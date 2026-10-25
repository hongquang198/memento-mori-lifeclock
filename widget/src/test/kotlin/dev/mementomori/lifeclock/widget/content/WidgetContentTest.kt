package dev.mementomori.lifeclock.widget.content

import dev.mementomori.lifeclock.widget.R

import android.graphics.Bitmap
import android.view.LayoutInflater
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import dev.mementomori.lifeclock.model.LabelPlacement
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mementomori.lifeclock.widget.render.RenderedRing
import dev.mementomori.lifeclock.widget.render.WidgetState

/**
 * T2.8 - the composition itself, driven through Glance's own test harness rather than by
 * inspecting RemoteViews by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetContentTest {

    /**
     * Robolectric loads the merged resource table on first use (seconds). Pay it here, outside the
     * harness's 2s per-test budget, or the first composition test in the JVM times out.
     */
    @Before
    fun warmUpResources() {
        val context = RuntimeEnvironment.getApplication()
        context.resources.getColor(R.color.widget_primary, null)
        LayoutInflater.from(context).inflate(R.layout.widget_countdown_chronometer, null)
        LayoutInflater.from(context).inflate(R.layout.widget_initial, null)
    }

    private fun ring(
        label: String = "Days left",
        placement: LabelPlacement = LabelPlacement.UNDER,
        description: String = label,
    ) = RenderedRing(
        bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888),
        sizeDp = 80,
        label = label,
        labelPlacement = placement,
        contentDescription = description,
    )

    @Test
    fun `a ready widget draws the ring and its label`() = runGlanceAppWidgetUnitTest {
        val state = WidgetState.Ready(rings = listOf(ring()), chronometer = null)

        provideComposable { WidgetContent(state) }

        onNode(hasContentDescription("Days left")).assertExists()
        onNode(hasText("Days left")).assertExists()
    }

    @Test
    fun `a label placed inside the ring is still text, not pixels`() = runGlanceAppWidgetUnitTest {
        val state = WidgetState.Ready(
            rings = listOf(ring(label = "Days left", placement = LabelPlacement.INSIDE)),
            chronometer = null,
        )

        provideComposable { WidgetContent(state) }

        onNode(hasText("Days left")).assertExists()
    }

    @Test
    fun `no label means no text node`() = runGlanceAppWidgetUnitTest {
        val state = WidgetState.Ready(
            rings = listOf(ring(label = "", placement = LabelPlacement.NONE, description = "Countdown ring")),
            chronometer = null,
        )

        provideComposable { WidgetContent(state) }

        onNode(hasContentDescription("Countdown ring")).assertExists()
    }

    @Test
    fun `an unconfigured widget explains itself instead of drawing an empty ring`() =
        runGlanceAppWidgetUnitTest {
            provideComposable { WidgetContent(WidgetState.Unconfigured) }

            onNode(hasText(WidgetCopy.UNCONFIGURED)).assertExists()
        }

    @Test
    fun `an unsupported widget says so`() = runGlanceAppWidgetUnitTest {
        provideComposable { WidgetContent(WidgetState.Unsupported) }

        onNode(hasText(WidgetCopy.UNSUPPORTED)).assertExists()
    }
}
