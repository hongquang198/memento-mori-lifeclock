package dev.mementomori.lifeclock.widget.refresh

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.AppWidgetId
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import dev.mementomori.lifeclock.widget.CountdownWidget
import dev.mementomori.lifeclock.widget.CountdownWidgetReceiver
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder
import dev.mementomori.lifeclock.widget.WidgetLog

/**
 * The one thing that redraws widgets.
 *
 * It exists because a `BroadcastReceiver` only gets a short `goAsync` window, and M0 measured
 * that Glance 1.1.1 loses that race on a cold start: the composition is cancelled, the exception
 * is swallowed, and the widget stays on its placeholder forever. A worker gets its own budget.
 *
 * Everything enqueues this worker; nothing renders in a receiver.
 */
class CountdownRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dependencies = WidgetDependenciesHolder.current
        if (dependencies == null) {
            WidgetLog.w("refresh skipped: dependencies were never published")
            return Result.failure()
        }

        val reason = inputData.getString(WidgetRefreshScheduler.KEY_REASON) ?: "unspecified"
        val force = inputData.getBoolean(WidgetRefreshScheduler.KEY_FORCE, false)
        val gate = ProcessRefreshGate.of(dependencies)
        val scheduler = WidgetRefreshScheduler(applicationContext)

        return try {
            var updated = 0
            val decision = gate.tick(dependencies.clock.now(), force) {
                updated = updatePlacedWidgets(applicationContext)
            }
            scheduler.armBoundary(decision.nextAt)
            WidgetLog.i("refresh($reason): drew=${decision.drew} widgets=$updated next=${decision.nextAt}")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Not Result.failure(): the gate leaves the boundary REFRESHED, so the draw is owed.
            // failure() is terminal and the next attempt would be the 15 minute heartbeat.
            WidgetLog.w("refresh($reason) failed, retrying", error)
            Result.retry()
        }
    }
}

/**
 * Draws every placed instance, enumerated from the platform.
 *
 * Not `GlanceAppWidget.updateAll`: that reads Glance's own id registry, which is written by the
 * receiver's `onUpdate` - the callback M0 measured as losing its race on a cold start. On a Pixel
 * 8a (2026-10-25) the registry was empty, so `updateAll` updated zero widgets while the gate
 * reported `drew=true`, and the widget sat on its placeholder until the launcher happened to send
 * an update. The platform's list cannot go stale, so it is the source of truth.
 *
 * @return how many instances were redrawn, so the log can tell "drew nothing" from "drew fine".
 */
internal suspend fun updatePlacedWidgets(
    context: Context,
    // The seam exists so a test can assert "one draw per placed instance" without Glance's
    // composition, which Robolectric hands to a background session and cannot observe.
    draw: suspend (AppWidgetId) -> Unit = { CountdownWidget().update(context, it) },
): Int {
    val ids = placedWidgetIds(context)
    ids.forEach { draw(AppWidgetId(it)) }
    return ids.size
}

/** The platform's list of placed instances. Split out so it can be asserted without composing. */
internal fun placedWidgetIds(context: Context): IntArray =
    AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, CountdownWidgetReceiver::class.java))
