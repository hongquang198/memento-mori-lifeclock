package dev.mementomori.lifeclock.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * T1.2–T1.8 — the four slice categories.
 *
 * Every category shares three rules:
 *
 *  - **Clamp first.** A `now` before `start` is treated as `start`; a `now` after `end` is treated
 *    as `end`. A widget that was asleep for three days must not render a negative countdown.
 *  - **Display counts round up.** `remaining` is a `Long` in the category's own unit (10 days,
 *    2 weeks, 3 months, 120 free hours). Rounding up means the final day reads "1 day", not "0",
 *    and keeps the text consistent with the exact arc: 2.9 days left is "3 days" beside a 29% arc.
 *  - **The fraction is exact and shared.** `fractionRemaining` is computed once from the exact
 *    instants, so the four arcs of a `CONCENTRIC` widget can never disagree about elapsed time.
 *
 * Where the categories differ is only in *what a unit is*:
 *
 *  - [TimeCategory.DAYS] / [TimeCategory.WEEKS] are **elapsed** units (24 h, 7 × 24 h), so the
 *    result is identical in every time zone.
 *  - [TimeCategory.MONTHS] is a **calendar** count via [ChronoUnit.MONTHS] in the given zone,
 *    because a month has no fixed length and "Feb 28" only means something relative to a zone.
 *  - [TimeCategory.NET_FREE_HOURS] subtracts sleep and maintenance, so it is a budget rather
 *    than a duration. Without a [FreeTimePolicy] the category is absent from the result.
 */
class DefaultCountdownModel(
    /** Required only for [TimeCategory.NET_FREE_HOURS]; the other categories ignore it. */
    private val freeTimePolicy: FreeTimePolicy? = null,
) : CountdownModel {

    override fun slices(target: CountdownTarget, now: Instant, zone: ZoneId): List<TimeSlice> {
        val totalSpan = Duration.between(target.start, target.end)

        // A deadline that does not come after its own reference point has no meaningful fraction:
        // the ratio would be 0/0 (NaN) or negative. One check here instead of one per category.
        if (totalSpan <= Duration.ZERO) return emptyList()

        val remainingSpan = Duration.between(now, target.end)
            // Clamp both directions: "before the start" must not exceed a full arc, and
            // "after the deadline" must not go below zero.
            .coerceIn(Duration.ZERO, totalSpan)

        val fraction = fractionOf(remainingSpan, totalSpan)

        // The clamped "now". Deriving it from the clamped span means the calendar categories are
        // clamped by the same decision as the duration ones, rather than repeating the clamp.
        val effectiveNow = target.end.minus(remainingSpan)

        return buildList {
            add(unitSlice(TimeCategory.DAYS, ONE_DAY, remainingSpan, totalSpan, fraction))
            add(unitSlice(TimeCategory.WEEKS, ONE_WEEK, remainingSpan, totalSpan, fraction))
            add(
                TimeSlice(
                    category = TimeCategory.MONTHS,
                    remaining = ceilMonthsBetween(effectiveNow, target.end, zone),
                    total = ceilMonthsBetween(target.start, target.end, zone),
                    fractionRemaining = fraction,
                ),
            )

            if (freeTimePolicy != null) {
                add(
                    TimeSlice(
                        category = TimeCategory.NET_FREE_HOURS,
                        remaining = freeHoursCeil(remainingSpan, freeTimePolicy),
                        total = freeHoursCeil(totalSpan, freeTimePolicy),
                        fractionRemaining = fraction,
                    ),
                )
            }
        }
    }

    /** A category measured in fixed-length units (a day, a week). */
    private fun unitSlice(
        category: TimeCategory,
        unit: Duration,
        remainingSpan: Duration,
        totalSpan: Duration,
        fraction: Double,
    ): TimeSlice = TimeSlice(
        category = category,
        remaining = ceilTo(remainingSpan, unit),
        total = ceilTo(totalSpan, unit),
        fractionRemaining = fraction,
    )

    /**
     * Rounds **up** to the nearest multiple of [unit].
     *
     * Integer arithmetic, deliberately: `ceil(d / unit)` on doubles can turn a value that is
     * exactly N units into N+1 through floating-point representation.
     */
    private fun ceilTo(span: Duration, unit: Duration): Long {
        val unitMillis = unit.toMillis()
        val millis = span.toMillis()
        val whole = millis / unitMillis
        return if (millis % unitMillis == 0L) whole else whole + 1
    }

    /**
     * Whole calendar months from [from] to [to], rounded up, counted in [zone].
     *
     * [ChronoUnit.MONTHS] truncates, and month-end makes that visibly wrong: from 31 Jan to
     * 28 Feb it reports 0 months, although the calendar has moved one month forward. Comparing
     * `fromDate.plusMonths(whole)` against `toDate` detects the remainder and rounds up.
     */
    private fun ceilMonthsBetween(from: Instant, to: Instant, zone: ZoneId): Long {
        if (!to.isAfter(from)) return 0

        val fromDate = from.atZone(zone).toLocalDate()
        val toDate = to.atZone(zone).toLocalDate()

        val whole = ChronoUnit.MONTHS.between(fromDate, toDate)
        val rounded = if (fromDate.plusMonths(whole).isBefore(toDate)) whole + 1 else whole

        // Any positive remainder is at least "1 month", exactly like a few hours is "1 day".
        return max(rounded, 1L)
    }

    /**
     * Free hours: `(elapsed − sleep) × (1 − maintenance)`.
     *
     * Works in doubles because the policy is a ratio, then rounds with a tolerance — see
     * [ceilHours] for why the tolerance is not optional.
     */
    private fun freeHoursCeil(span: Duration, policy: FreeTimePolicy): Long {
        val minutes = span.toMinutes().toDouble()
        val wakingHours = minutes / MINUTES_PER_HOUR -
            minutes / MINUTES_PER_DAY * policy.sleepHoursPerDay
        return ceilHours(wakingHours * (1.0 - policy.maintenanceRatio))
    }

    /**
     * Rounds up, ignoring floating-point noise just below an integer.
     *
     * Without the tolerance this is a real bug: 70 h with 8 h sleep/day and 25% maintenance is
     * exactly 35 free hours, but IEEE arithmetic produces `35.00000000000001`, and a naive
     * [ceil] reports 36. The tolerance is 1 ns of an hour — far below anything a human can see,
     * far above the error of a few multiplications.
     */
    private fun ceilHours(hours: Double): Long {
        if (hours <= 0.0) return 0
        val tolerance = max(ABS_HOURS_TOLERANCE, abs(hours) * RELATIVE_HOURS_TOLERANCE)
        return ceil(hours - tolerance).toLong()
    }

    /**
     * `remaining / total` in `[0, 1]`.
     *
     * Uses millis rather than nanos: [Duration.toNanos] overflows past roughly 292 years, and a
     * countdown to a far-future date is a legitimate target (T1.7).
     */
    private fun fractionOf(remaining: Duration, total: Duration): Double {
        val totalMillis = total.toMillis()
        if (totalMillis <= 0L) return 0.0
        return (remaining.toMillis().toDouble() / totalMillis.toDouble()).coerceIn(0.0, 1.0)
    }

    private companion object {
        val ONE_DAY: Duration = Duration.ofDays(1)
        val ONE_WEEK: Duration = Duration.ofDays(7)

        const val MINUTES_PER_HOUR = 60.0
        const val MINUTES_PER_DAY = 1440.0

        /** One nanosecond expressed in hours. */
        const val ABS_HOURS_TOLERANCE = 1e-9
        const val RELATIVE_HOURS_TOLERANCE = 1e-12
    }
}
