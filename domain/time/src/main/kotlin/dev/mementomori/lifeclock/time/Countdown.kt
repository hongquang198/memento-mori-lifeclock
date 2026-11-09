package dev.mementomori.lifeclock.time

import dev.mementomori.lifeclock.model.TimeCategory
import java.time.Instant
import java.time.ZoneId

/**
 * One arc's data.
 *
 * [remaining]/[total] are counts in [category]'s own unit (a month has no fixed length, so a
 * Duration would be a lie). [remaining] rounds up; [fractionRemaining] is the exact ratio and is
 * the same for every category.
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
