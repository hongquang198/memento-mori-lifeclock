package dev.mementomori.lifeclock.ui

import android.app.Application
import dev.mementomori.lifeclock.widget.appWidgetBindingKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mementomori.lifeclock.data.DeleteConfigResult
import dev.mementomori.lifeclock.data.DeleteProfileResult
import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.RingPresets
import dev.mementomori.lifeclock.model.RingTemplate
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetInstanceBinding
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.mortality.LifeSnapshot
import dev.mementomori.lifeclock.mortality.ProfileDraft
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder
import dev.mementomori.lifeclock.widget.refresh.WidgetRefreshScheduler
import dev.mementomori.lifeclock.widget.refresh.placedWidgetIds
import dev.mementomori.lifeclock.widget.render.WidgetSpec
import dev.mementomori.lifeclock.widget.render.WidgetSpecResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/** One placed instance, resolved for the "Your widgets" list. */
data class PlacedWidget(
    val appWidgetId: Int,
    val configId: String?,
    val configName: String?,
)

/**
 * The app's only state holder.
 *
 * It reads the same graph the widget uses ([WidgetDependenciesHolder]) instead of building a second
 * one: a config saved here is the config the next draw reads, because there is only one store.
 * The widget itself is redrawn through the same worker, never directly.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val deps = requireNotNull(WidgetDependenciesHolder.current) {
        "LifeClockApplication must publish the graph before any screen reads it"
    }
    private val appContext = app.applicationContext

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
        deps.profiles.save(draft.toProfile())
        refreshWidgets()
    }

    fun deleteProfile(profileId: String) = viewModelScope.launch {
        _message.value = when (val result = deps.profiles.delete(profileId)) {
            DeleteProfileResult.Deleted -> "Person deleted"
            is DeleteProfileResult.BlockedByConfigs ->
                "Still used by ${result.configIds.size} config(s)"
            DeleteProfileResult.NotFound -> "Already gone"
        }
    }

    fun saveConfig(config: WidgetConfigRecord) = viewModelScope.launch {
        deps.configs.save(config)
        refreshWidgets()
    }

    fun duplicateConfig(config: WidgetConfigRecord) = viewModelScope.launch {
        deps.configs.save(
            config.copy(configId = UUID.randomUUID().toString(), name = "${config.name} copy"),
        )
    }

    fun deleteConfig(configId: String) = viewModelScope.launch {
        _message.value = when (val result = deps.configs.delete(configId)) {
            DeleteConfigResult.Deleted -> "Config deleted"
            is DeleteConfigResult.BlockedByBindings ->
                "Still used by ${result.glanceIds.size} placed widget(s)"
            DeleteConfigResult.NotFound -> "Already gone"
        }
        refreshWidgets()
    }

    /** Reads placements from the platform, never from Glance's cache (M0). */
    fun refreshPlaced() = viewModelScope.launch {
        val bindings = deps.bindings.observeAllBindings().first()
        val known = deps.configs.observeAll().first()

        _placed.value = placedWidgetIds(appContext).sorted().map { id ->
            val configId = bindingFor(bindings, id)
            PlacedWidget(
                appWidgetId = id,
                configId = configId,
                configName = known.firstOrNull { it.configId == configId }?.name,
            )
        }
    }

    fun refreshWidgets() {
        WidgetRefreshScheduler(appContext).refreshNow(reason = "app", force = true)
    }

    private fun bindingFor(bindings: List<WidgetInstanceBinding>, appWidgetId: Int): String? =
        bindings.firstOrNull { it.glanceId == appWidgetBindingKey(appWidgetId) }?.configId
}
