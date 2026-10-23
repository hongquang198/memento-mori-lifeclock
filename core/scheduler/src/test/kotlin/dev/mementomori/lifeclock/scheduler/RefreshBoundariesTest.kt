package dev.mementomori.lifeclock.scheduler

import dev.mementomori.lifeclock.model.RefreshIntent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.ZoneId

private val UTC: ZoneId = ZoneId.of("UTC")
private val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

/**
 * T2.11 — the boundary rule behind each `RefreshIntent`. All instants are local-midnight or
 * local-hour boundaries, so a timezone with a non-zero offset must still produce the same wall
 * clock reading after the boundary.
 */
class RefreshBoundariesTest : FunSpec({

    val utc = RefreshBoundaries(UTC)

    test("HOURLY moves to the next whole local hour") {
        utc.nextBoundary(Instant.parse("2026-06-15T10:15:00Z"), RefreshIntent.HOURLY) shouldBe
            Instant.parse("2026-06-15T11:00:00Z")
    }

    test("HOURLY on an exact hour still moves forward") {
        utc.nextBoundary(Instant.parse("2026-06-15T10:00:00Z"), RefreshIntent.HOURLY) shouldBe
            Instant.parse("2026-06-15T11:00:00Z")
    }

    test("DAILY moves to the next local midnight") {
        utc.nextBoundary(Instant.parse("2026-06-15T13:45:00Z"), RefreshIntent.DAILY) shouldBe
            Instant.parse("2026-06-16T00:00:00Z")
    }

    test("DAILY on midnight still moves to the next day") {
        utc.nextBoundary(Instant.parse("2026-06-15T00:00:00Z"), RefreshIntent.DAILY) shouldBe
            Instant.parse("2026-06-16T00:00:00Z")
    }

    test("DAILY survives the spring-forward day in Berlin") {
        // 2026-03-29 02:00 CET -> 03:00 CEST, so local midnight is still UTC+1.
        RefreshBoundaries(BERLIN).nextBoundary(Instant.parse("2026-03-28T22:30:00Z"), RefreshIntent.DAILY) shouldBe
            Instant.parse("2026-03-28T23:00:00Z")
    }

    test("SIX_HOURLY walks the 00/06/12/18 grid") {
        utc.nextBoundary(Instant.parse("2026-06-15T05:59:00Z"), RefreshIntent.SIX_HOURLY) shouldBe
            Instant.parse("2026-06-15T06:00:00Z")
        utc.nextBoundary(Instant.parse("2026-06-15T06:00:00Z"), RefreshIntent.SIX_HOURLY) shouldBe
            Instant.parse("2026-06-15T12:00:00Z")
        utc.nextBoundary(Instant.parse("2026-06-15T18:30:00Z"), RefreshIntent.SIX_HOURLY) shouldBe
            Instant.parse("2026-06-16T00:00:00Z")
    }

    test("every intent always moves strictly forward, in any timezone") {
        val zones = listOf(UTC, BERLIN, ZoneId.of("Asia/Kathmandu"), ZoneId.of("America/Sao_Paulo"))
        val instants = listOf(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-03-29T01:30:00Z"),
            Instant.parse("2026-10-25T00:59:59Z"),
            Instant.parse("2026-12-31T23:00:00Z"),
        )
        for (zone in zones) {
            val boundaries = RefreshBoundaries(zone)
            for (intent in RefreshIntent.entries) {
                for (instant in instants) {
                    val next = boundaries.nextBoundary(instant, intent)
                    check(next.isAfter(instant)) { "$zone $intent from $instant gave $next" }
                }
            }
        }
    }
})
