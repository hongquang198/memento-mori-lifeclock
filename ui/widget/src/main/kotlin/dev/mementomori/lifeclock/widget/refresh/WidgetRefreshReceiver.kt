package dev.mementomori.lifeclock.widget.refresh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Everything that means "the widget may now be wrong".
 *
 * It **never renders**. It only enqueues [CountdownRefreshWorker], because a receiver that draws
 * is exactly the M0 bug: a cold-started process loses the `goAsync` race and the frame is never
 * drawn. Enqueueing is cheap and survives the process being killed right after.
 *
 * All of these actions are exempt from the implicit-broadcast ban on API 26+, which is why they
 * can still be declared in the manifest. Not `directBootAware`: they run after unlock, when
 * credential-encrypted storage (and WorkManager) is available.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val scheduler = WidgetRefreshScheduler(context)
        if (action in REINSTALL_ACTIONS) scheduler.ensurePeriodic()
        scheduler.refreshNow(reason = action, force = action in FORCED_ACTIONS)
    }

    private companion object {
        val ACTION_TIME_CHANGED = Intent.ACTION_TIME_CHANGED
        val ACTION_TIMEZONE_CHANGED = Intent.ACTION_TIMEZONE_CHANGED
        val ACTION_DATE_CHANGED = Intent.ACTION_DATE_CHANGED
        val ACTION_MY_PACKAGE_REPLACED = Intent.ACTION_MY_PACKAGE_REPLACED

        val REINSTALL_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, ACTION_MY_PACKAGE_REPLACED)

        /** The clock or the calendar jumped, so the last render says nothing about the next one. */
        val FORCED_ACTIONS = REINSTALL_ACTIONS + setOf(ACTION_TIME_CHANGED, ACTION_TIMEZONE_CHANGED, ACTION_DATE_CHANGED)

        val HANDLED_ACTIONS = FORCED_ACTIONS + WidgetRefreshScheduler.ACTION_BOUNDARY
    }
}
