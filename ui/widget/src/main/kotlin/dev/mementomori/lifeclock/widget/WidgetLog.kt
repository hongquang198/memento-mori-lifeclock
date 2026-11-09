package dev.mementomori.lifeclock.widget

import android.util.Log

/** One tag for every refresh-path log, so `adb logcat -s LifeClockWidget` shows the whole story. */
internal object WidgetLog {
    private const val TAG = "LifeClockWidget"

    fun i(message: String) = Log.i(TAG, message)

    fun w(message: String, error: Throwable? = null) = Log.w(TAG, message, error)
}
