package dev.mementomori.lifeclock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder

/**
 * Process-wide setup.
 *
 * `@HiltAndroidApp` is what makes Hilt build the injector for this app: any class with
 * `@AndroidEntryPoint` or `@HiltWorker` gets its fields filled from here.
 *
 * `onCreate` runs before every activity, service and broadcast receiver in this process, so it
 * is the first place the widget's dependencies can be published. Widget receivers get no
 * constructor arguments, so this holder is how they find the repositories and the clock.
 */
@HiltAndroidApp
class LifeClockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        WidgetDependenciesHolder.current = AppDependencies.from(this)
    }
}
