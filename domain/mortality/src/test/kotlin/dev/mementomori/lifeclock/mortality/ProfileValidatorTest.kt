package dev.mementomori.lifeclock.mortality

import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.Sex
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldBeEmpty
import java.time.LocalDate

class ProfileValidatorTest : StringSpec({

    val today = LocalDate.of(2026, 10, 26)

    fun profile(
        born: LocalDate = LocalDate.of(1990, 1, 1),
        sleep: Double = 8.0,
        screen: Double = 4.0,
    ) = PersonProfile(
        profileId = "p1",
        name = "Test",
        birthEpochDay = born.toEpochDay(),
        sex = Sex.FEMALE,
        countryIso3 = "USA",
        sleepHoursPerNight = sleep,
        screenHoursPerDay = screen,
    )

    "accepts a plausible profile" {
        ProfileValidator.validate(profile(), today).shouldBeEmpty()
    }

    "rejects a birth date in the future" {
        ProfileValidator.validate(profile(born = LocalDate.of(2027, 1, 1)), today)
            .shouldContain(ProfileProblem.FUTURE_BIRTH)
    }

    "rejects an age no human reaches" {
        ProfileValidator.validate(profile(born = LocalDate.of(1900, 1, 1)), today)
            .shouldContain(ProfileProblem.IMPLAUSIBLE_AGE)
    }

    "rejects sleep outside a possible day" {
        ProfileValidator.validate(profile(sleep = -1.0), today)
            .shouldContain(ProfileProblem.SLEEP_OUT_OF_RANGE)
        ProfileValidator.validate(profile(sleep = 20.0), today)
            .shouldContain(ProfileProblem.SLEEP_OUT_OF_RANGE)
    }

    "rejects a screen time longer than the day" {
        ProfileValidator.validate(profile(screen = 25.0), today)
            .shouldContain(ProfileProblem.SCREEN_OUT_OF_RANGE)
    }

    "rejects sleep and screen together exceeding the day" {
        ProfileValidator.validate(profile(sleep = 12.0, screen = 13.0), today)
            .shouldContain(ProfileProblem.OCCUPIED_HOURS_OVER_DAY)
    }
})
