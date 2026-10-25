package dev.mementomori.lifeclock.widget.render

import android.graphics.Bitmap
import android.widget.RemoteViews
import dev.mementomori.lifeclock.model.LabelPlacement

/** Everything the composition needs, resolved before composing: Glance cannot do IO or drawing. */
sealed interface WidgetState {

    /** No config is bound to this placed widget, or the bound config no longer exists. */
    data object Unconfigured : WidgetState

    /** A config exists but this build cannot draw it yet (LIFE_CLOCK needs M3). */
    data object Unsupported : WidgetState

    data class Ready(
        val rings: List<RenderedRing>,
        /** Null where a nested RemoteViews is unavailable, so the widget shows ring + label only. */
        val chronometer: RemoteViews?,
    ) : WidgetState
}

/** One already-rasterized ring plus the text Glance draws beside it. */
data class RenderedRing(
    val bitmap: Bitmap,
    val sizeDp: Int,
    val label: String,
    val labelPlacement: LabelPlacement,
    /** Falls back to a generic description when the config has no label. */
    val contentDescription: String,
)
