package dev.mementomori.lifeclock.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A schedule field as two independently editable halves.
 *
 * A countdown's span has always been an `Instant` pair, but authoring was day-locked
 * (`atStartOfDay` / `atTime(23, 59)`), so the shortest expressible countdown was one day - and an
 * hours ring could never show anything a days ring did not already show.
 *
 * The rule these functions exist to keep: **editing one half never resets the other**. Picking a
 * date keeps the time the user already chose, and vice versa. Rebuilding the instant from scratch
 * on every pick is exactly what would turn "14:30" back into "23:59" when a date is touched.
 */
object ScheduleTime {

    /** Reproduces the day-only behaviour that preceded the time picker, so stored spans still mean what they meant. */
    val DEFAULT_START: LocalTime = LocalTime.MIDNIGHT
    val DEFAULT_DEADLINE: LocalTime = LocalTime.of(23, 59)

    fun dateOf(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun timeOf(epochMillis: Long, zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()

    /**
     * The instant for a wall-clock date and time.
     *
     * On a DST spring-forward gap the local time does not exist, and `atZone` moves it forward by
     * the gap: the honest answer, because the instant is real even though 02:30 is not.
     */
    fun instant(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        date.atTime(time).atZone(zone).toInstant()

    /** Replaces the date, keeping the time of day. */
    fun withDate(epochMillis: Long, date: LocalDate, zone: ZoneId): Long =
        instant(date, timeOf(epochMillis, zone), zone).toEpochMilli()

    /** Replaces the time of day, keeping the date. */
    fun withTime(epochMillis: Long, time: LocalTime, zone: ZoneId): Long =
        instant(dateOf(epochMillis, zone), time, zone).toEpochMilli()

    /** A deadline nobody has picked yet: today, through the end of the day. */
    fun defaultDeadline(zone: ZoneId): Long =
        instant(LocalDate.now(zone), DEFAULT_DEADLINE, zone).toEpochMilli()
}
