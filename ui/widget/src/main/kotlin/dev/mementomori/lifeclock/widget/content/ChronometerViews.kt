package dev.mementomori.lifeclock.widget.content

import dev.mementomori.lifeclock.widget.R

import android.content.Context
import android.widget.RemoteViews

/**
 * The live numeric readout.
 *
 * Glance has no Chronometer, and a platform `Chronometer` ticks itself with no code and no
 * wakeups from us, so it is embedded as raw RemoteViews.
 */
object ChronometerViews {

    /** `Chronometer` counts down to `base` in the elapsedRealtime clock. */
    fun baseFor(nowElapsedRealtime: Long, remainingMillis: Long): Long =
        nowElapsedRealtime + remainingMillis

    /**
     * Nesting a RemoteViews inside another is only verified from API 31 up; below that the widget
     * degrades to ring + label rather than risking a crash on every update.
     */
    fun canEmbed(sdkInt: Int): Boolean = sdkInt >= 31

    fun build(context: Context, base: Long): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_countdown_chronometer).apply {
            setChronometerCountDown(R.id.widget_chronometer, true)
            setChronometer(R.id.widget_chronometer, base, null, true)
        }
}
