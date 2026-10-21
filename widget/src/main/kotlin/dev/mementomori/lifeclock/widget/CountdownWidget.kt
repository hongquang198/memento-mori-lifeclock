package dev.mementomori.lifeclock.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import dev.mementomori.lifeclock.widget.ring.CachedRingRenderer

/**
 * The countdown widget.
 *
 * A placed widget owns no settings: it renders whatever config it is bound to, and the config
 * Activity is what changes that binding.
 */
class CountdownWidget : GlanceAppWidget() {

    // One cache per process, so a refresh reuses frames from the previous render.
    private val renderer = CachedRingRenderer()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dependencies = WidgetDependenciesHolder.current
            ?: error("WidgetDependenciesHolder.current must be set in Application.onCreate")
        val state = WidgetStateLoader(dependencies, renderer, context).load(id.bindingKey())
        provideContent { WidgetContent(state) }
    }
}

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
}
