package dev.mementomori.lifeclock.widget.refresh

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder
import dev.mementomori.lifeclock.widget.WidgetLog

/**
 * Drops the bindings of widgets the user removed.
 *
 * `onDeleted` is not suspend and storage is a `DataStore`, so the write is moved to a worker for
 * the same reason every other write is: a receiver must not hold the main thread.
 */
class UnbindWidgetsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bindings = WidgetDependenciesHolder.current?.bindings ?: return Result.failure()
        val ids = inputData.getIntArray(KEY_IDS) ?: return Result.success()
        return try {
            ids.forEach { bindings.unbind(it.toString()) }
            WidgetLog.i("unbound ${ids.size} widget(s)")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            WidgetLog.w("unbind failed", error)
            Result.failure()
        }
    }

    companion object {
        private const val KEY_IDS = "appWidgetIds"

        fun enqueue(context: Context, appWidgetIds: IntArray) {
            if (appWidgetIds.isEmpty()) return
            val request = OneTimeWorkRequestBuilder<UnbindWidgetsWorker>()
                .setInputData(workDataOf(KEY_IDS to appWidgetIds))
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("lifeclock-unbind", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
