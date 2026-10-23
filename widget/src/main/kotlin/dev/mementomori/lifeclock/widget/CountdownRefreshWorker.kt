package dev.mementomori.lifeclock.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

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
            val decision = gate.tick(dependencies.clock.now(), force) {
                CountdownWidget().updateAll(applicationContext)
            }
            scheduler.armBoundary(decision.nextAt)
            WidgetLog.i("refresh($reason): drew=${decision.drew} next=${decision.nextAt}")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            WidgetLog.w("refresh($reason) failed", error)
            Result.failure()
        }
    }
}
