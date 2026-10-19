package dev.mementomori.lifeclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * M0 fix verification, part 1 of 2: the boot trigger.
 *
 * This receiver deliberately **does no rendering**. It only enqueues [RefreshWidgetsWorker],
 * because a `BroadcastReceiver` runs on the same short `goAsync` budget that loses the race on a
 * cold start. Doing the render here would reproduce the exact bug we are fixing - it would look
 * correct in review and fail intermittently on a real device.
 *
 * `BOOT_COMPLETED` (as opposed to `LOCKED_BOOT_COMPLETED`) is delivered **after the user is
 * unlocked**, so credential-encrypted storage is available and `WorkManager` can run. This
 * receiver is therefore *not* `directBootAware`.
 *
 * Requires `android.permission.RECEIVE_BOOT_COMPLETED`. `BOOT_COMPLETED` is exempt from the
 * API 26+ ban on implicit broadcasts reaching manifest receivers, so registering it in the
 * manifest is correct (unlike our own custom actions - see M0-findings.md §7).
 */
class BootRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        spikeLog(context, "boot:enqueue")
        WorkManager.getInstance(context).enqueueUniqueWork(
            RefreshWidgetsWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWidgetsWorker>().build(),
        )
    }
}
