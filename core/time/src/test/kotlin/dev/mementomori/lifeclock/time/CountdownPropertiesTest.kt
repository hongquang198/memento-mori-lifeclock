package dev.mementomori.lifeclock.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.time.Instant
import java.time.ZoneId

/**
 * T1.9 — properties that must hold for *every* target and every "now", not just the examples.
 *
 * Example-based tests prove the cases we thought of. These prove the invariants we rely on:
 * an arc can never leave its track, the countdown can never run backwards, and the four arcs
 * on a CONCENTRIC widget can never disagree about how much time has passed.
 */
class CountdownPropertiesTest : FunSpec({

    val zone: ZoneId = ZoneId.of("UTC")
    val model = DefaultCountdownModel(FreeTimePolicy(sleepHoursPerDay = 8.0, maintenanceRatio = 0.25))
    val anchor: Instant = Instant.parse("2026-01-01T00:00:00Z")

    // ~95 years of span, and a "now" that can sit well before the start or well past the end.
    val spans = Arb.long(1L, 3_000_000_000L)
    val nowOffsets = Arb.long(-1_000_000_000L, 4_000_000_000L)

    fun targetOf(spanSeconds: Long) = CountdownTarget(anchor, anchor.plusSeconds(spanSeconds))

    test("fractionRemaining stays inside [0,1] wherever 'now' lands") {
        checkAll(spans, nowOffsets) { span, nowOffset ->
            model.slices(targetOf(span), anchor.plusSeconds(nowOffset), zone).forEach { slice ->
                slice.fractionRemaining.shouldBeGreaterThanOrEqual(0.0)
                slice.fractionRemaining.shouldBeLessThanOrEqual(1.0)
            }
        }
    }

    test("fraction never increases as time moves forward") {
        checkAll(spans, nowOffsets, Arb.long(0L, 1_000_000_000L)) { span, nowOffset, advance ->
            val target = targetOf(span)
            val earlier = anchor.plusSeconds(nowOffset)
            val later = earlier.plusSeconds(advance)

            val before = model.slices(target, earlier, zone).associate { it.category to it.fractionRemaining }
            val after = model.slices(target, later, zone).associate { it.category to it.fractionRemaining }

            before.forEach { (category, fraction) ->
                after.getValue(category).shouldBeLessThanOrEqual(fraction)
            }
        }
    }

    test("every category reports the same fraction") {
        checkAll(spans, nowOffsets) { span, nowOffset ->
            val slices = model.slices(targetOf(span), anchor.plusSeconds(nowOffset), zone)
            slices.map { it.fractionRemaining }.distinct().size shouldBe 1
        }
    }
})
