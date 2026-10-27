package dev.mementomori.lifeclock.widget.content

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.mementomori.lifeclock.model.LabelPlacement
import dev.mementomori.lifeclock.model.WidgetLayout
import dev.mementomori.lifeclock.widget.render.LayoutPlan
import dev.mementomori.lifeclock.widget.render.RenderedRing
import dev.mementomori.lifeclock.widget.render.WidgetState
import kotlin.math.roundToInt

/**
 * The widget's only composable. Takes a finished [WidgetState], so it can be composed in a unit
 * test with no repositories, no DataStore and no time.
 *
 * [modifier] carries the tap action, so tests can compose the same content without one.
 */
@Composable
fun WidgetContent(state: WidgetState, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp).then(modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            WidgetState.Unconfigured -> Message(WidgetCopy.UNCONFIGURED)
            WidgetState.Unsupported -> Message(WidgetCopy.UNSUPPORTED)
            is WidgetState.Ready -> ReadyContent(state)
        }
    }
}

@Composable
private fun ReadyContent(state: WidgetState.Ready) {
    val planned = LayoutPlan.plan(state.layout, state.rings.size)

    when (state.layout) {
        WidgetLayout.SINGLE, WidgetLayout.STACK -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            planned.forEach { slot -> Ring(state.rings[slot.index]) }
        }

        WidgetLayout.GRID -> Row(horizontalAlignment = Alignment.CenterHorizontally) {
            planned.forEach { slot -> Ring(state.rings[slot.index]) }
        }

        WidgetLayout.CONCENTRIC -> Box(contentAlignment = Alignment.Center) {
            // Outermost first so the inner arcs paint on top; only the first ring's label is drawn,
            // because three labels inside one circle is noise, not information.
            planned.forEach { slot ->
                Ring(state.rings[slot.index], scale = slot.scale, showLabel = slot.index == 0)
            }
        }
    }

    state.chronometer?.let { AndroidRemoteViews(it) }
}

@Composable
private fun Ring(ring: RenderedRing, scale: Float = 1f, showLabel: Boolean = true) {
    val scaled = ring.copy(sizeDp = (ring.sizeDp * scale).roundToInt())
    when {
        !showLabel -> RingImage(scaled)

        // The label sits on top of the bitmap; the bitmap itself never contains text.
        ring.labelPlacement == LabelPlacement.INSIDE -> Box(contentAlignment = Alignment.Center) {
            RingImage(scaled)
            Label(ring.label)
        }

        ring.labelPlacement == LabelPlacement.UNDER -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RingImage(scaled)
            Label(ring.label)
        }

        else -> RingImage(scaled)
    }
}

@Composable
private fun RingImage(ring: RenderedRing) {
    Image(
        provider = ImageProvider(ring.bitmap),
        contentDescription = ring.contentDescription,
        modifier = GlanceModifier.size(ring.sizeDp.dp),
    )
}

@Composable
private fun Label(text: String) {
    if (text.isBlank()) return
    Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
}

@Composable
private fun Message(text: String) {
    Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
}

/** Copy for states that have no config to draw. Kept here so the composable needs no resources. */
internal object WidgetCopy {
    const val UNCONFIGURED = "Open Memento Mori to choose a countdown"
    const val UNSUPPORTED = "Open Memento Mori to finish setting this up"
}
