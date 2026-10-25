package dev.mementomori.lifeclock.widget

import dev.mementomori.lifeclock.data.LifeClockStore
import dev.mementomori.lifeclock.time.Clock
import dev.mementomori.lifeclock.time.DefaultCountdownModel
import java.time.Instant
import java.time.ZoneId

/** Noon UTC, so every boundary in these tests has an obvious answer. */
internal val TEST_NOW: Instant = Instant.parse("2026-06-15T12:00:00Z")

/**
 * The smallest graph the widget layer needs, over a real store.
 *
 * Shared by the tests rather than rebuilt per file: the fakes were drifting apart, and a test that
 * renders against a different graph than the gate is tested against proves less.
 */
internal class TestDependencies(
    store: LifeClockStore,
    override val zone: ZoneId = ZoneId.of("UTC"),
    override val clock: Clock = Clock { TEST_NOW },
) : WidgetDependencies {
    override val configs = store.configs
    override val bindings = store.bindings
    override val countdownModel = DefaultCountdownModel()
}
