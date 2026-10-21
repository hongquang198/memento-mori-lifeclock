package dev.mementomori.lifeclock.widget

import dev.mementomori.lifeclock.data.WidgetBindingRepository
import dev.mementomori.lifeclock.data.WidgetConfigRepository
import dev.mementomori.lifeclock.time.Clock
import dev.mementomori.lifeclock.time.CountdownModel
import java.time.ZoneId

/**
 * What the widget layer needs from the rest of the app.
 *
 * This exists instead of Hilt annotations because the system instantiates the widget receiver:
 * `:app` builds the graph and publishes it through [WidgetDependenciesHolder], so `:widget` needs
 * neither Hilt nor an annotation processor, and tests construct this directly.
 */
interface WidgetDependencies {
    val configs: WidgetConfigRepository
    val bindings: WidgetBindingRepository
    val countdownModel: CountdownModel
    val clock: Clock

    /** Read per use, not cached: the user can change timezone while the process lives. */
    val zone: ZoneId
}

/**
 * Set once in `Application.onCreate`, before any receiver can run.
 *
 * Null means the app entry point never published the graph, which is a wiring bug rather than a
 * runtime condition, so the widget fails loudly in logcat instead of silently rendering nothing.
 */
object WidgetDependenciesHolder {
    @Volatile
    var current: WidgetDependencies? = null
}
