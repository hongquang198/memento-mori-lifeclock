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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.mementomori.lifeclock.model.LabelPlacement
import dev.mementomori.lifeclock.widget.render.RenderedRing
import dev.mementomori.lifeclock.widget.render.WidgetState

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
    state.rings.forEach { ring -> Ring(ring) }
    state.chronometer?.let { AndroidRemoteViews(it) }
}

@Composable
private fun Ring(ring: RenderedRing) {
    when (ring.labelPlacement) {
        // The label sits on top of the bitmap; the bitmap itself never contains text.
        LabelPlacement.INSIDE -> Box(contentAlignment = Alignment.Center) {
            RingImage(ring)
            Label(ring.label)
        }

        LabelPlacement.UNDER -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RingImage(ring)
            Label(ring.label)
        }

        LabelPlacement.NONE -> RingImage(ring)
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
    const val UNSUPPORTED = "This widget type arrives in a later milestone"
}
