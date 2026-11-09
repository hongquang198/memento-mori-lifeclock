package dev.mementomori.lifeclock.usecases

import dev.mementomori.lifeclock.contracts.WidgetConfigRepository
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import java.util.UUID

/**
 * Copy a config under a new identity.
 *
 * No redraw: a duplicate is a catalog entry that nothing is bound to yet. It becomes visible on the
 * home screen only when the user places and binds a widget to it.
 */
class DuplicateConfigUseCase(
    private val configs: WidgetConfigRepository,
) {
    suspend operator fun invoke(config: WidgetConfigRecord): WidgetConfigRecord {
        val copy = config.copy(
            configId = UUID.randomUUID().toString(),
            name = "${config.name} copy",
        )
        configs.save(copy)
        return copy
    }
}
