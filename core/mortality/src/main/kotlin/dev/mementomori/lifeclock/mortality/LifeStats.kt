package dev.mementomori.lifeclock.mortality

import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.TimeCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Whether a number came out of a source or out of a decision of ours.
 *
 * The distinction is the whole credibility argument: `SOURCE` means "re-derivable from the cited
 * table", `MODEL` means "we chose this constant, here is why". Mixing them silently is how a
 * calculator starts lying.
 */
enum class StatKind { SOURCE, MODEL }

/** One arc's worth of life left, shaped like the countdown engine's output so the renderer is shared. */
data class LifeStat(
    val category: TimeCategory,
    val remaining: Long,
    val total: Long,
    val fractionRemaining: Double,
    val citation: Citation,
    val kind: StatKind,
)

/** A number the widget may show as text but that is not an arc (heartbeats, seasons). */
data class DerivedStat(
    val id: String,
    val value: Long,
    val unit: String,
    val citation: Citation,
    val kind: StatKind,
)

/** Everything one draw needs, computed with no Android, no IO and no clock of its own. */
data class LifeSnapshot(
    val ageYears: Int,
    val lifeTable: Citation,
    val stats: Map<TimeCategory, LifeStat>,
    val derived: List<DerivedStat>,
)

/**
 * Profile + life table -> remaining life.
 *
 * The only inputs are the profile, the instant and the zone; the table is injected. That is what
 * makes the tests deterministic: no `Instant.now()` inside, no hidden locale.
 */
class LifeStatsEngine(private val tables: LifeTableSource) {

    /**
     * Null means "cannot answer honestly" (future birth, unknown country, age past the table).
     * Callers must show a setup state, never a zero: a zeroed arc reads as "you are dead".
     */
    fun compute(profile: PersonProfile, now: Instant, zone: ZoneId): LifeSnapshot? {
        val birth = LocalDate.ofEpochDay(profile.birthEpochDay)
        val today = now.atZone(zone).toLocalDate()
        if (birth.isAfter(today)) return null

        val age = ChronoUnit.YEARS.between(birth, today).toInt()
        if (age > MAX_AGE) return null

        val expected = tables.lifeExpectancy(profile.countryIso3, profile.sex, age) ?: return null

        // e_x is years, the product counts days. 365.2425 is the mean Gregorian year: using 365
        // would drift by a day every four years, which is visible on a 28,000-day number.
        val daysLeft = expected * DAYS_PER_YEAR
        val livedDays = ChronoUnit.DAYS.between(birth, today).toDouble()
        val totalDays = daysLeft + livedDays

        // One ratio for every arc: the ring answers "how much of my life is left", so weeks and
        // months must agree with days. Rounding each arc separately would show 3 inconsistent rings.
        val fraction = daysLeft / totalDays

        fun stat(category: TimeCategory, perDay: Double) = LifeStat(
            category = category,
            // Round up: "1 day left" must not render as "0 days left" while a day remains.
            remaining = ceil(daysLeft * perDay).toLong(),
            total = ceil(totalDays * perDay).toLong(),
            fractionRemaining = fraction,
            citation = tables.citation,
            kind = StatKind.SOURCE,
        )

        val stats = buildMap {
            put(TimeCategory.DAYS, stat(TimeCategory.DAYS, 1.0))
            put(TimeCategory.WEEKS, stat(TimeCategory.WEEKS, 1.0 / DAYS_PER_WEEK))
            put(TimeCategory.MONTHS, stat(TimeCategory.MONTHS, 1.0 / DAYS_PER_MONTH))
            // NET_FREE_HOURS needs the ATUS time-use split; it lands with that source, not before.
        }

        val derived = listOf(
            DerivedStat(
                id = "heartbeats",
                value = ceil(daysLeft * MINUTES_PER_DAY * HEART_RATE_BPM).toLong(),
                unit = "beats",
                citation = HEART_RATE_CITATION,
                kind = StatKind.MODEL,
            ),
            DerivedStat(
                id = "seasons",
                value = ceil(daysLeft / DAYS_PER_SEASON).toLong(),
                unit = "seasons",
                citation = tables.citation,
                kind = StatKind.SOURCE,
            ),
        )

        return LifeSnapshot(ageYears = age, lifeTable = tables.citation, stats = stats, derived = derived)
    }

    companion object {
        /** Mean Gregorian year, days. */
        const val DAYS_PER_YEAR = 365.2425
        const val DAYS_PER_WEEK = 7.0

        /** 365.2425 / 12. */
        const val DAYS_PER_MONTH = 30.436875
        const val DAYS_PER_SEASON = DAYS_PER_YEAR / 4
        const val MINUTES_PER_DAY = 24 * 60.0

        /**
         * Resting heart rate, beats per minute. The AHA publishes a *range* (60-100), not a point,
         * so 72 is our choice - flagged MODEL everywhere it surfaces. A user-supplied pulse would
         * replace it; inventing precision we do not have would not.
         */
        const val HEART_RATE_BPM = 72.0

        /** The table has no age beyond 100, and no verified human beyond 122. */
        const val MAX_AGE = 120

        val HEART_RATE_CITATION = Citation(
            source = "American Heart Association",
            vintage = "Target Heart Rates Chart, accessed 2026-10-26",
            table = "Resting heart rate, normal adult range 60-100 bpm",
            url = "https://www.heart.org/en/healthy-living/fitness/fitness-basics/target-heart-rates",
            retrieved = "2026-10-26",
            license = "AHA site terms",
        )
    }
}
