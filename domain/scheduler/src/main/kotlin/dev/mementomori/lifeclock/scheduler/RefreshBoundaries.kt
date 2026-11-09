package dev.mementomori.lifeclock.scheduler

import dev.mementomori.lifeclock.model.RefreshIntent
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Answers "when can the ring next change?" for a [RefreshIntent].
 *
 * Boundaries are computed in the user's [zone], not in UTC: "daily" means the local midnight,
 * and a DST shift must not move it. Every function returns a strictly later instant than the
 * one it was given, so a scheduler that sleeps until it will always make progress.
 *
 * [mustDrawBy] is the floor under the intent: the instant a ring's count actually changes (see
 * [RefreshSchedule]). The intent is a ceiling on staleness, the content is a floor on freshness,
 * and the earlier wins - a DAILY countdown showing hours must not sleep until midnight.
 */
class RefreshBoundaries(private val zone: ZoneId) {

    fun nextBoundary(
        after: Instant,
        intent: RefreshIntent,
        mustDrawBy: Instant? = null,
    ): Instant {
        val scheduled = when (intent) {
            RefreshIntent.HOURLY -> after.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()

            // 00:00, 06:00, 12:00, 18:00 local time.
            RefreshIntent.SIX_HOURLY -> {
                val local = after.atZone(zone)
                val nextSlot = (local.hour / SLOT_HOURS + 1) * SLOT_HOURS
                val slot = if (nextSlot == HOURS_PER_DAY) {
                    local.toLocalDate().plusDays(1).atStartOfDay(zone)
                } else {
                    local.toLocalDate().atTime(nextSlot, 0).atZone(zone)
                }
                slot.toInstant()
            }

            RefreshIntent.DAILY -> after.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        }

        // Ignore a floor that is not in the future: the contract is "strictly later than `after`".
        return if (mustDrawBy != null && mustDrawBy.isAfter(after) && mustDrawBy.isBefore(scheduled)) {
            mustDrawBy
        } else {
            scheduled
        }
    }

    private companion object {
        const val SLOT_HOURS = 6
        const val HOURS_PER_DAY = 24
    }
}
