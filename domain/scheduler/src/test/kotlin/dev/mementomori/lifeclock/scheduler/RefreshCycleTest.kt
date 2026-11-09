package dev.mementomori.lifeclock.scheduler

import dev.mementomori.lifeclock.model.RefreshIntent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.ZoneId

private val NOON: Instant = Instant.parse("2026-06-15T12:00:00Z")
private val MIDNIGHT: Instant = Instant.parse("2026-06-16T00:00:00Z")

/** T2.11 — the IDLE → SCHEDULED → REFRESHED transitions, driven with explicit instants. */
class RefreshCycleTest : FunSpec({

    val cycle = RefreshCycle(RefreshBoundaries(ZoneId.of("UTC")))

    test("an idle widget ignores ticks") {
        cycle.onTick(RefreshState.Idle, NOON, RefreshIntent.DAILY) shouldBe RefreshState.Idle
    }

    test("enabling schedules the next boundary") {
        cycle.onEnabled(NOON, RefreshIntent.DAILY) shouldBe
            RefreshState(WidgetLifecycle.SCHEDULED, MIDNIGHT)
    }

    test("a tick before the boundary changes nothing") {
        val scheduled = RefreshState(WidgetLifecycle.SCHEDULED, MIDNIGHT)
        cycle.onTick(scheduled, MIDNIGHT.minusSeconds(1), RefreshIntent.DAILY) shouldBe scheduled
    }

    test("a tick at the boundary owes a redraw") {
        val scheduled = RefreshState(WidgetLifecycle.SCHEDULED, MIDNIGHT)
        cycle.onTick(scheduled, MIDNIGHT, RefreshIntent.DAILY) shouldBe
            RefreshState(WidgetLifecycle.REFRESHED, Instant.parse("2026-06-17T00:00:00Z"))
    }

    test("settling a redraw returns to scheduled, keeping the boundary") {
        val refreshed = RefreshState(WidgetLifecycle.REFRESHED, MIDNIGHT)
        cycle.onSettled(refreshed) shouldBe RefreshState(WidgetLifecycle.SCHEDULED, MIDNIGHT)
    }

    test("settling a scheduled state is a no-op") {
        val scheduled = RefreshState(WidgetLifecycle.SCHEDULED, MIDNIGHT)
        cycle.onSettled(scheduled) shouldBe scheduled
    }

    test("disabling returns to idle") {
        cycle.onDisabled() shouldBe RefreshState.Idle
    }

    test("a whole day walks IDLE -> SCHEDULED -> REFRESHED -> SCHEDULED") {
        var state = RefreshState.Idle
        state = cycle.onEnabled(NOON, RefreshIntent.DAILY)
        state.lifecycle shouldBe WidgetLifecycle.SCHEDULED

        state = cycle.onTick(state, MIDNIGHT, RefreshIntent.DAILY)
        state.lifecycle shouldBe WidgetLifecycle.REFRESHED
        state.nextRefreshAt shouldBe Instant.parse("2026-06-17T00:00:00Z")

        state = cycle.onSettled(state)
        state.lifecycle shouldBe WidgetLifecycle.SCHEDULED

        // Two ticks inside the same day must not redraw twice.
        state = cycle.onTick(state, Instant.parse("2026-06-16T09:00:00Z"), RefreshIntent.DAILY)
        state.lifecycle shouldBe WidgetLifecycle.SCHEDULED
    }
})
