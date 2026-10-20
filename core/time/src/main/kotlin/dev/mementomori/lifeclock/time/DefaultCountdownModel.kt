package dev.mementomori.lifeclock.time

import java.time.Duration
import java.time.Instant

/**
 * The unit-sliced categories: DAYS and WEEKS (MONTHS is calendar-based, see T1.5).
 *
 * Both numbers in a [TimeSlice] are computed differently on purpose (design.md §2.2):
 *
 *  - [TimeSlice.remaining] is rounded **up** to the category's unit ([ceilTo]), so the text ticks
 *    once a day (or week) instead of on every render, and the final partial unit never reads zero.
 *  - [TimeSlice.fractionRemaining] comes from the **exact** durations and is identical for every
 *    category, because it describes elapsed time, not how that time is displayed.
 *
 * Both are clamped, which is what keeps a widget that was asleep for three days from rendering a
 * negative countdown.
 */
class DefaultCountdownModel(
    /** Required only for [TimeCategory.NET_FREE_HOURS]; unused by the pure-time slices. */
    private val freeTimePolicy: FreeTimePolicy? = null,
) : CountdownModel {

    override fun slices(target: CountdownTarget, now: Instant): List<TimeSlice> {
        val total = Duration.between(target.start, target.end)

        // A deadline that does not come after its own reference point has no meaningful
        // fraction: the ratio would be 0/0 (NaN) or negative. Returning an empty list keeps
        // that decision in one place instead of letting each caller special-case it.
        if (total <= Duration.ZERO) return emptyList()

        val remaining = Duration.between(now, target.end)
            // Clamp both directions: "before the start" must not exceed a full ring, and
            // "after the deadline" must not go below zero.
            .coerceIn(Duration.ZERO, total)

        val fraction = fractionOf(remaining, total)

        // One list, one fraction, two display units. Written as a literal list rather than a
        // loop over an enum because the units differ per category and MONTHS cannot join it.
        return listOf(
            TimeSlice(TimeCategory.DAYS, remaining.ceilTo(ONE_DAY), total.ceilTo(ONE_DAY), fraction),
            TimeSlice(TimeCategory.WEEKS, remaining.ceilTo(ONE_WEEK), total.ceilTo(ONE_WEEK), fraction),
        )
    }

    /**
     * Rounds **up** to the nearest multiple of [unit].
     *
     * Three reasons, and the last two decided it:
     *
     * 1. A countdown must never read "0 days" while time remains: with 4 hours left, "1 day" is
     *    the honest number for "still in the final day".
     * 2. It keeps the text and the arc consistent. 2.9 days left on a 10-day countdown shows
     *    "3 days", and 3/10 agrees with the arc's 29%. Truncating would print "2 days" beside a
     *    29%-full arc, i.e. two numbers that contradict each other.
     * 3. Rounding from the **exact** duration, not from truncated days, is what makes a partial
     *    week correct: 7.5 days left is 2 weeks, not 1.
     *
     * Integer arithmetic, deliberately: a `ceil(d / unit)` on doubles can round a value that is
     * exactly N units up to N+1 through floating-point representation.
     */
    private fun Duration.ceilTo(unit: Duration): Duration {
        val unitMillis = unit.toMillis()
        val millis = toMillis()
        val wholeUnits = millis / unitMillis
        val rounded = if (millis % unitMillis == 0L) wholeUnits else wholeUnits + 1
        return Duration.ofMillis(rounded * unitMillis)
    }

    /**
     * `remaining / total` in `[0, 1]`.
     *
     * Uses millis rather than nanos: [Duration.toNanos] overflows past roughly 292 years, and
     * this engine must survive a far-future deadline (T1.7).
     */
    private fun fractionOf(remaining: Duration, total: Duration): Double =
        (remaining.toMillis().toDouble() / total.toMillis().toDouble()).coerceIn(0.0, 1.0)

    private companion object {
        val ONE_DAY: Duration = Duration.ofDays(1)
        val ONE_WEEK: Duration = Duration.ofDays(7)
    }
}
