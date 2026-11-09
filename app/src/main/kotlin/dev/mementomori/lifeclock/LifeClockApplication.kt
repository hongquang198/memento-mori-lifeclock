package dev.mementomori.lifeclock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.mementomori.lifeclock.widget.WidgetDependencies
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder
import javax.inject.Inject

/**
 * Process-wide setup.
 *
 * `@HiltAndroidApp` is what makes Hilt build the injector for this app: any class with
 * `@AndroidEntryPoint` or `@HiltWorker` gets its fields filled from here.
 *
 * The graph is built **once**, by Hilt, and published to [WidgetDependenciesHolder] because widget
 * receivers are created by the system and cannot be injected. That holder is the one legitimate
 * service locator in the project: it is the seam between "the system instantiates this class" and
 * "the rest of the app uses constructor injection". Everything that *can* be injected now is.
 */
@HiltAndroidApp
class LifeClockApplication : Application() {

    @Inject
    lateinit var dependencies: WidgetDependencies

    override fun onCreate() {
        super.onCreate()
        // Hilt injects Application fields before this returns, so `dependencies` is set here.
        WidgetDependenciesHolder.current = dependencies
    }
}
