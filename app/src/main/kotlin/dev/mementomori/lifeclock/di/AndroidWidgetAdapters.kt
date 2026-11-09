package dev.mementomori.lifeclock.di

import android.content.Context
import dev.mementomori.lifeclock.contracts.WidgetPlacements
import dev.mementomori.lifeclock.contracts.WidgetRefresher
import dev.mementomori.lifeclock.widget.refresh.WidgetRefreshScheduler
import dev.mementomori.lifeclock.widget.refresh.placedWidgetIds

/*
 * The adapters. Everything platform-shaped that the ViewModel used to do lives here now.
 *
 * These are three lines each on purpose: an adapter's job is to translate, not to decide. The
 * decisions (when to redraw, which ids count as placed) already live in `:ui:widget` and are tested
 * there; this file only connects the port to them.
 */

/** Redraw through the same worker and alarm the scheduled path uses - never a direct Glance update. */
class AndroidWidgetRefresher(context: Context) : WidgetRefresher {
    private val appContext = context.applicationContext

    override fun redrawNow() {
        WidgetRefreshScheduler(appContext).refreshNow(reason = "app", force = true)
    }
}

/** Read placements from `AppWidgetManager`, not from Glance's cache (M0). */
class AndroidWidgetPlacements(context: Context) : WidgetPlacements {
    private val appContext = context.applicationContext

    override fun placed(): List<Int> = placedWidgetIds(appContext).toList()
}
