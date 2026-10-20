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

    fun sliceAt(now: Instant, start: Instant = START, end: Instant = END): TimeSlice? =
        DefaultCountdownModel()
            .slices(CountdownTarget(start, end), now)
            .singleOrNull { it.category == TimeCategory.DAYS }

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

    test("remaining is quantized for display while the ring stays exact") {
        // 70 h = 2.9 days left. The text must not claim "3 days", and the ring must not round
        // to the same place: 70/240 = 29%, not "nearly full".
        val slice = sliceAt(END.minus(Duration.ofHours(70)))!!
        slice.remaining shouldBe Duration.ofDays(2)
        (slice.fractionRemaining * 100).toInt() shouldBe 29
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
