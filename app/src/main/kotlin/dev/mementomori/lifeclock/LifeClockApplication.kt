package dev.mementomori.lifeclock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder

/**
 * The application entry point.
 *
 * `@HiltAndroidApp` tells Hilt to generate the dependency-injection container that lives
 * for the whole process. Everything injected anywhere in the app (view models, workers)
 * ultimately comes from here.
 *
 * The widget cannot be constructor-injected: the system creates its receiver. So the graph it
 * needs is published before any receiver can run, which is what `onCreate` guarantees.
 */
@HiltAndroidApp
class LifeClockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        WidgetDependenciesHolder.current = LifeClockGraph.from(this)
    }
}
