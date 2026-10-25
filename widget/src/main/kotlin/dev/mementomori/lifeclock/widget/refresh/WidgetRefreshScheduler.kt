package dev.mementomori.lifeclock.widget.refresh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Wakes the widget up. Two mechanisms, because neither is sufficient alone:
 *
 *  - `WorkManager`, every [PERIODIC_MINUTES] (its floor), is the heartbeat. It survives reboots
 *    and Doze, but it is imprecise by design, so a countdown can flip up to a period late.
 *  - an inexact `setAndAllowWhileIdle` alarm on the next boundary is what makes it precise:
 *    midnight, the next hour, whichever the bound config asked for.
 *
 * No exact alarms: `SCHEDULE_EXACT_ALARM` is a restricted permission, and a widget that is a few
 * minutes late is still correct. Every entry point goes through `WorkManager` rather than doing
 * the render in the receiver - M0 measured that a receiver's `goAsync` budget loses the race on
 * a cold start.
 */
class WidgetRefreshScheduler(private val context: Context) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context.applicationContext)

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<CountdownRefreshWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_REASON to REASON_PERIODIC, KEY_FORCE to false))
            .build()
        // KEEP: re-arming on every boot would reset the period and delay the first tick. The
        // trade-off is that the *first* registration wins, input data included, until it is
        // cancelled - so changing this payload only affects devices that never had the work.
        workManager.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun refreshNow(reason: String, force: Boolean) {
        val request = OneTimeWorkRequestBuilder<CountdownRefreshWorker>()
            .setInputData(workDataOf(KEY_REASON to reason, KEY_FORCE to force))
            .build()
        workManager.enqueueUniqueWork(UNIQUE_IMMEDIATE, ExistingWorkPolicy.REPLACE, request)
    }

    /** Arms the next wakeup, or cancels it when [at] is null (nothing placed). */
    fun armBoundary(at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = boundaryIntent()
        if (at == null) {
            alarms.cancel(pending)
            return
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
    }

    fun stop() {
        workManager.cancelUniqueWork(UNIQUE_PERIODIC)
        workManager.cancelUniqueWork(UNIQUE_IMMEDIATE)
        context.getSystemService(AlarmManager::class.java).cancel(boundaryIntent())
    }

    private fun boundaryIntent(): PendingIntent {
        val intent = Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_BOUNDARY)
        // FLAG_IMMUTABLE is mandatory from API 31 and harmless below it.
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_BOUNDARY = "dev.mementomori.lifeclock.widget.BOUNDARY"

        /** WorkManager's minimum periodic interval. Lower values are silently raised to it. */
        const val PERIODIC_MINUTES = 15L

        internal const val UNIQUE_PERIODIC = "lifeclock-periodic-refresh"
        internal const val UNIQUE_IMMEDIATE = "lifeclock-immediate-refresh"
        private const val REASON_PERIODIC = "periodic"
        const val KEY_REASON = "reason"
        const val KEY_FORCE = "force"
    }
}
