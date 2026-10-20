package dev.mementomori.lifeclock.time

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private val UTC: ZoneId = ZoneId.of("UTC")
private val START: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val END: Instant = START.plus(Duration.ofDays(10))

private val MODEL = DefaultCountdownModel()

/**
 * T1.1–T1.8 — the specification of every slice category.
 *
 * Written before the implementation. Each test states one rule from design.md §2.2, so this file
 * doubles as the readable contract: if you disagree with a test, the design is wrong, not the test.
 */
class CountdownModelTest : FunSpec({

    fun slice(
        category: TimeCategory,
        now: Instant,
        start: Instant = START,
        end: Instant = END,
        zone: ZoneId = UTC,
        model: CountdownModel = MODEL,
    ): TimeSlice? =
        model.slices(CountdownTarget(start, end), now, zone)
            .singleOrNull { it.category == category }

    context("DAYS") {

        test("at the start: full total, full arc") {
            val s = slice(TimeCategory.DAYS, START)!!
            s.total shouldBe 10L
            s.remaining shouldBe 10L
            s.fractionRemaining shouldBe 1.0
        }

        test("halfway: half the days, half-full arc") {
            val s = slice(TimeCategory.DAYS, START.plus(Duration.ofDays(5)))!!
            s.remaining shouldBe 5L
            s.fractionRemaining shouldBe 0.5
        }

        test("at the deadline: zero left, empty arc") {
            val s = slice(TimeCategory.DAYS, END)!!
            s.remaining shouldBe 0L
            s.fractionRemaining shouldBe 0.0
        }

        test("past the deadline: clamps to zero instead of going negative") {
            val s = slice(TimeCategory.DAYS, END.plus(Duration.ofDays(3)))!!
            s.remaining shouldBe 0L
            s.fractionRemaining shouldBe 0.0
        }

        test("before the countdown starts: arc clamps to full, never above it") {
            val s = slice(TimeCategory.DAYS, START.minus(Duration.ofDays(2)))!!
            s.remaining shouldBe 10L
            s.fractionRemaining shouldBe 1.0
        }

        test("display rounds up, and stays consistent with the exact arc") {
            // 70 h = 2.9 days left. Rounding up gives "3 days"; 3/10 agrees with the arc's 29%.
            // Truncating would print "2 days" next to a 29%-full arc - two contradicting numbers.
            val s = slice(TimeCategory.DAYS, END.minus(Duration.ofHours(70)))!!
            s.remaining shouldBe 3L
            (s.fractionRemaining * 100).toInt() shouldBe 29
        }

        test("the final partial day reads 1, never 0") {
            // 4 hours left is still "1 day" for display purposes; 0 would look expired.
            slice(TimeCategory.DAYS, END.minus(Duration.ofHours(4)))!!.remaining shouldBe 1L
        }
    }

    context("WEEKS") {

        test("a 10-day countdown spans 2 weeks") {
            val s = slice(TimeCategory.WEEKS, START)!!
            s.total shouldBe 2L
            s.remaining shouldBe 2L
            s.fractionRemaining shouldBe 1.0
        }

        test("a 15-day countdown spans 3 weeks") {
            slice(TimeCategory.WEEKS, START, end = START.plus(Duration.ofDays(15)))!!.total shouldBe 3L
        }

        test("7.5 days left is still in the second week, so it reads 2") {
            // Rounding weeks must use the exact duration, not truncated days: 7.5 days is
            // ceil(7.5 / 7) = 2 weeks, while truncating to 7 days first would wrongly say 1.
            val now = END.minus(Duration.ofDays(7)).minus(Duration.ofHours(12))
            slice(TimeCategory.WEEKS, now)!!.remaining shouldBe 2L
        }

        test("exactly 7 days left reads 1") {
            slice(TimeCategory.WEEKS, END.minus(Duration.ofDays(7)))!!.remaining shouldBe 1L
        }

        test("past the deadline clamps to zero") {
            val s = slice(TimeCategory.WEEKS, END.plus(Duration.ofDays(5)))!!
            s.remaining shouldBe 0L
            s.fractionRemaining shouldBe 0.0
        }
    }

    context("MONTHS - calendar, not 30-day") {

        test("Jan 1 to Apr 1 is 3 months") {
            val s = slice(
                TimeCategory.MONTHS,
                now = START,
                start = Instant.parse("2026-01-01T00:00:00Z"),
                end = Instant.parse("2026-04-01T00:00:00Z"),
            )!!
            s.total shouldBe 3L
            s.remaining shouldBe 3L
        }

        test("two months elapsed leaves one month") {
            val s = slice(
                TimeCategory.MONTHS,
                now = Instant.parse("2026-03-01T00:00:00Z"),
                start = Instant.parse("2026-01-01T00:00:00Z"),
                end = Instant.parse("2026-04-01T00:00:00Z"),
            )!!
            s.remaining shouldBe 1L
        }

        test("a partial month rounds up") {
            // 30 days is not a month in calendar terms; anything inside April reads "1".
            val s = slice(
                TimeCategory.MONTHS,
                now = Instant.parse("2026-03-02T00:00:00Z"),
                start = Instant.parse("2026-01-01T00:00:00Z"),
                end = Instant.parse("2026-04-01T00:00:00Z"),
            )!!
            s.remaining shouldBe 1L
        }

        test("month-end: Jan 31 to Feb 28 is still 1 month") {
            // Feb has no 31st, so java.time clamps Jan 31 + 1 month to Feb 28. That is the
            // calendar ending the month, not a shortfall - it must not read "0 months".
            val s = slice(
                TimeCategory.MONTHS,
                now = Instant.parse("2026-01-31T00:00:00Z"),
                start = Instant.parse("2026-01-31T00:00:00Z"),
                end = Instant.parse("2026-02-28T00:00:00Z"),
            )!!
            s.total shouldBe 1L
        }

        test("leap year: Jan 29 to Feb 29 2028 is 1 month") {
            val s = slice(
                TimeCategory.MONTHS,
                now = Instant.parse("2028-01-29T00:00:00Z"),
                start = Instant.parse("2028-01-29T00:00:00Z"),
                end = Instant.parse("2028-02-29T00:00:00Z"),
            )!!
            s.total shouldBe 1L
        }

        test("the zone decides which month an instant belongs to") {
            // 2026-02-28T20:00Z is 28 Feb in UTC but 1 Mar in Tokyo (UTC+9).
            val start = Instant.parse("2026-02-28T20:00:00Z")
            val end = Instant.parse("2026-03-31T20:00:00Z")

            slice(TimeCategory.MONTHS, start, start = start, end = end, zone = UTC)!!.total shouldBe 2L
            slice(TimeCategory.MONTHS, start, start = start, end = end, zone = ZoneId.of("Asia/Tokyo"))!!
                .total shouldBe 1L
        }
    }

    context("DST and far-future bounds (T1.7)") {

        test("a DST transition changes elapsed days but not calendar months") {
            // Europe/Berlin springs forward on 2026-03-29: the span is 31 days of wall time but
            // only 743 hours of elapsed time. Days are an elapsed unit; months are a calendar one.
            val berlin = ZoneId.of("Europe/Berlin")
            val start = Instant.parse("2026-03-01T00:00:00Z")
            val end = Instant.parse("2026-04-01T00:00:00Z")

            slice(TimeCategory.DAYS, start, start = start, end = end, zone = berlin)!!.total shouldBe 31L
            slice(TimeCategory.MONTHS, start, start = start, end = end, zone = berlin)!!.total shouldBe 1L
        }

        test("a 300-year countdown does not overflow") {
            // Guards the toMillis() choice in fractionOf: toNanos overflows past ~292 years.
            val start = Instant.parse("2026-01-01T00:00:00Z")
            val end = Instant.parse("2326-01-01T00:00:00Z")

            val days = slice(TimeCategory.DAYS, start, start = start, end = end)!!
            days.fractionRemaining shouldBe 1.0

            slice(TimeCategory.MONTHS, start, start = start, end = end)!!.total shouldBe 3600L
        }
    }

    context("NET_FREE_HOURS") {

        val policy = FreeTimePolicy(sleepHoursPerDay = 8.0, maintenanceRatio = 0.25)
        val withPolicy = DefaultCountdownModel(policy)

        test("10 days at 8 h sleep and 25% maintenance leave 120 free hours") {
            // 240 h - 80 h sleep = 160 waking; 160 x 0.75 = 120.
            val s = slice(TimeCategory.NET_FREE_HOURS, START, model = withPolicy)!!
            s.total shouldBe 120L
            s.remaining shouldBe 120L
        }

        test("halfway through leaves half the free hours") {
            val s = slice(
                TimeCategory.NET_FREE_HOURS,
                START.plus(Duration.ofDays(5)),
                model = withPolicy,
            )!!
            s.remaining shouldBe 60L
            s.fractionRemaining shouldBe 0.5
        }

        test("rounding is not fooled by floating-point error") {
            // 70 h - 23.333... h sleep = 46.666... waking; x 0.75 = 35.00000000000001 in IEEE
            // arithmetic. A naive ceil() would report 36 free hours.
            slice(TimeCategory.NET_FREE_HOURS, END.minus(Duration.ofHours(70)), model = withPolicy)!!
                .remaining shouldBe 35L
        }

        test("without a policy the slice is absent, not zero") {
            val slices = MODEL.slices(CountdownTarget(START, END), START, UTC)
            slices.map { it.category } shouldBe
                listOf(TimeCategory.DAYS, TimeCategory.WEEKS, TimeCategory.MONTHS)
        }
    }

    context("degenerate targets") {

        test("a zero-length countdown has no meaningful slice") {
            MODEL.slices(CountdownTarget(START, START), START, UTC).shouldBeEmpty()
        }

        test("an inverted countdown (end before start) has no slice") {
            MODEL.slices(CountdownTarget(END, START), START, UTC).shouldBeEmpty()
        }
    }

    context("FreeTimePolicy contract") {

        test("rejects impossible sleep hours") {
            shouldThrow<IllegalArgumentException> { FreeTimePolicy(25.0, 0.1) }
            shouldThrow<IllegalArgumentException> { FreeTimePolicy(-1.0, 0.1) }
        }

        test("rejects an impossible maintenance ratio") {
            shouldThrow<IllegalArgumentException> { FreeTimePolicy(8.0, 1.5) }
        }
    }
})
