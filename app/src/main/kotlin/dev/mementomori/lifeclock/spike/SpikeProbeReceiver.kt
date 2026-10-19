package dev.mementomori.lifeclock.spike

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * M0 spike (T0.4) — measures how Android actually delivers *scheduled* work while the device
 * is dozing, instead of trusting the documented numbers.
 *
 * Throwaway code: it exists only to produce the measurements recorded in
 * `docs/spikes/M0-findings.md`, and must be deleted before any production milestone.
 *
 * How to drive it (device connected):
 * ```
 *   adb shell am broadcast -a dev.mementomori.lifeclock.spike.SCHEDULE
 *   adb shell dumpsys deviceidle force-idle        # enter Doze immediately
 *   adb logcat -s SPIKE                            # watch which alarm actually fires
 *   adb shell dumpsys deviceidle unforce           # leave Doze
 * ```
 *
 * Why two alarms: `setAndAllowWhileIdle` is inexact and the system may defer it during Doze,
 * while `setExactAndAllowWhileIdle` is exact but needs a special permission on API 31+.
 * The whole "coarse ring + live text" design hinges on which of these we are actually allowed
 * to use, so we measure before committing.
 */
class SpikeProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SCHEDULE -> schedule(context)
            ACTION_INEXACT, ACTION_EXACT -> logFired(intent)
            else -> Log.w(TAG, "ignoring action=${intent.action}")
        }
    }

    private fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        // On API 31+ exact alarms need SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM. `USE_EXACT_ALARM`
        // is reserved by Play for alarm-clock/calendar apps, so a widget like ours is not
        // entitled to it — recording that fact is half the point of this probe.
        val exactAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        Log.i(TAG, "SCHEDULE exactAlarmsAllowed=$exactAllowed sdk=${Build.VERSION.SDK_INT} wallMs=${System.currentTimeMillis()}")

        arm(context, requestCode = RC_INEXACT, exact = false)
        arm(context, requestCode = RC_EXACT, exact = true)
    }

    private fun arm(context: Context, requestCode: Int, exact: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = SystemClock.elapsedRealtime() + PROBE_DELAY_MS
        val pendingIntent = probePendingIntent(context, requestCode, exact)
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
            Log.i(TAG, "ARMED exact=$exact delayMs=$PROBE_DELAY_MS elapsedMs=${SystemClock.elapsedRealtime()}")
        } catch (e: SecurityException) {
            // Expected outcome on API 31+ when the exact-alarm permission is not granted.
            Log.w(TAG, "ARM-FAILED exact=$exact reason=${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun probePendingIntent(context: Context, requestCode: Int, exact: Boolean): PendingIntent {
        val intent = Intent(context, SpikeProbeReceiver::class.java).apply {
            action = if (exact) ACTION_EXACT else ACTION_INEXACT
            putExtra(EXTRA_ARMED_AT_ELAPSED, SystemClock.elapsedRealtime())
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun logFired(intent: Intent) {
        val armedAt = intent.getLongExtra(EXTRA_ARMED_AT_ELAPSED, 0L)
        val latenessMs = if (armedAt > 0L) SystemClock.elapsedRealtime() - armedAt - PROBE_DELAY_MS else -1L
        val label = if (intent.action == ACTION_EXACT) "EXACT" else "INEXACT"
        Log.i(
            TAG,
            "$label-FIRED latenessMs=$latenessMs wallMs=${System.currentTimeMillis()} elapsedMs=${SystemClock.elapsedRealtime()}",
        )
    }

    companion object {
        private const val TAG = "SPIKE"
        private const val PROBE_DELAY_MS = 120_000L
        private const val RC_INEXACT = 1001
        private const val RC_EXACT = 1002
        private const val EXTRA_ARMED_AT_ELAPSED = "armedAtElapsed"

        const val ACTION_SCHEDULE = "dev.mementomori.lifeclock.spike.SCHEDULE"
        const val ACTION_INEXACT = "dev.mementomori.lifeclock.spike.INEXACT"
        const val ACTION_EXACT = "dev.mementomori.lifeclock.spike.EXACT"
    }
}
