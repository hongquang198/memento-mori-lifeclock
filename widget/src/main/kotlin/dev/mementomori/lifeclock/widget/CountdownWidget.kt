package dev.mementomori.lifeclock.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import dev.mementomori.lifeclock.widget.ring.CachedRingRenderer
import dev.mementomori.lifeclock.widget.refresh.UnbindWidgetsWorker
import dev.mementomori.lifeclock.widget.content.WidgetContent
import dev.mementomori.lifeclock.widget.refresh.WidgetRefreshScheduler
import dev.mementomori.lifeclock.widget.render.WidgetStateLoader
import dev.mementomori.lifeclock.widget.render.appWidgetWidthDp
import dev.mementomori.lifeclock.widget.config.configureIntent

/**
 * The countdown widget.
 *
 * A placed widget owns no settings: it renders whatever config it is bound to, and the config
 * Activity is what changes that binding.
 */
class CountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dependencies = WidgetDependenciesHolder.current
            ?: error("WidgetDependenciesHolder.current must be set in Application.onCreate")
        val appWidgetId = (id as? AppWidgetId)?.appWidgetId
        val state = WidgetStateLoader(dependencies, processRingRenderer, context)
            .load(id.bindingKey(), appWidgetId?.let { appWidgetWidthDp(context, it) })

        // Tapping the widget must reach the picker. The launcher's own "reconfigure" affordance is
        // not dependable (measured on a Pixel 8a: an unbound widget only offers "Remove"), so the
        // widget carries its own action - the same intent the launcher uses at placement.
        val tap = appWidgetId?.let {
            GlanceModifier.clickable(actionStartActivity(configureIntent(context, it)))
        } ?: GlanceModifier

        provideContent { WidgetContent(state, tap) }
    }
}

/**
 * One cache for the process, not one per [CountdownWidget].
 *
 * The system, and our worker, each create a new widget instance per update, so a cache owned by
 * the instance would be thrown away before it ever hit.
 */
private val processRingRenderer = CachedRingRenderer()

/**
 * The one canonical way a placed widget is named in storage.
 *
 * The binding repository stores strings, so the config Activity must use this same function or
 * widget and settings would disagree about which instance they mean.
 */
fun GlanceId.bindingKey(): String = when (this) {
    is AppWidgetId -> appWidgetId.toString()
    else -> toString()
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()

    /** The first instance was placed: start the heartbeat and draw it once. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler(context).apply {
            ensurePeriodic()
            refreshNow(reason = "onEnabled", force = true)
        }
    }

    /** The last instance was removed: nothing left to wake up for. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler(context).stop()
    }

    /** Storage is a DataStore, so the write goes to a worker rather than the main thread. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        UnbindWidgetsWorker.enqueue(context, appWidgetIds)
    }
}
