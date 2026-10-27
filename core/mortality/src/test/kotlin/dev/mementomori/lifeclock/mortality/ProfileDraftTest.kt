package dev.mementomori.lifeclock.mortality

import dev.mementomori.lifeclock.model.Sex
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class ProfileDraftTest : StringSpec({

    val today = LocalDate.of(2026, 10, 27)
    val countries = setOf("USA", "VNM")

    fun draft() = ProfileDraft(
        profileId = "p1",
        name = "Someone",
        birthEpochDay = LocalDate.of(1990, 1, 1).toEpochDay(),
        sex = Sex.FEMALE,
        countryIso3 = "VNM",
    )

    "a plausible profile is valid" {
        draft().validate(today, countries).shouldBeEmpty()
    }

    "a blank name is refused" {
        draft().copy(name = " ").validate(today, countries)
            .shouldContain(ProfileProblem.NAME_BLANK)
    }

    "a country outside the table is refused, not defaulted" {
        draft().copy(countryIso3 = "ZZZ").validate(today, countries)
            .shouldContain(ProfileProblem.UNKNOWN_COUNTRY)
    }

    "the mortality rules still apply to a draft" {
        draft().copy(birthEpochDay = LocalDate.of(2030, 1, 1).toEpochDay())
            .validate(today, countries)
            .shouldContain(ProfileProblem.FUTURE_BIRTH)

        draft().copy(sleepHoursPerNight = 20.0).validate(today, countries)
            .shouldContain(ProfileProblem.SLEEP_OUT_OF_RANGE)
    }

    "the country list comes from the bundled table" {
        val table = CsvLifeTableSource.bundled()
        table.countries.size shouldBe 237
        table.countries shouldContain "VNM"
        table.countries shouldContain "USA"
    }
})
