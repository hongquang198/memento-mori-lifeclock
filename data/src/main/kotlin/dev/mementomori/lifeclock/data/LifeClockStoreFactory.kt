package dev.mementomori.lifeclock.data


import dev.mementomori.lifeclock.contracts.DeleteConfigResult
import dev.mementomori.lifeclock.contracts.DeleteProfileResult
import dev.mementomori.lifeclock.contracts.WidgetConfigRepository
import dev.mementomori.lifeclock.contracts.WidgetBindingRepository
import dev.mementomori.lifeclock.contracts.ProfileRepository
import dev.mementomori.lifeclock.contracts.LifeClockStore
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * The one DataStore file behind [LifeClockStore].
 *
 * A `preferencesDataStore` delegate is deliberately a top-level property: it keeps a single
 * DataStore instance per process. Two instances over the same file corrupt each other, so never
 * build the store with a second delegate.
 */
private val Context.lifeClockDataStore by preferencesDataStore(name = "lifeclock")

object LifeClockStoreFactory {

    /** Cheap to call: the delegate hands back the same DataStore every time. */
    fun create(context: Context): LifeClockStore =
        DataStoreLifeClockStore(context.applicationContext.lifeClockDataStore)
}
