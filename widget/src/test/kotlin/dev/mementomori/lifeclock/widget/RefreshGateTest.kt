package dev.mementomori.lifeclock.widget

import dev.mementomori.lifeclock.data.InMemoryLifeClockStore
import dev.mementomori.lifeclock.data.LifeClockStore
import dev.mementomori.lifeclock.model.RefreshIntent
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.time.Clock
import dev.mementomori.lifeclock.time.DefaultCountdownModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private val NOON: Instant = Instant.parse("2026-06-15T12:00:00Z")
private val NEXT_MIDNIGHT: Instant = Instant.parse("2026-06-16T00:00:00Z")
private val NEXT_HOUR: Instant = Instant.parse("2026-06-15T13:00:00Z")

private class FakeDependencies(
    store: LifeClockStore,
    override val zone: ZoneId = ZoneId.of("UTC"),
) : WidgetDependencies {
    override val configs = store.configs
    override val bindings = store.bindings
    override val countdownModel = DefaultCountdownModel()
    override val clock = Clock { NOON }
}

private suspend fun LifeClockStore.bindCountdown(configId: String, intent: RefreshIntent) {
    configs.save(
        WidgetConfigRecord(
            configId = configId,
            name = configId,
            type = WidgetType.COUNTDOWN,
            startEpochMillis = 0,
            endEpochMillis = 1,
            granularity = intent,
        ),
    )
    bindings.bind(configId, configId)
}

/**
 * T2.12 — the granularity gate. A coarse `WorkManager` tick must not redraw a daily widget every
 * 15 minutes, but the first tick in a fresh process must always redraw.
 *
 * The assertions are mostly on how often the draw lambda runs, not on the flag: drawing is the
 * cost being managed, and `settled()` is no longer separable from it.
 */
class RefreshGateTest {

    @Test
    fun `nothing placed means nothing to render and no wakeup`() = runBlocking {
        val gate = RefreshGate(FakeDependencies(InMemoryLifeClockStore()))
        var draws = 0

        val decision = gate.tick(NOON, force = false) { draws++ }

        assertFalse(decision.drew)
        assertNull(decision.nextAt)
        assertEquals(0, draws)
    }

    @Test
    fun `the first tick in a fresh process draws`() = runBlocking {
        val store = InMemoryLifeClockStore().apply { bindCountdown("daily", RefreshIntent.DAILY) }
        val gate = RefreshGate(FakeDependencies(store))
        var draws = 0

        val decision = gate.tick(NOON, force = false) { draws++ }

        assertTrue(decision.drew)
        assertEquals(NEXT_MIDNIGHT, decision.nextAt)
        assertEquals(1, draws)
    }

    @Test
    fun `a tick inside the same day does not draw again`() = runBlocking {
        val store = InMemoryLifeClockStore().apply { bindCountdown("daily", RefreshIntent.DAILY) }
        val gate = RefreshGate(FakeDependencies(store))
        var draws = 0

        gate.tick(NOON, force = false) { draws++ }
        val decision = gate.tick(NEXT_MIDNIGHT.minusSeconds(900), force = false) { draws++ }

        assertFalse(decision.drew)
        assertEquals(1, draws)
    }

    @Test
    fun `reaching the boundary draws once`() = runBlocking {
        val store = InMemoryLifeClockStore().apply { bindCountdown("daily", RefreshIntent.DAILY) }
        val gate = RefreshGate(FakeDependencies(store))
        var draws = 0

        gate.tick(NOON, force = false) { draws++ }

        val due = gate.tick(NEXT_MIDNIGHT, force = false) { draws++ }
        assertTrue(due.drew)
        assertEquals(Instant.parse("2026-06-17T00:00:00Z"), due.nextAt)

        assertFalse(gate.tick(NEXT_MIDNIGHT.plusSeconds(900), force = false) { draws++ }.drew)
        assertEquals(2, draws)
    }

    @Test
    fun `an hourly and a daily widget wake at the earlier boundary independently`() = runBlocking {
        val store = InMemoryLifeClockStore()
        store.bindCountdown("hourly", RefreshIntent.HOURLY)
        store.configs.save(
            WidgetConfigRecord(
                configId = "daily",
                name = "daily",
                type = WidgetType.COUNTDOWN,
                startEpochMillis = 0,
                endEpochMillis = 1,
                granularity = RefreshIntent.DAILY,
            ),
        )
        store.bindings.bind("daily-widget", "daily")

        val gate = RefreshGate(FakeDependencies(store))

        assertEquals(NEXT_HOUR, gate.tick(NOON, force = false) {}.nextAt)

        // 13:00 is an hourly boundary but not a daily one: exactly one of the two is due.
        assertTrue(gate.tick(NEXT_HOUR, force = false) {}.drew)
    }

    @Test
    fun `force draws even when the boundary has not moved`() = runBlocking {
        val store = InMemoryLifeClockStore().apply { bindCountdown("daily", RefreshIntent.DAILY) }
        val gate = RefreshGate(FakeDependencies(store))
        var draws = 0

        gate.tick(NOON, force = false) { draws++ }
        val forced = gate.tick(NOON.plusSeconds(60), force = true) { draws++ }

        assertTrue(forced.drew)
        assertEquals(2, draws)
    }

    @Test
    fun `a failed draw stays due, so the next tick retries`() = runBlocking {
        val store = InMemoryLifeClockStore().apply { bindCountdown("daily", RefreshIntent.DAILY) }
        val gate = RefreshGate(FakeDependencies(store))
        gate.tick(NOON, force = false) {}

        runCatching { gate.tick(NEXT_MIDNIGHT, force = false) { throw IllegalStateException("no RemoteViews") } }

        var draws = 0
        val retry = gate.tick(NEXT_MIDNIGHT.plusSeconds(60), force = false) { draws++ }
        assertTrue(retry.drew)
        assertEquals(1, draws)
    }

    @Test
    fun `concurrent callers all receive the same gate`() {
        val dependencies = FakeDependencies(InMemoryLifeClockStore())
        val pool = Executors.newFixedThreadPool(4)
        try {
            val start = CountDownLatch(1)
            val futures = (1..200).map { pool.submit(Callable { start.await(); ProcessRefreshGate.of(dependencies) }) }
            start.countDown()

            assertEquals(1, futures.map { it.get() }.toSet().size)
        } finally {
            pool.shutdownNow()
        }
    }
}
