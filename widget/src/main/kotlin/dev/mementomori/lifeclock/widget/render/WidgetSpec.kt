package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.mortality.LifeSnapshot
import dev.mementomori.lifeclock.time.Clock
import dev.mementomori.lifeclock.time.CountdownModel
import dev.mementomori.lifeclock.time.CountdownTarget
import java.time.Instant
import java.time.ZoneId

/** One arc to draw: the stored appearance plus the fraction it was computed to. */
data class RingSpec(val config: RingConfigRecord, val fraction: Double)

/** What a widget should show, decided with no Android and no IO. */
data class WidgetSpec(
    val rings: List<RingSpec>,
    /**
     * Drives the live `Chronometer`. Null for a life clock: it has no deadline to tick toward, and
     * a zero here would render a stopwatch counting *up* from the last draw.
     */
    val remainingMillis: Long?,
)

/**
 * Config + time -> what to draw.
 *
 * Separated from the loader so every branch worth testing (unbound, unrenderable, empty slices)
 * is reachable with plain JUnit instead of a widget host. The life snapshot is passed in rather
 * than looked up: this class must stay synchronous, and the profile lives behind a Flow.
 */
class WidgetSpecResolver(
    private val countdownModel: CountdownModel,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    fun resolve(config: WidgetConfigRecord, lifeSnapshot: LifeSnapshot? = null): WidgetSpec? =
        when (config.type) {
            WidgetType.COUNTDOWN -> resolveCountdown(config)
            WidgetType.LIFE_CLOCK -> resolveLifeClock(config, lifeSnapshot)
        }

    private fun resolveCountdown(config: WidgetConfigRecord): WidgetSpec? {
        val end = config.endEpochMillis ?: return null

        val now = clock.now()
        val target = CountdownTarget(
            start = Instant.ofEpochMilli(config.startEpochMillis),
            end = Instant.ofEpochMilli(end),
        )
        val slices = countdownModel.slices(target, now, zone).associateBy { it.category }
        val rings = config.rings.mapNotNull { ring ->
            slices[ring.category]?.let { RingSpec(ring, it.fractionRemaining) }
        }
        if (rings.isEmpty()) return null

        return WidgetSpec(
            rings = rings,
            // A missed deadline reads as zero rather than a negative Chronometer target.
            remainingMillis = (end - now.toEpochMilli()).coerceAtLeast(0),
        )
    }

    /**
     * A life clock has no deadline: its fraction is "how much of my life is left", already decided
     * by the engine. A missing snapshot means no profile is bound yet, which is a setup state, not
     * a zero - rendering 0% would read as "you are dead".
     */
    private fun resolveLifeClock(config: WidgetConfigRecord, snapshot: LifeSnapshot?): WidgetSpec? {
        if (snapshot == null) return null

        val rings = config.rings.mapNotNull { ring ->
            snapshot.stats[ring.category]?.let { RingSpec(ring, it.fractionRemaining) }
        }
        if (rings.isEmpty()) return null

        return WidgetSpec(rings = rings, remainingMillis = null)
    }
}
