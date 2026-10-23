package dev.mementomori.lifeclock

import android.content.Context
import dev.mementomori.lifeclock.data.LifeClockStoreFactory
import dev.mementomori.lifeclock.data.WidgetBindingRepository
import dev.mementomori.lifeclock.data.WidgetConfigRepository
import dev.mementomori.lifeclock.time.Clock
import dev.mementomori.lifeclock.time.CountdownModel
import dev.mementomori.lifeclock.time.DefaultCountdownModel
import dev.mementomori.lifeclock.widget.WidgetDependencies
import java.time.ZoneId

/**
 * Builds the objects the widget layer needs.
 *
 * Hand-written on purpose. The system creates the widget receiver itself, so nothing can be
 * passed into its constructor; the objects are parked in `WidgetDependenciesHolder` instead.
 * When the config screen lands this becomes a Hilt `@EntryPoint`, and `:widget` does not change.
 */
object AppDependencies {

    fun from(context: Context): WidgetDependencies {
        val store = LifeClockStoreFactory.create(context.applicationContext)
        return object : WidgetDependencies {
            override val configs: WidgetConfigRepository = store.configs
            override val bindings: WidgetBindingRepository = store.bindings
            override val countdownModel: CountdownModel = DefaultCountdownModel()
            override val clock: Clock = Clock.systemUTC()
            override val zone: ZoneId get() = ZoneId.systemDefault()
        }
    }
}
