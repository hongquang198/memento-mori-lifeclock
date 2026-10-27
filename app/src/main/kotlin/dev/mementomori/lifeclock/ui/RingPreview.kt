package dev.mementomori.lifeclock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mementomori.lifeclock.model.LabelPlacement
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.mortality.LifeSnapshot
import dev.mementomori.lifeclock.widget.render.LayoutPlan
import dev.mementomori.lifeclock.widget.render.LifeStatText
import dev.mementomori.lifeclock.widget.render.RenderedRing
import dev.mementomori.lifeclock.widget.render.RingRasterizer
import dev.mementomori.lifeclock.time.CountdownLabel
import dev.mementomori.lifeclock.widget.render.WidgetSpec
import kotlin.math.roundToInt

/**
 * The editor's preview.
 *
 * It runs the **same** `RingRasterizer` and the same `WidgetSpec` the worker uses, so "the preview
 * matches the placed widget" is a property of the code path, not a promise in a doc.
 */
@Composable
fun RingPreview(
    config: WidgetConfigRecord,
    spec: WidgetSpec?,
    snapshot: LifeSnapshot?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rasterizer = remember(context) { RingRasterizer.create(context) }

    val rings = remember(spec, snapshot) {
        spec?.let { resolved ->
            rasterizer.rasterize(
                rings = resolved.rings,
                cellWidthDp = null,
                labelFor = { ring ->
                    snapshot?.stats?.get(ring.config.category)?.let(LifeStatText::of)
                        ?: ring.count?.let { CountdownLabel.of(it, ring.config.category) }
                },
            )
        } ?: emptyList()
    }
    val planned = remember(config.layout, rings.size) { LayoutPlan.plan(config.layout, rings.size) }

    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 180.dp).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (config.layout) {
                dev.mementomori.lifeclock.model.WidgetLayout.SINGLE,
                dev.mementomori.lifeclock.model.WidgetLayout.STACK,
                -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    planned.forEach { slot -> PreviewRing(rings[slot.index], slot.scale, true) }
                }

                dev.mementomori.lifeclock.model.WidgetLayout.GRID -> Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    planned.forEach { slot -> PreviewRing(rings[slot.index], slot.scale, true) }
                }

                dev.mementomori.lifeclock.model.WidgetLayout.CONCENTRIC -> Box(
                    contentAlignment = Alignment.Center,
                ) {
                    planned.forEach { slot -> PreviewRing(rings[slot.index], slot.scale, slot.index == 0) }
                }
            }

            // The widget's live Chronometer can only sit outside the bitmap; the preview stands in
            // for it with the same H:MM:SS the platform would render.
            spec?.chronometerMillis?.let(::clockText)?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            }

            if (rings.isEmpty()) {
                Text("Nothing to preview yet", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Preview-only stand-in for the platform Chronometer's `H:MM:SS`. */
private fun clockText(millis: Long): String {
    val seconds = millis / 1000
    return "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
}

@Composable
private fun PreviewRing(ring: RenderedRing, scale: Float, showLabel: Boolean) {
    val size = (ring.sizeDp * scale).roundToInt().dp
    val image: @Composable () -> Unit = {
        Image(
            bitmap = ring.bitmap.asImageBitmap(),
            contentDescription = ring.contentDescription,
            modifier = Modifier.size(size),
        )
    }
    val label: @Composable () -> Unit = {
        Text(ring.label, style = MaterialTheme.typography.bodySmall)
    }

    when {
        !showLabel || ring.labelPlacement == LabelPlacement.NONE -> Box { image() }
        ring.labelPlacement == LabelPlacement.INSIDE -> Box(contentAlignment = Alignment.Center) {
            image()
            label()
        }
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            image()
            label()
        }
    }
}


