package dev.mementomori.lifeclock.contracts

/*
 * The two things the app needs from the *platform* but must not implement itself.
 *
 * A port is a dependency inversion: the interface is declared here, in the layer that needs the
 * capability, and implemented by the layer that has it (`:app/di`, over `:ui:widget`). The
 * ViewModel therefore never imports `AppWidgetManager`, `AlarmManager` or WorkManager - which is
 * what makes it a plain JVM object you can unit test.
 */

/** Redraws every placed widget through the real refresh path (worker + alarm), never directly. */
interface WidgetRefresher {
    fun redrawNow()
}

/**
 * The placed instances, read from the platform.
 *
 * Deliberately not Glance's registry: a stale Glance cache was the M0 bug, so the source of truth
 * for "what is on the home screen" is `AppWidgetManager`, and this port is where that lives.
 */
interface WidgetPlacements {
    fun placed(): List<Int>
}

/** One placed instance, resolved for the "Your widgets" list. A read model, not a domain entity. */
data class PlacedWidget(
    val appWidgetId: Int,
    val configId: String?,
    val configName: String?,
)
