package dev.mementomori.lifeclock.usecases

import dev.mementomori.lifeclock.contracts.DeleteProfileResult
import dev.mementomori.lifeclock.contracts.ProfileRepository

/**
 * Delete a person, if no config still computes from them.
 *
 * No redraw: the repository refuses while a config references the profile, and a successful delete
 * removes a person that nothing renders. There is nothing on the home screen to update.
 */
class DeleteProfileUseCase(
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(profileId: String): DeleteProfileResult = profiles.delete(profileId)
}
