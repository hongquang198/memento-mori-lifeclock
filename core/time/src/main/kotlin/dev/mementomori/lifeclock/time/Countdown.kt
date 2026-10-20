package dev.mementomori.lifeclock.time

import java.time.Instant
import java.time.ZoneId

/**
 * The four ways a countdown can be expressed. Each one is a separate arc.
 *
 * They are not four views of the same number: `DAYS` and `WEEKS` are elapsed-time units,
 * `MONTHS` is a calendar count (its length depends on the zone), and `NET_FREE_HOURS` is a
 * *budget* with sleep and maintenance removed.
 */
enum class TimeCategory { DAYS, WEEKS, MONTHS, NET_FREE_HOURS }

/**
 * One arc's data: how much is left and how full to draw it.
 *
 * [remaining] and [total] are **counts in [category]'s own unit**, not durations. A month has no
 * fixed length, so a `Duration` field would force a fake 30-day month on every caller; the
 * renderer only ever needs "3" and "DAYS", never date arithmetic.
 *
 * [remaining] is rounded **up**, so a countdown never reads "0 days" while time remains.
 * [fractionRemaining] is the **exact** ratio, clamped to `[0, 1]`, because the arc is redrawn far
 * less often than the text ticks and should not look frozen between ticks. It is the same for
 * every category: it describes elapsed time, not how that time is displayed.
 */
data class TimeSlice(
    val category: TimeCategory,
    val remaining: Long,
    val total: Long,
    val fractionRemaining: Double,
)

/**
 * The interval a countdown counts down across.
 *
 * [end] is the deadline. [start] is the reference used for [TimeSlice.total]: it is usually the
 * moment the countdown was created, which is what makes "how full is the arc" meaningful.
 */
data class CountdownTarget(val start: Instant, val end: Instant)

/**
 * The budget a countdown is expressed in once sleep and maintenance are removed.
 *
 * Lives here rather than in `:core:mortality` so the dependency only ever points *down*: the
 * pure time module does the arithmetic, and the caller supplies the ATUS-derived ratio. A
 * `:core:time` that imported mortality constants could not be reasoned about in isolation.
 */
data class FreeTimePolicy(
    /** Hours per day not available to the target, e.g. sleep. */
    val sleepHoursPerDay: Double,
    /** Fraction of the remaining waking hours consumed by unavoidable maintenance (0..1). */
    val maintenanceRatio: Double,
) {
    init {
        // Fail at the boundary rather than rendering a negative or infinite countdown. The
        // config layer validates persisted values; this is the last line of defence.
        require(sleepHoursPerDay in 0.0..HOURS_PER_DAY) {
            "sleepHoursPerDay must be in 0..24, was $sleepHoursPerDay"
        }
        require(maintenanceRatio in 0.0..1.0) {
            "maintenanceRatio must be in 0..1, was $maintenanceRatio"
        }
    }

    private companion object {
        const val HOURS_PER_DAY = 24.0
    }
}

/**
 * Turns a [CountdownTarget] into one [TimeSlice] per category.
 *
 * Pure and synchronous: no Android, no clock of its own, no I/O. That is what makes it
 * testable with plain JUnit and fast enough to recompute on every render.
 */
interface CountdownModel {
    /**
     * @param zone the zone in which [TimeCategory.MONTHS] is counted. Explicit rather than
     *   `ZoneId.systemDefault()`: the same instant can fall in different months in different
     *   zones, and a hidden default would make the result depend on global state.
     */
    fun slices(target: CountdownTarget, now: Instant, zone: ZoneId): List<TimeSlice>
}
