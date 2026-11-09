package dev.mementomori.lifeclock.data

import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetInstanceBinding

/**
 * Shared rules, used by every implementation so they cannot drift apart.
 *
 * `internal` on purpose: the two implementations in this module must agree on upsert order and on
 * what "still referenced" means, but nothing outside the data layer should build storage semantics
 * itself. The contracts in `:domain:contracts` describe *what*, this file describes *how*.
 */
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

    fun upsertProfile(
        current: List<PersonProfile>,
        next: PersonProfile,
    ): List<PersonProfile> {
        val index = current.indexOfFirst { it.profileId == next.profileId }
        return if (index >= 0) {
            current.toMutableList().also { it[index] = next }
        } else {
            current + next
        }
    }

    /** Configs that would be left computing from nothing if the profile were deleted. */
    fun configsReferencing(
        configs: List<WidgetConfigRecord>,
        profileId: String,
    ): List<String> = configs.filter { it.profileRef == profileId }.map { it.configId }
}
