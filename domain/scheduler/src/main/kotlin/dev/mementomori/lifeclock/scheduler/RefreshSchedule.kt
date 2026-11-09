package dev.mementomori.lifeclock.scheduler

import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * When the picture on screen next becomes untrue.
 *
 * [dev.mementomori.lifeclock.model.RefreshIntent] is a *ceiling* on staleness: what the user is
 * willing to pay for. This is the *floor*: the instant a ring's own count changes. A countdown
 * showing hours cannot be refreshed daily and still be right, so the earlier of the two has to win.
 *
 * Measured on a Pixel 8a (2026-10-28): a 24-hour countdown with an hours ring was configured
 * DAILY, so the only Doze-proof wakeup was the midnight boundary. The widget froze at `23 hours`
 * from 00:04 until the deferred 15-minute job finally ran after the phone woke, 10.5 hours later.
 */
object RefreshSchedule {

    /** The earliest instant at which any ring of [config] would display a different count. */
    fun contentChange(config: WidgetConfigRecord, now: Instant, zone: ZoneId): Instant? =
        when (config.type) {
            // A life clock's counts move at the calendar day: the engine compares dates.
            WidgetType.LIFE_CLOCK -> nextMidnight(now, zone)

            WidgetType.COUNTDOWN -> {
                val end = config.endEpochMillis?.let(Instant::ofEpochMilli) ?: return null
                config.rings
                    .mapNotNull { nextCountChange(it.category, end, now, zone) }
                    .minOrNull()
            }
        }

    /**
     * The instant `ceil(remaining / unit)` decrements.
     *
     * With `count = ceil(R / u)`, the count is `count` while `R` lies in `((count-1)u, count*u]`,
     * so it drops at `deadline - (count-1)u` - which is exactly the deadline when one unit is left.
     */
    private fun nextCountChange(
        category: TimeCategory,
        end: Instant,
        now: Instant,
        zone: ZoneId,
    ): Instant? {
        val unit = when (category) {
            TimeCategory.HOURS, TimeCategory.NET_FREE_HOURS -> Duration.ofHours(1)
            TimeCategory.DAYS -> Duration.ofDays(1)
            TimeCategory.WEEKS -> Duration.ofDays(7)
            // Calendar months: the engine compares dates, so the count moves at local midnight.
            TimeCategory.MONTHS -> return nextMidnight(now, zone)
        }

        val remaining = Duration.between(now, end)
        if (remaining <= Duration.ZERO) return null

        // Integer ceil: a double quotient would have to be trusted to be exact at the boundary.
        val unitMillis = unit.toMillis()
        val count = (remaining.toMillis() + unitMillis - 1) / unitMillis
        if (count <= 0) return null

        return end.minus(unit.multipliedBy(count - 1)).takeIf { it.isAfter(now) }
    }

    private fun nextMidnight(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
}
