package dev.mementomori.lifeclock.widget.config

import dev.mementomori.lifeclock.data.WidgetBindingRepository
import dev.mementomori.lifeclock.data.WidgetConfigRepository
import dev.mementomori.lifeclock.model.RefreshIntent
import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.time.Clock
import kotlinx.coroutines.flow.first
import java.time.temporal.ChronoUnit

/**
 * The rules behind the config picker, with no Android in sight.
 *
 * [ConfigActivity] only reads an intent, draws a list and reports a result; *what* is offered and
 * *what* a choice means is decided here, so it can be tested as plain Kotlin.
 */
class ConfigPicker(
    private val configs: WidgetConfigRepository,
    private val bindings: WidgetBindingRepository,
    private val clock: Clock,
) {

    /** Catalog order is stable, so the list the user sees does not reshuffle between visits. */
    suspend fun catalog(): List<WidgetConfigRecord> = configs.observeAll().first()

    /**
     * A fresh install has nothing to bind to, so the picker would be an empty screen and the first
     * placement would have to be cancelled. Seed one placeholder countdown instead.
     *
     * Returns the seeded record, or null when the catalog already had something.
     */
    suspend fun seedIfEmpty(seedName: String): WidgetConfigRecord? {
        if (catalog().isNotEmpty()) return null

        val now = clock.now()
        val seed = WidgetConfigRecord(
            configId = SEED_CONFIG_ID,
            name = seedName,
            type = WidgetType.COUNTDOWN,
            startEpochMillis = now.toEpochMilli(),
            endEpochMillis = now.plus(SEED_DAYS, ChronoUnit.DAYS).toEpochMilli(),
            rings = listOf(RingConfigRecord(category = TimeCategory.DAYS)),
            granularity = RefreshIntent.DAILY,
        )
        configs.save(seed)
        return seed
    }

    /** Point a placed widget at [configId]. Rebinding the same instance replaces its row. */
    suspend fun choose(glanceId: String, configId: String) = bindings.bind(glanceId, configId)

    /** What a placed widget currently renders, so a reconfigure can mark it. Null when unbound. */
    suspend fun currentConfig(glanceId: String): String? =
        bindings.observeBinding(glanceId).first()?.configId

    companion object {
        /** Stable, so re-running the seed (or a second Activity) cannot create a duplicate. */
        const val SEED_CONFIG_ID: String = "seed-first-year"

        /** One year from first placement, until the editor (feature C) can pick a real deadline. */
        const val SEED_DAYS: Long = 365
    }
}
