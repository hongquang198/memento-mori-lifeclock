package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.widget.R

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.mortality.LifeSnapshot
import dev.mementomori.lifeclock.widget.ring.RingRenderer
import dev.mementomori.lifeclock.widget.ring.RingStyleResolver
import dev.mementomori.lifeclock.widget.ring.ThemePalette
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import dev.mementomori.lifeclock.widget.content.ChronometerViews
import dev.mementomori.lifeclock.widget.WidgetDependencies
import dev.mementomori.lifeclock.widget.WidgetLog

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
    suspend fun load(glanceId: String, cellWidthDp: Int? = null): WidgetState {
        val binding = dependencies.bindings.observeBinding(glanceId).first()
            ?: return WidgetState.Unconfigured
        val config = dependencies.configs.observe(binding.configId).first()
            ?: return WidgetState.Unconfigured

        // Life clocks need their profile before the resolver runs; a countdown never touches it.
        val snapshot = if (config.type == WidgetType.LIFE_CLOCK) lifeSnapshot(config) else null

        val spec = WidgetSpecResolver(
            countdownModel = dependencies.countdownModel,
            clock = dependencies.clock,
            zone = dependencies.zone,
        ).resolve(config, snapshot) ?: return WidgetState.Unsupported

        val density = context.resources.displayMetrics.density
        val styleResolver = RingStyleResolver(
            density = density,
            palette = ResourceThemePalette(context),
            fallbackArgb = context.getColor(R.color.widget_fallback),
        )
        val genericDescription = context.getString(R.string.ring_content_description)

        val rings = spec.rings.map { ring ->
            val style = styleResolver.resolve(ring.config.style)
            val diameterDp = fitDiameterDp(ring.config.style.diameterDp, cellWidthDp)
            val fitted = style.copy(diameterPx = (diameterDp * density).roundToInt())
            val bitmap = renderer.render(fitted, ring.fraction)

            // A life clock's point is the number, so the ring's own count replaces the static label.
            // A countdown keeps its authored label; its number ticks in the Chronometer below.
            val label = snapshot?.stats?.get(ring.config.category)
                ?.let(LifeStatText::of)
                ?: style.label

            // The one line that answers "what did it actually draw" without a debugger attached.
            WidgetLog.i(
                "render($glanceId): fraction=${ring.fraction} " +
                    "bitmap=${bitmap.width}x${bitmap.height} cell=${cellWidthDp ?: "?"}dp " +
                    "label=\"$label\"",
            )
            RenderedRing(
                bitmap = bitmap,
                sizeDp = diameterDp,
                label = label,
                labelPlacement = style.labelPlacement,
                contentDescription = label.ifBlank { genericDescription },
            )
        }

        return WidgetState.Ready(rings = rings, chronometer = chronometerFor(spec.remainingMillis))
    }

    /**
     * Null when the profile is missing, unparseable for the table, or the country is not covered.
     * The caller turns that into a setup message, never into a zeroed arc.
     */
    private suspend fun lifeSnapshot(config: WidgetConfigRecord): LifeSnapshot? {
        val profileId = config.profileRef ?: return null
        val profile = dependencies.profiles.observe(profileId).first() ?: return null
        return dependencies.lifeStats.compute(profile, dependencies.clock.now(), dependencies.zone)
    }

    private fun chronometerFor(remainingMillis: Long?): RemoteViews? {
        if (remainingMillis == null) return null
        if (!ChronometerViews.canEmbed(Build.VERSION.SDK_INT)) return null
        // elapsedRealtime resets on reboot, so the base is recomputed on every render and never
        // persisted - which is exactly why the widget must refresh after BOOT_COMPLETED.
        val base = ChronometerViews.baseFor(SystemClock.elapsedRealtime(), remainingMillis)
        return ChronometerViews.build(context, base)
    }
}
