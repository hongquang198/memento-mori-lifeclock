package dev.mementomori.lifeclock.data

import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetInstanceBinding
import kotlinx.coroutines.flow.Flow

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
}

/** Shared rules, used by every implementation so they cannot drift apart. */
internal object StoreRules {

    fun upsertBinding(
        current: List<WidgetInstanceBinding>,
        next: WidgetInstanceBinding,
    ): List<WidgetInstanceBinding> = current.filterNot { it.glanceId == next.glanceId } + next

    fun boundGlanceIds(
        bindings: List<WidgetInstanceBinding>,
        configId: String,
    ): List<String> = bindings.filter { it.configId == configId }.map { it.glanceId }

    /** Replace in place to keep catalog order; append only when the id is new. */
    fun upsertConfig(
        current: List<WidgetConfigRecord>,
        next: WidgetConfigRecord,
    ): List<WidgetConfigRecord> {
        val index = current.indexOfFirst { it.configId == next.configId }
        return if (index >= 0) {
            current.toMutableList().also { it[index] = next }
        } else {
            current + next
        }
    }
}
