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
 * Builds the object graph the widget layer needs.
 *
 * Deliberately hand-wired, not Hilt: the widget receiver is instantiated by the system, so the
 * graph is published once through `WidgetDependenciesHolder`. When the config UI lands this
 * becomes an `@EntryPoint`, and nothing in `:widget` has to change.
 */
object LifeClockGraph {

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
