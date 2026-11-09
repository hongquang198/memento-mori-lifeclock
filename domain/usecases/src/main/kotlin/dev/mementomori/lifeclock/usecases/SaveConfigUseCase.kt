package dev.mementomori.lifeclock.usecases

import dev.mementomori.lifeclock.contracts.WidgetConfigRepository
import dev.mementomori.lifeclock.contracts.WidgetRefresher
import dev.mementomori.lifeclock.model.WidgetConfigRecord

/**
 * Save a config, then redraw the home screen.
 *
 * The order is the whole point. Saving alone changes the store and leaves the placed widget showing
 * yesterday's ring until the next scheduled refresh, which can be hours away. The redraw is part of
 * the operation, not a detail the caller could forget.
 */
class SaveConfigUseCase(
    private val configs: WidgetConfigRepository,
    private val refresher: WidgetRefresher,
) {
    suspend operator fun invoke(config: WidgetConfigRecord) {
        configs.save(config)
        refresher.redrawNow()
    }
}
