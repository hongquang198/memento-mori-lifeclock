package dev.mementomori.lifeclock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mementomori.lifeclock.contracts.DeleteConfigResult
import dev.mementomori.lifeclock.contracts.DeleteProfileResult
import dev.mementomori.lifeclock.contracts.PlacedWidget
import dev.mementomori.lifeclock.contracts.WidgetRefresher
import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.RingPresets
import dev.mementomori.lifeclock.model.RingTemplate
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.mortality.LifeSnapshot
import dev.mementomori.lifeclock.mortality.ProfileDraft
import dev.mementomori.lifeclock.usecases.DeleteConfigUseCase
import dev.mementomori.lifeclock.usecases.DeleteProfileUseCase
import dev.mementomori.lifeclock.usecases.DuplicateConfigUseCase
import dev.mementomori.lifeclock.usecases.ListPlacedWidgetsUseCase
import dev.mementomori.lifeclock.usecases.SaveConfigUseCase
import dev.mementomori.lifeclock.usecases.SaveProfileUseCase
import dev.mementomori.lifeclock.widget.WidgetDependencies
import dev.mementomori.lifeclock.widget.render.WidgetSpec
import dev.mementomori.lifeclock.widget.render.WidgetSpecResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/**
 * The app's only state holder.
 *
 * Note what is *not* in the constructor: a `Context`. It used to be an `AndroidViewModel` that built
 * a `WidgetRefreshScheduler` and called `AppWidgetManager` itself, which meant the screen's state
 * holder could only be tested on a device. The platform work now sits behind [WidgetRefresher] and
 * the `ListPlacedWidgetsUseCase` port, and the use cases own the "save, then redraw" ordering.
 *
 * [WidgetDependencies] is injected whole rather than as six separate parameters because the app and
 * the widget must share one instance of it: one store, one clock. Hilt guarantees that with
 * `@Singleton` in `AppModule`.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val deps: WidgetDependencies,
    private val saveConfigUseCase: SaveConfigUseCase,
    private val deleteConfigUseCase: DeleteConfigUseCase,
    private val duplicateConfigUseCase: DuplicateConfigUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val deleteProfileUseCase: DeleteProfileUseCase,
    private val listPlacedWidgetsUseCase: ListPlacedWidgetsUseCase,
    private val refresher: WidgetRefresher,
) : ViewModel() {

    val profiles: StateFlow<List<PersonProfile>> =
        deps.profiles.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val configs: StateFlow<List<WidgetConfigRecord>> =
        deps.configs.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every country the shipped table covers. The editor cannot offer one it cannot compute. */
    val countries: List<String> = deps.lifeStats.countries

    private val _placed = MutableStateFlow<List<PlacedWidget>>(emptyList())
    val placed: StateFlow<List<PlacedWidget>> = _placed.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun today(): LocalDate = deps.clock.now().atZone(deps.zone).toLocalDate()

    fun countryLabel(iso3: String): String = CountryNames.of(iso3)

    fun snapshotFor(draft: ProfileDraft): LifeSnapshot? =
        deps.lifeStats.compute(draft.toProfile(), deps.clock.now(), deps.zone)

    fun snapshotFor(config: WidgetConfigRecord): LifeSnapshot? {
        val profileId = config.profileRef ?: return null
        val profile = profiles.value.firstOrNull { it.profileId == profileId } ?: return null
        return deps.lifeStats.compute(profile, deps.clock.now(), deps.zone)
    }

    /** The same resolver the widget uses, so the preview and the placed widget cannot disagree. */
    fun specFor(config: WidgetConfigRecord): WidgetSpec? =
        WidgetSpecResolver(deps.countdownModel, deps.clock, deps.zone)
            .resolve(config, snapshotFor(config))

    fun newProfile(): ProfileDraft = ProfileDraft.blank(UUID.randomUUID().toString(), today())

    fun newConfig(type: WidgetType): WidgetConfigRecord {
        val now = deps.clock.now()
        val base = WidgetConfigRecord(
            configId = UUID.randomUUID().toString(),
            name = if (type == WidgetType.LIFE_CLOCK) "Life clock" else "Countdown",
            type = type,
            startEpochMillis = now.toEpochMilli(),
            endEpochMillis = if (type == WidgetType.COUNTDOWN) {
                now.plus(30, ChronoUnit.DAYS).toEpochMilli()
            } else {
                null
            },
            profileRef = if (type == WidgetType.LIFE_CLOCK) profiles.value.firstOrNull()?.profileId else null,
        )
        return RingPresets.apply(RingTemplate.CLASSIC, base)
    }

    fun saveProfile(draft: ProfileDraft) = viewModelScope.launch {
        saveProfileUseCase(draft.toProfile())
    }

    fun deleteProfile(profileId: String) = viewModelScope.launch {
        _message.value = when (val result = deleteProfileUseCase(profileId)) {
            DeleteProfileResult.Deleted -> "Person deleted"
            is DeleteProfileResult.BlockedByConfigs ->
                "Still used by ${result.configIds.size} config(s)"
            DeleteProfileResult.NotFound -> "Already gone"
        }
    }

    fun saveConfig(config: WidgetConfigRecord) = viewModelScope.launch {
        saveConfigUseCase(config)
    }

    fun duplicateConfig(config: WidgetConfigRecord) = viewModelScope.launch {
        duplicateConfigUseCase(config)
    }

    fun deleteConfig(configId: String) = viewModelScope.launch {
        _message.value = when (val result = deleteConfigUseCase(configId)) {
            DeleteConfigResult.Deleted -> "Config deleted"
            is DeleteConfigResult.BlockedByBindings ->
                "Still used by ${result.glanceIds.size} placed widget(s)"
            DeleteConfigResult.NotFound -> "Already gone"
        }
    }

    fun refreshPlaced() = viewModelScope.launch {
        _placed.value = listPlacedWidgetsUseCase()
    }

    /**
     * Redraws every placed instance, and says so when a redraw cannot possibly help.
     *
     * An instance with no binding draws the setup message however often it is redrawn, so
     * "Redraw now" used to look broken: nothing changed and nothing explained why. Binding is the
     * picker's job, so the message points at it. Only the explicit button reports: a save that
     * triggers a redraw must not nag about an unrelated unbound instance.
     */
    fun redrawNow() = viewModelScope.launch {
        val placed = listPlacedWidgetsUseCase().also { _placed.value = it }
        refresher.redrawNow()

        val unbound = placed.filter { it.configId == null }
        if (unbound.isNotEmpty()) {
            _message.value = unbound.joinToString(prefix = "No config yet: ", separator = ", ") {
                "#${it.appWidgetId}"
            } + ". Tap the widget, or press Choose config."
        }
    }
}
