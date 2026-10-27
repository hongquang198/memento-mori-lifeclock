package dev.mementomori.lifeclock.mortality

import dev.mementomori.lifeclock.model.Sex
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/**
 * Pins the bundled table against the values it was generated from.
 *
 * These are golden numbers, not "does it return something": if a regeneration changes a cell, this
 * fails and forces a deliberate decision instead of a silent shift in everyone's remaining life.
 */
class LifeTablesTest : StringSpec({

    val table = CsvLifeTableSource.bundled()

    "reads a known row exactly" {
        table.lifeExpectancy("USA", Sex.FEMALE, 40) shouldBe 43.2893
        table.lifeExpectancy("USA", Sex.MALE, 40) shouldBe 39.3343
    }

    "interpolates linearly inside a five-year age group" {
        val at40 = table.lifeExpectancy("USA", Sex.FEMALE, 40)!!
        val at45 = table.lifeExpectancy("USA", Sex.FEMALE, 45)!!
        val at42 = table.lifeExpectancy("USA", Sex.FEMALE, 42)!!

        // 42 is 2/5 of the way from 40 to 45. Truncating to 40 instead would overstate life by
        // years, which is the one error this product cannot afford.
        (abs((at42 - at40) / (at45 - at40) - 2.0 / 5.0) < 1e-12) shouldBe true
    }

    "an unknown country is null, never a default country" {
        table.lifeExpectancy("ZZZ", Sex.MALE, 40) shouldBe null
    }

    "an age past the last group is null, never the last row repeated" {
        table.lifeExpectancy("USA", Sex.MALE, 101) shouldBe null
        table.lifeExpectancy("USA", Sex.MALE, -1) shouldBe null
    }

    "remaining life falls monotonically with age" {
        val ages = listOf(0, 40, 60, 80)
        val values = ages.map { table.lifeExpectancy("USA", Sex.MALE, it)!! }
        values.zipWithNext().all { (younger, older) -> younger > older } shouldBe true
    }

    "the citation names a vintage, not just a URL" {
        table.citation.vintage shouldBe "2024 revision, period 2023, medium variant"
        table.citation.retrieved shouldBe "2026-10-26"
        table.citation.license shouldBe "CC BY 3.0 IGO"
    }
})
