package dev.mementomori.lifeclock.scheduler

import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.ZoneId

private val UTC: ZoneId = ZoneId.of("UTC")
private val SAIGON: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

private val NOW: Instant = Instant.parse("2026-06-15T10:15:00Z")

private fun countdown(
    end: String,
    vararg rings: TimeCategory,
): WidgetConfigRecord = WidgetConfigRecord(
    configId = "c",
    name = "c",
    type = WidgetType.COUNTDOWN,
    startEpochMillis = 0,
    endEpochMillis = Instant.parse(end).toEpochMilli(),
    rings = rings.map { RingConfigRecord(it) },
)

/**
 * F8 — the content floor.
 *
 * A ring's count changes on a deadline-relative grid, not on the wall clock: a countdown ending at
 * 22:45 shows "22 hours" from 22:45-1h onward, so the picture becomes untrue at 21:45, not at
 * midnight. The intent is a ceiling on how stale a frame may be; this is the floor under it.
 */
class RefreshScheduleTest : FunSpec({

    test("an hours ring changes at the deadline's minute, not on the hour") {
        RefreshSchedule.contentChange(countdown("2026-06-20T22:45:00Z", TimeCategory.HOURS), NOW, UTC) shouldBe
            Instant.parse("2026-06-15T10:45:00Z")
    }

    test("one hour left: the next change is the deadline itself") {
        RefreshSchedule.contentChange(
            countdown("2026-06-15T11:15:00Z", TimeCategory.HOURS),
            NOW,
            UTC,
        ) shouldBe Instant.parse("2026-06-15T11:15:00Z")
    }

    test("a passed deadline has nothing left to change") {
        RefreshSchedule.contentChange(countdown("2026-06-15T10:15:00Z", TimeCategory.HOURS), NOW, UTC) shouldBe null
    }

    test("a days ring changes at the deadline's time of day") {
        // 5.52 days left -> the 6 becomes 5 at 22:45 today.
        RefreshSchedule.contentChange(countdown("2026-06-20T22:45:00Z", TimeCategory.DAYS), NOW, UTC) shouldBe
            Instant.parse("2026-06-15T22:45:00Z")
    }

    test("a weeks ring changes on the deadline's weekday") {
        // 20.52 days left -> ceil = 3 weeks, so 3 becomes 2 at deadline - 2 weeks.
        RefreshSchedule.contentChange(countdown("2026-07-05T22:45:00Z", TimeCategory.WEEKS), NOW, UTC) shouldBe
            Instant.parse("2026-06-21T22:45:00Z")
    }

    test("the finest ring wins when several are drawn") {
        RefreshSchedule.contentChange(
            countdown("2026-07-05T22:45:00Z", TimeCategory.WEEKS, TimeCategory.HOURS),
            NOW,
            UTC,
        ) shouldBe Instant.parse("2026-06-15T10:45:00Z")
    }

    test("months move at local midnight: the engine compares dates") {
        RefreshSchedule.contentChange(countdown("2027-01-01T00:00:00Z", TimeCategory.MONTHS), NOW, SAIGON) shouldBe
            Instant.parse("2026-06-15T17:00:00Z")
    }

    test("a life clock changes at local midnight") {
        val lifeClock = WidgetConfigRecord(
            configId = "life",
            name = "life",
            type = WidgetType.LIFE_CLOCK,
            startEpochMillis = 0,
            profileRef = "p",
        )
        RefreshSchedule.contentChange(lifeClock, NOW, SAIGON) shouldBe Instant.parse("2026-06-15T17:00:00Z")
    }

    test("a countdown with no rings never wakes for content") {
        RefreshSchedule.contentChange(countdown("2026-06-20T22:45:00Z"), NOW, UTC) shouldBe null
    }
})
