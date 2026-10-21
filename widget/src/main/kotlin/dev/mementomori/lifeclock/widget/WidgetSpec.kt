package dev.mementomori.lifeclock.widget

import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.time.Clock
import dev.mementomori.lifeclock.time.CountdownModel
import dev.mementomori.lifeclock.time.CountdownTarget
import java.time.Instant
import java.time.ZoneId

/** One arc to draw: the stored appearance plus the fraction it was computed to. */
data class RingSpec(val config: RingConfigRecord, val fraction: Double)

/** What a widget should show, decided with no Android and no IO. */
data class WidgetSpec(val rings: List<RingSpec>, val remainingMillis: Long)

/**
 * Config + time -> what to draw.
 *
 * Separated from the loader so every branch worth testing (unbound, unrenderable, empty slices)
 * is reachable with plain JUnit instead of a widget host.
 */
class WidgetSpecResolver(
    private val countdownModel: CountdownModel,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    /** Null when this config cannot be rendered yet, e.g. a LIFE_CLOCK has no profile until M3. */
    fun resolve(config: WidgetConfigRecord): WidgetSpec? {
        if (config.type != WidgetType.COUNTDOWN) return null
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
}
