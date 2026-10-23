package dev.mementomori.lifeclock.scheduler

import dev.mementomori.lifeclock.model.RefreshIntent
import java.time.Instant

/** Where a widget instance is in its refresh cycle. Derived from the clock; never persisted. */
enum class WidgetLifecycle { IDLE, SCHEDULED, REFRESHED }

/**
 * One widget instance's cycle: no widgets placed ([WidgetLifecycle.IDLE]), waiting for the next
 * boundary ([WidgetLifecycle.SCHEDULED]), or a redraw is owed right now
 * ([WidgetLifecycle.REFRESHED]).
 */
data class RefreshState(
    val lifecycle: WidgetLifecycle,
    val nextRefreshAt: Instant?,
) {
    companion object {
        val Idle = RefreshState(WidgetLifecycle.IDLE, null)
    }
}

/**
 * The transitions between [WidgetLifecycle] values.
 *
 * `IDLE --onEnabled--> SCHEDULED --onTick (boundary reached)--> REFRESHED --onSettled--> SCHEDULED`
 * and anything `--onDisabled--> IDLE`.
 *
 * Kept separate from WorkManager so the rules are testable without a scheduler, a device or a
 * real clock: every function takes `now`.
 */
class RefreshCycle(private val boundaries: RefreshBoundaries) {

    fun onEnabled(now: Instant, intent: RefreshIntent): RefreshState =
        RefreshState(WidgetLifecycle.SCHEDULED, boundaries.nextBoundary(now, intent))

    /** Advances the cycle. A non-[WidgetLifecycle.IDLE] state whose boundary has passed becomes REFRESHED. */
    fun onTick(state: RefreshState, now: Instant, intent: RefreshIntent): RefreshState = when {
        state.lifecycle == WidgetLifecycle.IDLE -> state
        state.nextRefreshAt != null && now.isBefore(state.nextRefreshAt) -> state
        else -> RefreshState(WidgetLifecycle.REFRESHED, boundaries.nextBoundary(now, intent))
    }

    /** Called once the widget has actually been redrawn (or failed to be). */
    fun onSettled(state: RefreshState): RefreshState =
        if (state.lifecycle == WidgetLifecycle.REFRESHED) {
            state.copy(lifecycle = WidgetLifecycle.SCHEDULED)
        } else {
            state
        }

    fun onDisabled(): RefreshState = RefreshState.Idle
}
