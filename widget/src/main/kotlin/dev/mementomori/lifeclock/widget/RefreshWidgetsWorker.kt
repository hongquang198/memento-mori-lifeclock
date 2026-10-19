package dev.mementomori.lifeclock.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * M0 fix verification: renders the widget from a context that has a **real execution budget**.
 *
 * Why this exists
 * ---------------
 * M0 measured that Glance 1.1.1 performs the whole composition inside the broadcast's
 * `goAsync` window, on a **cold-starting** process. On a boot that race is lost, the work is
 * cancelled, and `runAndLogExceptions` swallows the `CancellationException` silently - so the
 * widget stays on `initialLayout` with no error anywhere (see `docs/spikes/M0-findings.md` §6).
 *
 * A `WorkManager` worker is not a broadcast: it gets its own execution window, so a slow
 * process start is not fatal. This is the mechanism the framework's boot-time `onUpdate`
 * cannot provide on Glance 1.1.1 (which has no `shouldLaunchAsyncRequestWorker()`; verified
 * against the pinned AAR with `javap`).
 *
 * Scope note: this lives in `:widget` only because Glance is on `:widget`'s classpath and not
 * `:app`'s. In M2 the *policy* (when to refresh) moves to `:core:scheduler` behind an
 * interface, leaving Glance here. It is deliberately not abstracted yet (YAGNI) - the only
 * thing this build has to prove is that a boot refresh stops losing the race.
 */
class RefreshWidgetsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        spikeLog(context, "worker:start")
        return try {
            SpikeRingWidget().updateAll(context)
            spikeLog(context, "worker:updateAll-ok")
            Result.success()
        } catch (t: Throwable) {
            // Retry rather than give up: a boot refresh that fails once must self-heal.
            spikeLog(context, "worker:updateAll-failed ${t.javaClass.simpleName}")
            Result.retry()
        }
    }

    companion object {
        /** Unique work name, so a boot refresh cannot pile up duplicates. */
        const val UNIQUE_WORK_NAME = "refresh-widgets"
    }
}
