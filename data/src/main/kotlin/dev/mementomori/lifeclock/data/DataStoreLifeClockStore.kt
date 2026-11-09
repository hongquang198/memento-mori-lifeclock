package dev.mementomori.lifeclock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetInstanceBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Production storage: one preferences file holding three JSON lists. */
class DataStoreLifeClockStore(dataStore: DataStore<Preferences>) : LifeClockStore {
    override val configs: WidgetConfigRepository = DataStoreWidgetConfigRepository(dataStore)
    override val bindings: WidgetBindingRepository = DataStoreWidgetBindingRepository(dataStore)
    override val profiles: ProfileRepository = DataStoreProfileRepository(dataStore)
}

class DataStoreWidgetConfigRepository(
    private val dataStore: DataStore<Preferences>,
) : WidgetConfigRepository {

    override fun observe(configId: String): Flow<WidgetConfigRecord?> =
        observeAll().map { all -> all.firstOrNull { it.configId == configId } }

    override fun observeAll(): Flow<List<WidgetConfigRecord>> =
        dataStore.data.map { it.configs() }

    override suspend fun save(config: WidgetConfigRecord) {
        dataStore.edit { prefs ->
            prefs[StoreKeys.CONFIGS] =
                StoreCodec.encodeConfigs(StoreRules.upsertConfig(prefs.configs(), config))
        }
    }

    override suspend fun delete(configId: String): DeleteConfigResult {
        var result: DeleteConfigResult = DeleteConfigResult.NotFound
        dataStore.edit { prefs ->
            val current = prefs.configs()
            if (current.none { it.configId == configId }) return@edit

            val bound = StoreRules.boundGlanceIds(prefs.bindings(), configId)
            if (bound.isNotEmpty()) {
                result = DeleteConfigResult.BlockedByBindings(bound)
                return@edit
            }

            prefs[StoreKeys.CONFIGS] =
                StoreCodec.encodeConfigs(current.filterNot { it.configId == configId })
            result = DeleteConfigResult.Deleted
        }
        return result
    }
}

class DataStoreWidgetBindingRepository(
    private val dataStore: DataStore<Preferences>,
) : WidgetBindingRepository {

    override fun observeBinding(glanceId: String): Flow<WidgetInstanceBinding?> =
        observeAllBindings().map { all -> all.firstOrNull { it.glanceId == glanceId } }

    override fun observeAllBindings(): Flow<List<WidgetInstanceBinding>> =
        dataStore.data.map { it.bindings() }

    override suspend fun bind(glanceId: String, configId: String) {
        dataStore.edit { prefs ->
            // Referential integrity: a dangling binding would render nothing and be invisible.
            require(prefs.configs().any { it.configId == configId }) {
                "Cannot bind $glanceId: unknown config $configId"
            }
            prefs[StoreKeys.BINDINGS] = StoreCodec.encodeBindings(
                StoreRules.upsertBinding(prefs.bindings(), WidgetInstanceBinding(glanceId, configId)),
            )
        }
    }

    override suspend fun unbind(glanceId: String) {
        dataStore.edit { prefs ->
            prefs[StoreKeys.BINDINGS] = StoreCodec.encodeBindings(
                prefs.bindings().filterNot { it.glanceId == glanceId },
            )
        }
    }
}

class DataStoreProfileRepository(
    private val dataStore: DataStore<Preferences>,
) : ProfileRepository {

    override fun observe(profileId: String): Flow<PersonProfile?> =
        observeAll().map { all -> all.firstOrNull { it.profileId == profileId } }

    override fun observeAll(): Flow<List<PersonProfile>> =
        dataStore.data.map { it.profiles() }

    override suspend fun save(profile: PersonProfile) {
        dataStore.edit { prefs ->
            prefs[StoreKeys.PROFILES] =
                StoreCodec.encodeProfiles(StoreRules.upsertProfile(prefs.profiles(), profile))
        }
    }

    override suspend fun delete(profileId: String): DeleteProfileResult {
        var result: DeleteProfileResult = DeleteProfileResult.NotFound
        dataStore.edit { prefs ->
            val current = prefs.profiles()
            if (current.none { it.profileId == profileId }) return@edit

            // Deleting it would leave those configs computing from nothing.
            val referenced = StoreRules.configsReferencing(prefs.configs(), profileId)
            if (referenced.isNotEmpty()) {
                result = DeleteProfileResult.BlockedByConfigs(referenced)
                return@edit
            }

            prefs[StoreKeys.PROFILES] =
                StoreCodec.encodeProfiles(current.filterNot { it.profileId == profileId })
            result = DeleteProfileResult.Deleted
        }
        return result
    }
}
