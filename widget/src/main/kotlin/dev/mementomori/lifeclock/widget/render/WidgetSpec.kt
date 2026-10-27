package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.mortality.LifeSnapshot
import dev.mementomori.lifeclock.time.CountdownLabel
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
     * Drives the live `Chronometer`, and only inside the last day: above that the platform view
     * renders total hours ("1568:37:23" for a 65-day countdown). Null for a life clock, which has
     * no deadline to tick toward.
     */
    val chronometerMillis: Long?,
    /** The coarse readout in the ring's unit ("66 days") when a clock would be unreadable. */
    val readout: String? = null,
)

/** Below one day, total hours are still a time a person reads fluently. */
private const val MILLIS_PER_DAY = 86_400_000L

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

        // A missed deadline reads as zero rather than a negative Chronometer target.
        val remaining = (end - now.toEpochMilli()).coerceAtLeast(0)
        val primary = slices.getValue(rings.first().config.category)

        // A Chronometer renders total H:MM:SS, so it is honest only inside the last day - and not
        // at zero, where it would count up. Everything above that reads in the ring's own unit.
        val live = remaining.takeIf { it in 1 until MILLIS_PER_DAY }

        return WidgetSpec(
            rings = rings,
            chronometerMillis = live,
            readout = if (live == null) CountdownLabel.of(primary.remaining, primary.category) else null,
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

        return WidgetSpec(rings = rings, chronometerMillis = null)
    }
}

