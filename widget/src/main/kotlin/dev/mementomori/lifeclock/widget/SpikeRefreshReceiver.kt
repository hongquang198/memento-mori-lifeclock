package dev.mementomori.lifeclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * M0 spike (T0.4) — the *production refresh path* in miniature: our own code asks Glance to
 * re-render every instance of a widget.
 *
 * This has to live in `:widget` (not `:app`) because re-rendering needs the Glance types, and
 * `:widget` deliberately does not leak Glance onto the app's compile classpath — the app talks
 * to widgets through the module boundary, not through Glance directly.
 *
 * Why a custom action instead of the real thing: `android.appwidget.action.APPWIDGET_UPDATE`
 * is a protected broadcast; a normal app (and adb) may not send it, so the spike exposes its
 * own action purely so the measurement can be driven from a shell.
 *
 * Throwaway code; delete before M2. Drive it with:
 * ```
 *   adb shell am broadcast -n dev.mementomori.lifeclock/.widget.SpikeRefreshReceiver \
 *       -a dev.mementomori.lifeclock.spike.REFRESH
 * ```
 */
class SpikeRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                SpikeRingWidget().updateAll(context)
                Log.i(TAG, "REFRESH ok wallMs=${System.currentTimeMillis()}")
            } catch (t: Throwable) {
                Log.w(TAG, "REFRESH failed: $t")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
