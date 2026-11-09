package dev.mementomori.lifeclock.mortality

import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.Sex
import java.time.LocalDate

/**
 * A profile being edited.
 *
 * Lives next to [ProfileValidator] because that is where the plausibility rules already are: one
 * place decides whether a person is a person, whether the answer arrives from the editor or a file.
 */
data class ProfileDraft(
    val profileId: String,
    val name: String,
    val birthEpochDay: Long,
    val sex: Sex,
    val countryIso3: String,
    val sleepHoursPerNight: Double = 8.0,
    val screenHoursPerDay: Double = 4.0,
) {
    fun toProfile(): PersonProfile = PersonProfile(
        profileId = profileId,
        name = name,
        birthEpochDay = birthEpochDay,
        sex = sex,
        countryIso3 = countryIso3,
        sleepHoursPerNight = sleepHoursPerNight,
        screenHoursPerDay = screenHoursPerDay,
    )

    fun validate(today: LocalDate, knownCountries: Set<String>): List<ProfileProblem> = buildList {
        if (name.isBlank()) add(ProfileProblem.NAME_BLANK)
        if (countryIso3 !in knownCountries) add(ProfileProblem.UNKNOWN_COUNTRY)
        addAll(ProfileValidator.validate(toProfile(), today))
    }

    fun isValid(today: LocalDate, knownCountries: Set<String>): Boolean =
        validate(today, knownCountries).isEmpty()

    companion object {
        fun blank(profileId: String, today: LocalDate) = ProfileDraft(
            profileId = profileId,
            name = "",
            birthEpochDay = today.minusYears(30).toEpochDay(),
            sex = Sex.FEMALE,
            countryIso3 = "VNM",
        )
    }
}
