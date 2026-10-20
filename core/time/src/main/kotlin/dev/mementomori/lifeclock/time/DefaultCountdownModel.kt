package dev.mementomori.lifeclock.time

import java.time.Duration
import java.time.Instant

/**
 * T1.2 — the DAYS slice.
 *
 * The two numbers in a [TimeSlice] are computed differently on purpose (design.md §2.2):
 *
 *  - [TimeSlice.remaining] is rounded **up** to whole days ([ceilToDays]), so the text ticks
 *    once a day instead of on every render, and the final day never reads zero.
 *  - [TimeSlice.fractionRemaining] comes from the **exact** durations, so the ring moves
 *    smoothly and does not look frozen between ticks.
 *
 * Both are clamped, which is what keeps a widget that was asleep for three days from rendering
 * a negative countdown.
 */
class DefaultCountdownModel(
    /** Required only for [TimeCategory.NET_FREE_HOURS]; unused by the pure-time slices. */
    private val freeTimePolicy: FreeTimePolicy? = null,
) : CountdownModel {

    override fun slices(target: CountdownTarget, now: Instant): List<TimeSlice> =
        listOfNotNull(daysSlice(target, now))

    private fun daysSlice(target: CountdownTarget, now: Instant): TimeSlice? {
        val totalExact = Duration.between(target.start, target.end)

        // A deadline that does not come after its own reference point has no meaningful
        // fraction: the ratio would be 0/0 (NaN) or negative. Returning null keeps that
        // decision in one place instead of letting each caller special-case it.
        if (totalExact <= Duration.ZERO) return null

        val remainingExact = Duration.between(now, target.end)
            // Clamp both directions: "before the start" must not exceed a full ring, and
            // "after the deadline" must not go below zero.
            .coerceIn(Duration.ZERO, totalExact)

        return TimeSlice(
            category = TimeCategory.DAYS,
            remaining = remainingExact.ceilToDays(),
            total = totalExact.ceilToDays(),
            fractionRemaining = fractionOf(remainingExact, totalExact),
        )
    }

    /**
     * Rounds **up** to whole days.
     *
     * Two reasons, and the second is the one that decided it:
     *
     * 1. A countdown must never read "0 days" while time remains: with 4 hours left, "1 day" is
     *    the honest number for "still in the final day".
     * 2. It keeps the text and the arc consistent. 2.9 days left on a 10-day countdown shows
     *    "3 days", and 3/10 agrees with the arc's 29%. Truncating would print "2 days" beside a
     *    29%-full arc, i.e. two numbers that contradict each other.
     */
    private fun Duration.ceilToDays(): Duration {
        val truncated = Duration.ofDays(toDays())
        return if (truncated == this) truncated else Duration.ofDays(toDays() + 1)
    }

    /**
     * `remaining / total` in `[0, 1]`.
     *
     * Uses millis rather than nanos: [Duration.toNanos] overflows past roughly 292 years, and
     * this engine must survive a far-future deadline (T1.7).
     */
    private fun fractionOf(remaining: Duration, total: Duration): Double =
        (remaining.toMillis().toDouble() / total.toMillis().toDouble()).coerceIn(0.0, 1.0)
}
