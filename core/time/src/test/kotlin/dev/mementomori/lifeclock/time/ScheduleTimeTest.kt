package dev.mementomori.lifeclock.time

import dev.mementomori.lifeclock.model.TimeCategory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val UTC: ZoneId = ZoneId.of("UTC")
private val DAY: LocalDate = LocalDate.of(2026, 11, 5)

/** The schedule's two halves, and the rule that editing one must not reset the other. */
class ScheduleTimeTest : StringSpec({

    fun at(time: LocalTime, date: LocalDate = DAY, zone: ZoneId = UTC): Long =
        ScheduleTime.instant(date, time, zone).toEpochMilli()

    "picking a new date keeps the time of day" {
        val start = at(LocalTime.of(14, 30))

        val moved = ScheduleTime.withDate(start, DAY.plusDays(2), UTC)

        ScheduleTime.dateOf(moved, UTC) shouldBe DAY.plusDays(2)
        ScheduleTime.timeOf(moved, UTC) shouldBe LocalTime.of(14, 30)
    }

    "picking a new time keeps the date" {
        val start = at(LocalTime.of(14, 30))

        val moved = ScheduleTime.withTime(start, LocalTime.of(9, 15), UTC)

        ScheduleTime.dateOf(moved, UTC) shouldBe DAY
        ScheduleTime.timeOf(moved, UTC) shouldBe LocalTime.of(9, 15)
    }

    "an eight-hour countdown is expressible: 09:00 to 17:00 on one day" {
        val start = ScheduleTime.instant(DAY, LocalTime.of(9, 0), UTC)
        val end = ScheduleTime.instant(DAY, LocalTime.of(17, 0), UTC)

        val slices = DefaultCountdownModel()
            .slices(CountdownTarget(start, end), start, UTC)
            .associateBy { it.category }

        slices.getValue(TimeCategory.HOURS).remaining shouldBe 8L
        // The same span in days still rounds up to one, and both arcs read as full at the start.
        slices.getValue(TimeCategory.DAYS).remaining shouldBe 1L
        slices.getValue(TimeCategory.HOURS).fractionRemaining shouldBe 1.0
    }

    "the defaults reproduce the day-only behaviour that came before" {
        ScheduleTime.DEFAULT_START shouldBe LocalTime.MIDNIGHT
        ScheduleTime.DEFAULT_DEADLINE shouldBe LocalTime.of(23, 59)

        Instant.ofEpochMilli(ScheduleTime.defaultDeadline(UTC)).atZone(UTC).toLocalTime() shouldBe
            LocalTime.of(23, 59)
    }

    "a spring-forward gap moves forward instead of failing" {
        // 02:30 does not exist in Berlin on 2026-03-29; the instant does, one hour later.
        val berlin = ZoneId.of("Europe/Berlin")
        val instant = ScheduleTime.instant(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), berlin)

        instant.atZone(berlin).toLocalTime() shouldBe LocalTime.of(3, 30)
    }
})
