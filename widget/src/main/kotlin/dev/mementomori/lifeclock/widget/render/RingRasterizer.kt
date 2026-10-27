package dev.mementomori.lifeclock.widget.render

import android.content.Context
import android.graphics.Bitmap
import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.widget.R
import dev.mementomori.lifeclock.widget.ring.CanvasRingRenderer
import dev.mementomori.lifeclock.widget.ring.RingRenderer
import dev.mementomori.lifeclock.widget.ring.RingStyleResolver
import kotlin.math.roundToInt

/**
 * Ring specs -> bitmaps. The one place that turns a style record into pixels.
 *
 * Extracted so the placed widget and the editor's preview share it: if the preview were a second
 * implementation, "what you see is what you place" would be a hope instead of a fact.
 */
class RingRasterizer(
    private val renderer: RingRenderer,
    private val styleResolver: RingStyleResolver,
    private val density: Float,
    private val genericDescription: String,
) {

    /**
     * @param cellWidthDp the launcher cell, when drawing into a widget. Null for a preview, which
     *   has no cell to fit into.
     * @param labelFor overrides the authored label (a life clock shows its own count).
     * @param onRendered receives one line per ring; the caller decides where it goes.
     */
    fun rasterize(
        rings: List<RingSpec>,
        cellWidthDp: Int? = null,
        labelFor: (RingConfigRecord) -> String? = { null },
        onRendered: (String) -> Unit = {},
    ): List<RenderedRing> = rings.map { ring ->
        val style = styleResolver.resolve(ring.config.style)
        val diameterDp = fitDiameterDp(ring.config.style.diameterDp, cellWidthDp)
        val fitted = style.copy(diameterPx = (diameterDp * density).roundToInt())
        val bitmap: Bitmap = renderer.render(fitted, ring.fraction)

        val label = labelFor(ring.config) ?: style.label
        onRendered(
            "fraction=${ring.fraction} bitmap=${bitmap.width}x${bitmap.height} " +
                "cell=${cellWidthDp ?: "?"}dp label=\"$label\"",
        )

        RenderedRing(
            bitmap = bitmap,
            sizeDp = diameterDp,
            label = label,
            labelPlacement = style.labelPlacement,
            contentDescription = label.ifBlank { genericDescription },
        )
    }

    companion object {
        fun create(context: Context, renderer: RingRenderer = CanvasRingRenderer()): RingRasterizer {
            val density = context.resources.displayMetrics.density
            return RingRasterizer(
                renderer = renderer,
                styleResolver = RingStyleResolver(
                    density = density,
                    palette = ResourceThemePalette(context),
                    fallbackArgb = context.getColor(R.color.widget_fallback),
                ),
                density = density,
                genericDescription = context.getString(R.string.ring_content_description),
            )
        }
    }
}
