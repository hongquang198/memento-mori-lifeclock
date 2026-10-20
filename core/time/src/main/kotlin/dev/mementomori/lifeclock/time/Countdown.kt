package dev.mementomori.lifeclock.time

import java.time.Duration
import java.time.Instant

/**
 * The four ways a countdown can be expressed. Each one is a separate "ring".
 *
 * They are not four views of the same number: `DAYS` is a wall-clock decomposition, while
 * `NET_FREE_HOURS` subtracts sleep and maintenance and is therefore a *budget*, not a duration.
 */
enum class TimeCategory { DAYS, WEEKS, MONTHS, NET_FREE_HOURS }

/**
 * One ring's data: how much is left and how full to draw it.
 *
 * [remaining] is the **display** value: quantized to the category (whole days, whole weeks) so
 * the text ticks once a day rather than once a millisecond.
 *
 * [fractionRemaining] is the **exact** ratio, clamped to `[0, 1]`, because the ring is redrawn
 * far less often than the text ticks and should not look frozen between ticks.
 */
data class TimeSlice(
    val category: TimeCategory,
    val remaining: Duration,
    val total: Duration,
    val fractionRemaining: Double,
)

/**
 * The interval a countdown counts down across, inclusive of both ends.
 *
 * [end] is the deadline. [start] is the reference used for [TimeSlice.total]: it is usually the
 * moment the countdown was created, which is what makes "how full is the ring" meaningful.
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
)

/**
 * Turns a [CountdownTarget] into one [TimeSlice] per category.
 *
 * Pure and synchronous: no Android, no clock of its own, no I/O. That is what makes it
 * testable with plain JUnit and fast enough to recompute on every render.
 */
interface CountdownModel {
    fun slices(target: CountdownTarget, now: Instant): List<TimeSlice>
}
