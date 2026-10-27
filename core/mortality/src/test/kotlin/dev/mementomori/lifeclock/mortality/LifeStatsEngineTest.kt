package dev.mementomori.lifeclock.mortality

import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.Sex
import dev.mementomori.lifeclock.model.TimeCategory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class LifeStatsEngineTest : StringSpec({

    val engine = LifeStatsEngine(CsvLifeTableSource.bundled())

    fun profile(born: LocalDate = LocalDate.of(1986, 1, 1), country: String = "USA") =
        PersonProfile(
            profileId = "p1",
            name = "Test",
            birthEpochDay = born.toEpochDay(),
            sex = Sex.MALE,
            countryIso3 = country,
        )

    val now = Instant.parse("2026-10-26T12:00:00Z")

    "uses the table value for the age reached today" {
        val snapshot = engine.compute(profile(), now, ZoneId.of("UTC"))!!
        snapshot.ageYears shouldBe 40
        // 39.3343 years x 365.2425, rounded up.
        snapshot.stats.getValue(TimeCategory.DAYS).remaining shouldBe 14367L
    }

    "converts the same remainder into weeks and months consistently" {
        val stats = engine.compute(profile(), now, ZoneId.of("UTC"))!!.stats
        stats.getValue(TimeCategory.WEEKS).remaining shouldBe 2053L
        stats.getValue(TimeCategory.MONTHS).remaining shouldBe 473L
    }

    "every arc reports the same fraction, so the rings cannot disagree" {
        val stats = engine.compute(profile(), now, ZoneId.of("UTC"))!!.stats
        val fractions = stats.values.map { it.fractionRemaining }.distinct()
        fractions.size shouldBe 1

        // daysLeft / (daysLeft + days lived), with the lived count from the calendar.
        (abs(fractions.single() - 0.49075234661105827) < 1e-12) shouldBe true
    }

    "derived numbers are labelled by where they come from" {
        val derived = engine.compute(profile(), now, ZoneId.of("UTC"))!!.derived.associateBy { it.id }

        derived.getValue("heartbeats").apply {
            value shouldBe 1489524741L
            // 72 bpm is our choice inside the AHA's 60-100 range, so it is a MODEL.
            kind shouldBe StatKind.MODEL
            citation.source shouldBe "American Heart Association"
        }
        derived.getValue("seasons").apply {
            value shouldBe 158L
            // e_x x 4: arithmetic on the table, no new assumption.
            kind shouldBe StatKind.SOURCE
        }
    }

    "a future birth date is unanswered, not zero" {
        engine.compute(profile(born = LocalDate.of(2030, 1, 1)), now, ZoneId.of("UTC")) shouldBe null
    }

    "an unknown country is unanswered, not a fallback country" {
        engine.compute(profile(country = "ZZZ"), now, ZoneId.of("UTC")) shouldBe null
    }

    "age is counted in the user's zone, not the machine's" {
        val born = LocalDate.of(2000, 1, 1)
        val instant = Instant.parse("2025-12-31T20:00:00Z")

        engine.compute(profile(born = born), instant, ZoneId.of("UTC"))!!.ageYears shouldBe 25
        // 20:00Z is already 03:00 on the 1st in Ho Chi Minh City: the birthday has happened there.
        engine.compute(profile(born = born), instant, ZoneId.of("Asia/Ho_Chi_Minh"))!!.ageYears shouldBe 26
    }
})
