package dev.mementomori.lifeclock.widget

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import dev.mementomori.lifeclock.widget.ring.RingRenderer
import dev.mementomori.lifeclock.widget.ring.RingStyleResolver
import dev.mementomori.lifeclock.widget.ring.ThemePalette
import kotlinx.coroutines.flow.first

/**
 * Reads the bound config and turns it into a [WidgetState].
 *
 * All IO and drawing happens here, before composition: `provideContent` is a composition, not a
 * place to block on DataStore or allocate bitmaps.
 */
class WidgetStateLoader(
    private val dependencies: WidgetDependencies,
    private val renderer: RingRenderer,
    private val context: Context,
) {
    suspend fun load(glanceId: String): WidgetState {
        val binding = dependencies.bindings.observeBinding(glanceId).first()
            ?: return WidgetState.Unconfigured
        val config = dependencies.configs.observe(binding.configId).first()
            ?: return WidgetState.Unconfigured

        val spec = WidgetSpecResolver(
            countdownModel = dependencies.countdownModel,
            clock = dependencies.clock,
            zone = dependencies.zone,
        ).resolve(config) ?: return WidgetState.Unsupported

        val density = context.resources.displayMetrics.density
        val styleResolver = RingStyleResolver(
            density = density,
            palette = ResourceThemePalette(context),
            fallbackArgb = context.getColor(R.color.widget_fallback),
        )
        val genericDescription = context.getString(R.string.ring_content_description)

        val rings = spec.rings.map { ring ->
            val style = styleResolver.resolve(ring.config.style)
            RenderedRing(
                bitmap = renderer.render(style, ring.fraction),
                sizeDp = ring.config.style.diameterDp,
                label = style.label,
                labelPlacement = style.labelPlacement,
                contentDescription = style.label.ifBlank { genericDescription },
            )
        }

        return WidgetState.Ready(rings = rings, chronometer = chronometerFor(spec.remainingMillis))
    }

    private fun chronometerFor(remainingMillis: Long): RemoteViews? {
        if (!ChronometerViews.canEmbed(Build.VERSION.SDK_INT)) return null
        // elapsedRealtime resets on reboot, so the base is recomputed on every render and never
        // persisted - which is exactly why the widget must refresh after BOOT_COMPLETED.
        val base = ChronometerViews.baseFor(SystemClock.elapsedRealtime(), remainingMillis)
        return ChronometerViews.build(context, base)
    }
}
