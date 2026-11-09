package dev.mementomori.lifeclock.contracts

import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetInstanceBinding
import kotlinx.coroutines.flow.Flow

/*
 * These interfaces used to live in `:data` next to the DataStore implementation. They moved here
 * because of what they actually are: ports. A use case has to name a repository to do its job, and
 * a use case may not depend on the storage engine. Interfaces in the data layer force exactly that
 * dependency; interfaces in the domain invert it.
 */

/** Outcome of a delete request. A boolean would lose the reason, and the UI must show it. */
sealed interface DeleteConfigResult {
    data object Deleted : DeleteConfigResult

    /** Refused: these placed widgets still render it. */
    data class BlockedByBindings(val glanceIds: List<String>) : DeleteConfigResult

    data object NotFound : DeleteConfigResult
}

/** The catalog of named, reusable configs. Holds no placement knowledge. */
interface WidgetConfigRepository {
    fun observe(configId: String): Flow<WidgetConfigRecord?>
    fun observeAll(): Flow<List<WidgetConfigRecord>>

    /** Insert, or replace in place (catalog order is stable). */
    suspend fun save(config: WidgetConfigRecord)

    /** Refuses while any binding points at [configId]. */
    suspend fun delete(configId: String): DeleteConfigResult
}

/** Placement -> config. A binding is the only per-instance state a widget has. */
interface WidgetBindingRepository {
    fun observeBinding(glanceId: String): Flow<WidgetInstanceBinding?>
    fun observeAllBindings(): Flow<List<WidgetInstanceBinding>>

    /** One row per [glanceId], so rebinding moves the instance rather than adding a second row. */
    suspend fun bind(glanceId: String, configId: String)

    suspend fun unbind(glanceId: String)
}

/** Both repositories over one storage file, so a delete can see the bindings it must respect. */
interface LifeClockStore {
    val configs: WidgetConfigRepository
    val bindings: WidgetBindingRepository
    val profiles: ProfileRepository
}

/** Outcome of deleting a profile. Mirrors [DeleteConfigResult]: the UI must be able to say why. */
sealed interface DeleteProfileResult {
    data object Deleted : DeleteProfileResult

    /** Refused: these configs still compute from it. */
    data class BlockedByConfigs(val configIds: List<String>) : DeleteProfileResult

    data object NotFound : DeleteProfileResult
}

/** Who a life clock is for. A config points at a profile; the profile owns no widget state. */
interface ProfileRepository {
    fun observe(profileId: String): Flow<PersonProfile?>
    fun observeAll(): Flow<List<PersonProfile>>

    suspend fun save(profile: PersonProfile)

    /** Refuses while any config still references [profileId]. */
    suspend fun delete(profileId: String): DeleteProfileResult
}
