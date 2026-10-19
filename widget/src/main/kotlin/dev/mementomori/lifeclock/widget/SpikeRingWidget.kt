package dev.mementomori.lifeclock.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider

/**
 * M0 spike widget (T0.2 + T0.3).
 *
 * Demonstrates the two halves of the "coarse ring + live text" design:
 *  - the ring is our rasterized bitmap (static frame), and
 *  - the numeric countdown is a platform `Chronometer`, which ticks on its own without
 *    our code running and without any wakeups from us.
 *
 * `Chronometer` is a platform `View` with no Glance equivalent, so it is embedded as raw
 * `RemoteViews` via [AndroidRemoteViews] (requires API 31+; the Pixel 8a is well above that).
 */
class SpikeRingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // T0.4 instrumentation: every re-render passes through here, so this timestamp is the
        // ground truth for "how often did Android actually refresh me?".
        spikeLog(context, "provideGlance id=$id")
        val ring = renderSpikeRing(fraction = 0.73f)
        val countdown = buildCountdownRemoteViews(
            context = context,
            remainingMillis = 7L * 24 * 60 * 60 * 1000, // a 7-day example deadline
        )
        provideContent { SpikeContent(ring, countdown) }
    }
}

@Composable
private fun SpikeContent(ring: android.graphics.Bitmap, countdown: RemoteViews) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF101014)))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(provider = ImageProvider(ring), contentDescription = "spike determinate ring")
        AndroidRemoteViews(countdown)
    }
}

/** Builds the raw RemoteViews holding the self-ticking countdown `Chronometer`. */
private fun buildCountdownRemoteViews(context: Context, remainingMillis: Long): RemoteViews {
    val remoteViews = RemoteViews(context.packageName, R.layout.spike_widget_chronometer)
    // Chronometer counts DOWN to `base`, expressed in elapsedRealtime() milliseconds.
    // elapsedRealtime() includes deep sleep but resets on reboot, so this must be recomputed
    // on every render and never persisted.
    val base = SystemClock.elapsedRealtime() + remainingMillis
    remoteViews.setChronometerCountDown(R.id.spike_chronometer, true)
    remoteViews.setChronometer(R.id.spike_chronometer, base, null, true)
    return remoteViews
}

/** Registers the widget with the Android widget host. */
class SpikeRingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpikeRingWidget()

    /**
     * T0.4 instrumentation, and the probe that identifies *which* boot failure we have.
     *
     * Glance 1.1.1's own `onUpdate` is (decompiled from the pinned 1.1.1 AAR):
     *
     * ```
     * updateManager(context)                                     // touches the appwidget service
     * appWidgetIds.map { async { glanceAppWidget.update(context, it) } }.awaitAll()
     * ```
     *
     * So an **empty** `appWidgetIds` renders nothing at all: no `provideGlance`, no exception,
     * no log. Recording the array here is what tells an "empty ids" failure apart from a
     * "the broadcast's goAsync budget expired" failure, because those two need different fixes.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        spikeLog(context, "onUpdate ids=${appWidgetIds.toList()}")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

/** Shared logcat tag for all M0 spike instrumentation (see docs/spikes/M0-findings.md). */
internal const val TAG = "SPIKE"

/**
 * File, in the app's private storage, that durably records every measurement.
 *
 * logcat proved inadequate for post-reboot evidence: lines were missing even though the widget
 * demonstrably re-rendered. This file survives reboots, process death and logcat rotation, so it
 * is the authoritative record. Read it with:
 * `adb shell run-as dev.mementomori.lifeclock cat files/m0_render_log.txt`
 */
internal const val SPIKE_LOG = "m0_render_log.txt"

/**
 * Records [message] to both logcat and [SPIKE_LOG].
 *
 * Note `context.filesDir` is credential-encrypted storage: a write before the user unlocks the
 * device fails. That failure is deliberately swallowed *after* logging, because a failed
 * measurement write must never break the widget.
 */
internal fun spikeLog(context: Context, message: String) {
    val line = "$message wallMs=${System.currentTimeMillis()} elapsedMs=${SystemClock.elapsedRealtime()}\n"
    Log.i(TAG, line.trim())
    runCatching { File(context.filesDir, SPIKE_LOG).appendText(line) }
        .onFailure { Log.w(TAG, "spike-log write failed: ${it.javaClass.simpleName}") }
}
