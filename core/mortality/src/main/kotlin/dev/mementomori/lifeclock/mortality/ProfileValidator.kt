package dev.mementomori.lifeclock.mortality

import dev.mementomori.lifeclock.model.PersonProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Everything that can be wrong with a profile, named so the editor can point at the field. */
enum class ProfileProblem {
    FUTURE_BIRTH,
    IMPLAUSIBLE_AGE,
    SLEEP_OUT_OF_RANGE,
    SCREEN_OUT_OF_RANGE,
    OCCUPIED_HOURS_OVER_DAY,
}

/**
 * Rejects profiles the engine cannot honestly answer for.
 *
 * Kept separate from the engine so the editor can validate while typing, without a life table or a
 * clock, and so the rules are testable one by one.
 */
object ProfileValidator {

    const val MAX_AGE = 120
    const val MAX_SLEEP_HOURS = 16.0
    const val MAX_SCREEN_HOURS = 24.0

    fun validate(profile: PersonProfile, today: LocalDate): List<ProfileProblem> = buildList {
        val birth = LocalDate.ofEpochDay(profile.birthEpochDay)
        when {
            birth.isAfter(today) -> add(ProfileProblem.FUTURE_BIRTH)
            ChronoUnit.YEARS.between(birth, today) > MAX_AGE -> add(ProfileProblem.IMPLAUSIBLE_AGE)
        }

        if (profile.sleepHoursPerNight < 0 || profile.sleepHoursPerNight > MAX_SLEEP_HOURS) {
            add(ProfileProblem.SLEEP_OUT_OF_RANGE)
        }
        if (profile.screenHoursPerDay < 0 || profile.screenHoursPerDay > MAX_SCREEN_HOURS) {
            add(ProfileProblem.SCREEN_OUT_OF_RANGE)
        }
        // Sleep and screen time cannot both be true if together they exceed the day.
        if (profile.sleepHoursPerNight + profile.screenHoursPerDay > 24) {
            add(ProfileProblem.OCCUPIED_HOURS_OVER_DAY)
        }
    }

    fun isValid(profile: PersonProfile, today: LocalDate): Boolean = validate(profile, today).isEmpty()
}
