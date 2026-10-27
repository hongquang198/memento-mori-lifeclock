package dev.mementomori.lifeclock.widget.config

import dev.mementomori.lifeclock.data.ProfileRepository
import dev.mementomori.lifeclock.data.WidgetBindingRepository
import dev.mementomori.lifeclock.data.WidgetConfigRepository
import dev.mementomori.lifeclock.model.LabelPlacement
import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.RefreshIntent
import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.RingStyleRecord
import dev.mementomori.lifeclock.model.Sex
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.time.Clock
import kotlinx.coroutines.flow.first
import java.time.LocalDate
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
    private val profiles: ProfileRepository,
    private val clock: Clock,
) {

    /** Catalog order is stable, so the list the user sees does not reshuffle between visits. */
    suspend fun catalog(): List<WidgetConfigRecord> = configs.observeAll().first()

    /**
     * A fresh install has nothing to bind to, so the picker would be an empty screen and the first
     * placement would have to be cancelled. Seed one countdown and one life clock.
     *
     * The life clock needs a person, and the editor that asks for one lands in M4, so the seed is a
     * *named sample* - a fabricated birthday must never be presented as the user's own. M4 replaces
     * this with real profile entry and drops the sample row.
     *
     * Returns the seeded records, or an empty list when the catalog already had something.
     */
    suspend fun seedIfEmpty(
        countdownName: String,
        lifeClockName: String,
        profileName: String,
    ): List<WidgetConfigRecord> {
        if (catalog().isNotEmpty()) return emptyList()

        val now = clock.now()
        profiles.save(
            PersonProfile(
                profileId = SEED_PROFILE_ID,
                name = profileName,
                birthEpochDay = SEED_BIRTH.toEpochDay(),
                sex = Sex.MALE,
                countryIso3 = SEED_COUNTRY,
            ),
        )

        val countdown = WidgetConfigRecord(
            configId = SEED_CONFIG_ID,
            name = countdownName,
            type = WidgetType.COUNTDOWN,
            startEpochMillis = now.toEpochMilli(),
            endEpochMillis = now.plus(SEED_DAYS, ChronoUnit.DAYS).toEpochMilli(),
            rings = listOf(RingConfigRecord(category = TimeCategory.DAYS)),
            granularity = RefreshIntent.DAILY,
        )
        val lifeClock = WidgetConfigRecord(
            configId = SEED_LIFE_CLOCK_ID,
            name = lifeClockName,
            type = WidgetType.LIFE_CLOCK,
            startEpochMillis = now.toEpochMilli(),
            profileRef = SEED_PROFILE_ID,
            rings = listOf(
                RingConfigRecord(
                    category = TimeCategory.DAYS,
                    style = RingStyleRecord(label = "Life left", labelPlacement = LabelPlacement.INSIDE),
                ),
            ),
            granularity = RefreshIntent.DAILY,
        )

        configs.save(countdown)
        configs.save(lifeClock)
        return listOf(countdown, lifeClock)
    }

    /** Point a placed widget at [configId]. Rebinding the same instance replaces its row. */
    suspend fun choose(glanceId: String, configId: String) = bindings.bind(glanceId, configId)

    /** What a placed widget currently renders, so a reconfigure can mark it. Null when unbound. */
    suspend fun currentConfig(glanceId: String): String? =
        bindings.observeBinding(glanceId).first()?.configId

    companion object {
        /** Stable, so re-running the seed (or a second Activity) cannot create a duplicate. */
        const val SEED_CONFIG_ID: String = "seed-first-year"

        const val SEED_LIFE_CLOCK_ID: String = "seed-life-clock"
        const val SEED_PROFILE_ID: String = "seed-profile"

        /** One year from first placement, until the editor (feature C) can pick a real deadline. */
        const val SEED_DAYS: Long = 365

        /** A labelled placeholder, not a claim about anyone. */
        val SEED_BIRTH: LocalDate = LocalDate.of(1990, 1, 1)
        const val SEED_COUNTRY: String = "VNM"
    }
}
