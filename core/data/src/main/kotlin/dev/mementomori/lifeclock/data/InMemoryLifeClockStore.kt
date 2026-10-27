package dev.mementomori.lifeclock.data

import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetInstanceBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Same contract, no disk. Used by tests, previews and the config Activity before anything is saved.
 *
 * Deliberately shares [StoreRules] with the DataStore implementation so the guard cannot diverge
 * between "what tests prove" and "what ships".
 */
class InMemoryLifeClockStore : LifeClockStore {

    private val configState = MutableStateFlow<List<WidgetConfigRecord>>(emptyList())
    private val bindingState = MutableStateFlow<List<WidgetInstanceBinding>>(emptyList())
    private val profileState = MutableStateFlow<List<PersonProfile>>(emptyList())

    override val configs: WidgetConfigRepository =
        InMemoryWidgetConfigRepository(configState, bindingState)

    override val bindings: WidgetBindingRepository =
        InMemoryWidgetBindingRepository(configState, bindingState)

    override val profiles: ProfileRepository =
        InMemoryProfileRepository(profileState, configState)
}

private class InMemoryWidgetConfigRepository(
    private val configs: MutableStateFlow<List<WidgetConfigRecord>>,
    private val bindings: MutableStateFlow<List<WidgetInstanceBinding>>,
) : WidgetConfigRepository {

    override fun observe(configId: String): Flow<WidgetConfigRecord?> =
        observeAll().map { all -> all.firstOrNull { it.configId == configId } }

    override fun observeAll(): Flow<List<WidgetConfigRecord>> = configs

    override suspend fun save(config: WidgetConfigRecord) {
        configs.value = StoreRules.upsertConfig(configs.value, config)
    }

    override suspend fun delete(configId: String): DeleteConfigResult {
        if (configs.value.none { it.configId == configId }) return DeleteConfigResult.NotFound

        val bound = StoreRules.boundGlanceIds(bindings.value, configId)
        if (bound.isNotEmpty()) return DeleteConfigResult.BlockedByBindings(bound)

        configs.value = configs.value.filterNot { it.configId == configId }
        return DeleteConfigResult.Deleted
    }
}

private class InMemoryWidgetBindingRepository(
    private val configs: MutableStateFlow<List<WidgetConfigRecord>>,
    private val bindings: MutableStateFlow<List<WidgetInstanceBinding>>,
) : WidgetBindingRepository {

    override fun observeBinding(glanceId: String): Flow<WidgetInstanceBinding?> =
        observeAllBindings().map { all -> all.firstOrNull { it.glanceId == glanceId } }

    override fun observeAllBindings(): Flow<List<WidgetInstanceBinding>> = bindings

    override suspend fun bind(glanceId: String, configId: String) {
        require(configs.value.any { it.configId == configId }) {
            "Cannot bind $glanceId: unknown config $configId"
        }
        bindings.value = StoreRules.upsertBinding(
            bindings.value,
            WidgetInstanceBinding(glanceId, configId),
        )
    }

    override suspend fun unbind(glanceId: String) {
        bindings.value = bindings.value.filterNot { it.glanceId == glanceId }
    }
}

private class InMemoryProfileRepository(
    private val profiles: MutableStateFlow<List<PersonProfile>>,
    private val configs: MutableStateFlow<List<WidgetConfigRecord>>,
) : ProfileRepository {

    override fun observe(profileId: String): Flow<PersonProfile?> =
        observeAll().map { all -> all.firstOrNull { it.profileId == profileId } }

    override fun observeAll(): Flow<List<PersonProfile>> = profiles

    override suspend fun save(profile: PersonProfile) {
        profiles.value = StoreRules.upsertProfile(profiles.value, profile)
    }

    override suspend fun delete(profileId: String): DeleteProfileResult {
        if (profiles.value.none { it.profileId == profileId }) return DeleteProfileResult.NotFound

        val referenced = StoreRules.configsReferencing(configs.value, profileId)
        if (referenced.isNotEmpty()) return DeleteProfileResult.BlockedByConfigs(referenced)

        profiles.value = profiles.value.filterNot { it.profileId == profileId }
        return DeleteProfileResult.Deleted
    }
}
