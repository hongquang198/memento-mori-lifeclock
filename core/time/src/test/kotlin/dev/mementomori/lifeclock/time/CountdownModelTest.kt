package dev.mementomori.lifeclock.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

private val START: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val END: Instant = START.plus(Duration.ofDays(10))

/**
 * T1.1 — the specification of the DAYS slice.
 *
 * Written before the implementation (T1.2). Every test states one rule from design.md §2.2, so
 * this file doubles as the readable contract: if you disagree with a test, the design is wrong,
 * not the test.
 */
class CountdownModelTest : FunSpec({

    fun sliceAt(
        now: Instant,
        category: TimeCategory = TimeCategory.DAYS,
        start: Instant = START,
        end: Instant = END,
    ): TimeSlice? =
        DefaultCountdownModel()
            .slices(CountdownTarget(start, end), now)
            .singleOrNull { it.category == category }

    test("DAYS at the start: full total, full ring") {
        val slice = sliceAt(START)!!
        slice.total shouldBe Duration.ofDays(10)
        slice.remaining shouldBe Duration.ofDays(10)
        slice.fractionRemaining shouldBe 1.0
    }

    test("DAYS halfway: half the days, half-full ring") {
        val slice = sliceAt(START.plus(Duration.ofDays(5)))!!
        slice.remaining shouldBe Duration.ofDays(5)
        slice.fractionRemaining shouldBe 0.5
    }

    test("DAYS at the deadline: zero left, empty ring") {
        val slice = sliceAt(END)!!
        slice.remaining shouldBe Duration.ZERO
        slice.fractionRemaining shouldBe 0.0
    }

    test("DAYS past the deadline: clamps to zero instead of going negative") {
        val slice = sliceAt(END.plus(Duration.ofDays(3)))!!
        slice.remaining shouldBe Duration.ZERO
        slice.fractionRemaining shouldBe 0.0
    }

    test("DAYS before the countdown starts: ring clamps to full, never above it") {
        val slice = sliceAt(START.minus(Duration.ofDays(2)))!!
        slice.remaining shouldBe Duration.ofDays(10)
        slice.fractionRemaining shouldBe 1.0
    }

    test("display rounds up, and stays consistent with the exact arc") {
        // 70 h = 2.9 days left. Rounding up gives "3 days"; 3/10 agrees with the arc's 29%.
        // Truncating would print "2 days" next to a 29%-full arc - two contradicting numbers.
        val slice = sliceAt(END.minus(Duration.ofHours(70)))!!
        slice.remaining shouldBe Duration.ofDays(3)
        (slice.fractionRemaining * 100).toInt() shouldBe 29
    }

    test("the final partial day reads 1, never 0") {
        // 4 hours left is still "1 day" for display purposes; 0 would look expired.
        val slice = sliceAt(END.minus(Duration.ofHours(4)))!!
        slice.remaining shouldBe Duration.ofDays(1)
    }

    test("WEEKS: a 10-day countdown spans 2 weeks") {
        val slice = sliceAt(START, TimeCategory.WEEKS)!!
        slice.total shouldBe Duration.ofDays(14)
        slice.remaining shouldBe Duration.ofDays(14)
        slice.fractionRemaining shouldBe 1.0
    }

    test("WEEKS: a 15-day countdown spans 3 weeks") {
        val slice = sliceAt(
            now = START,
            category = TimeCategory.WEEKS,
            end = START.plus(Duration.ofDays(15)),
        )!!
        slice.total shouldBe Duration.ofDays(21)
    }

    test("WEEKS: 7.5 days left is still in the second week, so it reads 2") {
        // Rounding weeks must use the exact duration, not truncated days: 7.5 days is
        // ceil(7.5 / 7) = 2 weeks, while truncating to 7 days first would wrongly say 1.
        val slice = sliceAt(END.minus(Duration.ofDays(7)).minus(Duration.ofHours(12)), TimeCategory.WEEKS)!!
        slice.remaining shouldBe Duration.ofDays(14)
    }

    test("WEEKS: exactly 7 days left reads 1") {
        val slice = sliceAt(END.minus(Duration.ofDays(7)), TimeCategory.WEEKS)!!
        slice.remaining shouldBe Duration.ofDays(7)
    }

    test("WEEKS: past the deadline clamps to zero") {
        val slice = sliceAt(END.plus(Duration.ofDays(5)), TimeCategory.WEEKS)!!
        slice.remaining shouldBe Duration.ZERO
        slice.fractionRemaining shouldBe 0.0
    }

    test("DAYS and WEEKS report the same fraction: only the display unit differs") {
        val now = END.minus(Duration.ofHours(70))
        sliceAt(now, TimeCategory.DAYS)!!.fractionRemaining shouldBe
            sliceAt(now, TimeCategory.WEEKS)!!.fractionRemaining
    }

    test("a zero-length countdown has no meaningful slice") {
        DefaultCountdownModel()
            .slices(CountdownTarget(START, START), START)
            .shouldBeEmpty()
    }

    test("an inverted countdown (end before start) has no slice") {
        DefaultCountdownModel()
            .slices(CountdownTarget(END, START), START)
            .shouldBeEmpty()
    }
})
