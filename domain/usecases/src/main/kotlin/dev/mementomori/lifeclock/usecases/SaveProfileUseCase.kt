package dev.mementomori.lifeclock.usecases

import dev.mementomori.lifeclock.contracts.ProfileRepository
import dev.mementomori.lifeclock.contracts.WidgetRefresher
import dev.mementomori.lifeclock.model.PersonProfile

/** Save a person, then redraw: a life clock bound to them just got a different answer. */
class SaveProfileUseCase(
    private val profiles: ProfileRepository,
    private val refresher: WidgetRefresher,
) {
    suspend operator fun invoke(profile: PersonProfile) {
        profiles.save(profile)
        refresher.redrawNow()
    }
}
