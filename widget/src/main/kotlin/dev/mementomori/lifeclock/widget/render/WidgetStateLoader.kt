package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.widget.R

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.mortality.LifeSnapshot
import dev.mementomori.lifeclock.widget.WidgetDependencies
import dev.mementomori.lifeclock.widget.WidgetLog
import dev.mementomori.lifeclock.widget.content.ChronometerViews
import kotlinx.coroutines.flow.first

/**
 * Reads the bound config and turns it into a [WidgetState].
 *
 * All IO and drawing happens here, before composition: `provideContent` is a composition, not a
 * place to block on DataStore or allocate bitmaps.
 */
class WidgetStateLoader(
    private val dependencies: WidgetDependencies,
    private val rasterizer: RingRasterizer,
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

        val rings = rasterizer.rasterize(
            rings = spec.rings,
            cellWidthDp = cellWidthDp,
            // A life clock's point is the number, so the ring's own count replaces the static label.
            // A countdown keeps its authored label; its number ticks in the Chronometer below.
            labelFor = { ring -> snapshot?.stats?.get(ring.category)?.let(LifeStatText::of) },
            onRendered = { line -> WidgetLog.i("render($glanceId): $line") },
        )

        return WidgetState.Ready(
            rings = rings,
            chronometer = chronometerFor(spec.remainingMillis),
            layout = config.layout,
        )
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
