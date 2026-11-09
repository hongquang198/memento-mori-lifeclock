package dev.mementomori.lifeclock

import android.content.Context
import dev.mementomori.lifeclock.data.LifeClockStoreFactory
import dev.mementomori.lifeclock.contracts.ProfileRepository
import dev.mementomori.lifeclock.contracts.WidgetBindingRepository
import dev.mementomori.lifeclock.contracts.WidgetConfigRepository
import dev.mementomori.lifeclock.mortality.CsvLifeTableSource
import dev.mementomori.lifeclock.mortality.LifeStatsEngine
import dev.mementomori.lifeclock.time.Clock
import dev.mementomori.lifeclock.time.CountdownModel
import dev.mementomori.lifeclock.time.DefaultCountdownModel
import dev.mementomori.lifeclock.widget.WidgetDependencies
import java.time.ZoneId

/**
 * Builds the objects the widget layer needs.
 *
 * Still hand-written, and still the only place that names a concrete storage engine. `AppModule`
 * calls it from a `@Provides` method, so Hilt owns the lifetime (`@Singleton`) and every consumer -
 * the ViewModel, the use cases, and the receivers through `WidgetDependenciesHolder` - receives the
 * same instance. Replacing DataStore with something else is a change to this function and nothing
 * above it.
 */
object AppDependencies {

    fun from(context: Context): WidgetDependencies {
        val store = LifeClockStoreFactory.create(context.applicationContext)
        return object : WidgetDependencies {
            override val configs: WidgetConfigRepository = store.configs
            override val bindings: WidgetBindingRepository = store.bindings
            override val profiles: ProfileRepository = store.profiles
            override val countdownModel: CountdownModel = DefaultCountdownModel()

            // Parsed once per process: 10k rows is cheap, but re-reading it per draw is not.
            override val lifeStats: LifeStatsEngine = LifeStatsEngine(CsvLifeTableSource.bundled())
            override val clock: Clock = Clock.systemUTC()
            override val zone: ZoneId get() = ZoneId.systemDefault()
        }
    }
}
