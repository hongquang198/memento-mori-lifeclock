package dev.mementomori.lifeclock.usecases

import dev.mementomori.lifeclock.contracts.PlacedWidget
import dev.mementomori.lifeclock.contracts.WidgetBindingRepository
import dev.mementomori.lifeclock.contracts.WidgetConfigRepository
import dev.mementomori.lifeclock.contracts.WidgetPlacements
import dev.mementomori.lifeclock.model.appWidgetBindingKey
import kotlinx.coroutines.flow.first

/**
 * "What is on the home screen right now?"
 *
 * Three sources, joined: the platform says which ids are placed, the bindings say which config each
 * one points at, and the catalog says what that config is called. A placed widget with no binding
 * keeps a null name - the screen uses that to explain why a redraw cannot change anything.
 *
 * Reading the ids from the platform (not Glance's cache) is the M0 lesson: Glance's registry can be
 * stale, and a list built from it lies about what the user is looking at.
 */
class ListPlacedWidgetsUseCase(
    private val placements: WidgetPlacements,
    private val bindings: WidgetBindingRepository,
    private val configs: WidgetConfigRepository,
) {
    suspend operator fun invoke(): List<PlacedWidget> {
        val ids = placements.placed()
        if (ids.isEmpty()) return emptyList()

        val bound = bindings.observeAllBindings().first()
        val known = configs.observeAll().first()

        return ids.sorted().map { id ->
            val configId = bound.firstOrNull { it.glanceId == appWidgetBindingKey(id) }?.configId
            PlacedWidget(
                appWidgetId = id,
                configId = configId,
                configName = known.firstOrNull { it.configId == configId }?.name,
            )
        }
    }
}
