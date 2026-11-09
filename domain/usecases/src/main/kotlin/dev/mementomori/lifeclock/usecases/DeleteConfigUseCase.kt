package dev.mementomori.lifeclock.usecases

import dev.mementomori.lifeclock.contracts.DeleteConfigResult
import dev.mementomori.lifeclock.contracts.WidgetConfigRepository
import dev.mementomori.lifeclock.contracts.WidgetRefresher

/**
 * Delete a config, then redraw.
 *
 * The repository refuses while a placed widget still renders it, so the caller gets a reason rather
 * than a boolean. The redraw happens either way: a refused delete is cheap, and a successful one
 * must not leave a widget pointing at a config that is gone.
 */
class DeleteConfigUseCase(
    private val configs: WidgetConfigRepository,
    private val refresher: WidgetRefresher,
) {
    suspend operator fun invoke(configId: String): DeleteConfigResult {
        val result = configs.delete(configId)
        refresher.redrawNow()
        return result
    }
}
