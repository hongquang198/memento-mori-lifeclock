package dev.mementomori.lifeclock.usecases

import dev.mementomori.lifeclock.contracts.DeleteConfigResult
import dev.mementomori.lifeclock.contracts.DeleteProfileResult
import dev.mementomori.lifeclock.contracts.LifeClockStore
import dev.mementomori.lifeclock.contracts.ProfileRepository
import dev.mementomori.lifeclock.contracts.WidgetBindingRepository
import dev.mementomori.lifeclock.contracts.WidgetConfigRepository
import dev.mementomori.lifeclock.contracts.WidgetPlacements
import dev.mementomori.lifeclock.contracts.WidgetRefresher
import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetInstanceBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A store that keeps everything in memory.
 *
 * The point of the fakes is not to re-test storage (the contract test in `:data` does that) but to
 * show what the use cases actually depend on: three interfaces, no DataStore, no Android, no
 * coroutine scheduler.
 */
class FakeStore(
    configs: List<WidgetConfigRecord> = emptyList(),
    bindings: List<WidgetInstanceBinding> = emptyList(),
    profiles: List<PersonProfile> = emptyList(),
) : LifeClockStore {

    private val configState = MutableStateFlow(configs)
    private val bindingState = MutableStateFlow(bindings)
    private val profileState = MutableStateFlow(profiles)

    override val configs: WidgetConfigRepository = object : WidgetConfigRepository {
        override fun observe(configId: String) =
            configState.map { all -> all.firstOrNull { it.configId == configId } }

        override fun observeAll() = configState

        override suspend fun save(config: WidgetConfigRecord) {
            configState.update { all -> all.filterNot { it.configId == config.configId } + config }
        }

        override suspend fun delete(configId: String): DeleteConfigResult {
            if (configState.value.none { it.configId == configId }) return DeleteConfigResult.NotFound
            val bound = bindingState.value.filter { it.configId == configId }.map { it.glanceId }
            if (bound.isNotEmpty()) return DeleteConfigResult.BlockedByBindings(bound)
            configState.update { all -> all.filterNot { it.configId == configId } }
            return DeleteConfigResult.Deleted
        }
    }

    override val bindings: WidgetBindingRepository = object : WidgetBindingRepository {
        override fun observeBinding(glanceId: String) =
            bindingState.map { all -> all.firstOrNull { it.glanceId == glanceId } }

        override fun observeAllBindings() = bindingState

        override suspend fun bind(glanceId: String, configId: String) {
            bindingState.update { all ->
                all.filterNot { it.glanceId == glanceId } + WidgetInstanceBinding(glanceId, configId)
            }
        }

        override suspend fun unbind(glanceId: String) {
            bindingState.update { all -> all.filterNot { it.glanceId == glanceId } }
        }
    }

    override val profiles: ProfileRepository = object : ProfileRepository {
        override fun observe(profileId: String) =
            profileState.map { all -> all.firstOrNull { it.profileId == profileId } }

        override fun observeAll() = profileState

        override suspend fun save(profile: PersonProfile) {
            profileState.update { all -> all.filterNot { it.profileId == profile.profileId } + profile }
        }

        override suspend fun delete(profileId: String): DeleteProfileResult {
            if (profileState.value.none { it.profileId == profileId }) return DeleteProfileResult.NotFound
            val referenced = configState.value.filter { it.profileRef == profileId }.map { it.configId }
            if (referenced.isNotEmpty()) return DeleteProfileResult.BlockedByConfigs(referenced)
            profileState.update { all -> all.filterNot { it.profileId == profileId } }
            return DeleteProfileResult.Deleted
        }
    }
}

/** Counts redraws so a test can assert the widget was actually told. */
class RecordingRefresher : WidgetRefresher {
    var redraws: Int = 0
        private set

    override fun redrawNow() {
        redraws++
    }
}

/** The platform's answer to "which ids are placed". */
class FakePlacements(private vararg val ids: Int) : WidgetPlacements {
    override fun placed(): List<Int> = ids.toList()
}
