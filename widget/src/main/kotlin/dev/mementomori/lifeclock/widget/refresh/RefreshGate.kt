package dev.mementomori.lifeclock.widget.refresh

import dev.mementomori.lifeclock.model.RefreshIntent
import dev.mementomori.lifeclock.scheduler.RefreshBoundaries
import dev.mementomori.lifeclock.scheduler.RefreshCycle
import dev.mementomori.lifeclock.scheduler.RefreshState
import dev.mementomori.lifeclock.scheduler.WidgetLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import dev.mementomori.lifeclock.widget.WidgetDependencies
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder

/**
 * Decides, at a given instant, whether the widgets owe a redraw and when to wake up next.
 *
 * The cycle is tracked per [RefreshIntent], in memory, because two configs can ask for different
 * granularities: a daily widget must not be redrawn just because an hourly one is due.
 *
 * Nothing is persisted. Losing this state on process death costs exactly one extra redraw (the
 * cycle restarts from [WidgetLifecycle.IDLE]); a stored timestamp would instead have to survive
 * clock changes and timezone edits, which is the harder problem.
 */
class RefreshGate(private val dependencies: WidgetDependencies) {

    /**
     * Serialises whole ticks, not just the state update.
     *
     * The periodic chain and the one-shot chain are separate WorkManager jobs, so they can run at
     * the same time. Holding the lock across [draw] is what stops both of them drawing the same
     * boundary; locking only the bookkeeping would still let two renders through.
     */
    private val mutex = Mutex()
    private var states: Map<RefreshIntent, RefreshState> = emptyMap()

    /** Rebuilt per use: the user can change timezone while the process (and this gate) lives. */
    private val cycle: RefreshCycle get() = RefreshCycle(RefreshBoundaries(dependencies.zone))

    /**
     * [drew] is telemetry, not an instruction: a redraw, if any, already happened inside [tick].
     * It exists so logcat can tell "ran and skipped" from "ran and drew".
     * [nextAt] is the earliest boundary across all widgets, or null when none are placed.
     */
    data class Decision(val drew: Boolean, val nextAt: Instant?)

    /**
     * Decides and, when due, runs [draw] - as one atomic step.
     *
     * There is deliberately no separate `settled()`: settling only happens after a successful
     * [draw], so a caller cannot mark the boundary drawn without drawing it. If [draw] throws, the
     * state stays REFRESHED and the next tick retries.
     */
    suspend fun tick(now: Instant, force: Boolean, draw: suspend () -> Unit): Decision = mutex.withLock {
        val intents = activeIntents()
        if (intents.isEmpty()) {
            states = emptyMap()
            return@withLock Decision(drew = false, nextAt = null)
        }

        // A fresh process has no memory of the last redraw, so it must redraw once: the frame on
        // screen may predate a reboot. Same for `force` (boot, time or timezone change).
        val unseen = force || intents.any { states[it] == null }
        if (force) states = emptyMap()

        states = intents.associateWith { intent ->
            val scheduled = when (states[intent]?.lifecycle) {
                null, WidgetLifecycle.IDLE -> cycle.onEnabled(now, intent)
                else -> states.getValue(intent)
            }
            cycle.onTick(scheduled, now, intent)
        }

        val due = unseen || states.values.any { it.lifecycle == WidgetLifecycle.REFRESHED }
        if (due) {
            draw()
            states = states.mapValues { (_, state) -> cycle.onSettled(state) }
        }

        Decision(drew = due, nextAt = states.values.mapNotNull { it.nextRefreshAt }.minOrNull())
    }

    /** The granularity of every bound config, skipping bindings whose config was deleted. */
    private suspend fun activeIntents(): Set<RefreshIntent> {
        val bindings = dependencies.bindings.observeAllBindings().first()
        if (bindings.isEmpty()) return emptySet()
        val configs = dependencies.configs.observeAll().first().associateBy { it.configId }
        return bindings.mapNotNull { configs[it.configId]?.granularity }.toSet()
    }
}

/**
 * One [RefreshGate] per process.
 *
 * Deliberately a double-checked lock rather than `get()` + `compareAndSet`: with CAS the loser of
 * the race gets its *own* instance back (`also` returns its receiver, not the CAS result), so two
 * workers would each keep a private cycle and draw the same boundary. Here the loser re-reads the
 * winner's instance inside the lock, so every caller ends up with the same one.
 *
 * Self-initialising rather than published by `:app` (unlike [WidgetDependenciesHolder]) because it
 * needs nothing the app knows, and losing it is harmless.
 */
internal object ProcessRefreshGate {

    @Volatile
    private var instance: RefreshGate? = null

    fun of(dependencies: WidgetDependencies): RefreshGate =
        instance ?: synchronized(this) {
            instance ?: RefreshGate(dependencies).also { instance = it }
        }

    /** Robolectric reuses one sandbox per `@Config`, so the singleton outlives a test class. */
    internal fun resetForTest() {
        synchronized(this) { instance = null }
    }
}
