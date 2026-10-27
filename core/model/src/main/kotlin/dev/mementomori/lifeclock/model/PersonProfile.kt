package dev.mementomori.lifeclock.model

import kotlinx.serialization.Serializable

/** Biological sex as the life tables report it. They publish two rows per country; there is no third. */
@Serializable
enum class Sex { FEMALE, MALE }

/**
 * Who the life clock is for.
 *
 * Stored as values, not as a `LocalDate`, so the record stays a pure serializable type and the
 * calendar rules (timezone, "how old are you today") stay in `:core:mortality` where they are tested.
 * [birthEpochDay] is `LocalDate.toEpochDay()` - a date, not an instant: a birthday does not have a
 * time of day, and storing one would make the answer depend on the timezone it was written in.
 */
@Serializable
data class PersonProfile(
    val profileId: String,
    val name: String,
    val birthEpochDay: Long,
    val sex: Sex,
    /** ISO 3166-1 alpha-3, matching the life table's `iso3` column. */
    val countryIso3: String,
    val sleepHoursPerNight: Double = 8.0,
    val screenHoursPerDay: Double = 4.0,
)
