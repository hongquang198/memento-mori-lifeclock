package dev.mementomori.lifeclock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.widget.config.configureIntent
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Which screen is on top. Small enough that a navigation library would be the bigger dependency. */
private sealed interface Screen {
    data object Home : Screen
    data object Widgets : Screen
    data class Profile(val profileId: String?) : Screen
    data class Config(val initial: WidgetConfigRecord) : Screen
}

/**
 * Survives rotation and process death, which `remember` did not: the app used to fall back to Home
 * on a rotation, and the system back gesture always finished the activity because nothing had
 * claimed it (see [MementoApp]).
 *
 * A string key, not the object graph: `WidgetConfigRecord` is already `@Serializable`, so the editor
 * screen can be rebuilt exactly, and an unparseable value degrades to `null` (Home) instead of
 * crashing the activity on restore.
 */
private val screenJson = Json { ignoreUnknownKeys = true }

private val ScreenSaver: Saver<Screen, String> = Saver(
    save = { screen ->
        when (screen) {
            Screen.Home -> "home"
            Screen.Widgets -> "widgets"
            is Screen.Profile -> "profile:${screen.profileId.orEmpty()}"
            is Screen.Config -> "config:" + screenJson.encodeToString(
                WidgetConfigRecord.serializer(),
                screen.initial,
            )
        }
    },
    restore = { saved ->
        when {
            saved == "home" -> Screen.Home
            saved == "widgets" -> Screen.Widgets
            saved.startsWith("profile:") -> Screen.Profile(saved.removePrefix("profile:").ifEmpty { null })
            saved.startsWith("config:") -> runCatching {
                Screen.Config(
                    screenJson.decodeFromString(
                        WidgetConfigRecord.serializer(),
                        saved.removePrefix("config:"),
                    ),
                )
            }.getOrNull()
            else -> null
        }
    },
)

private fun Screen.title(): String = when (this) {
    Screen.Home -> "Memento Mori"
    Screen.Widgets -> "Your widgets"
    is Screen.Profile -> if (profileId == null) "Add a person" else "Edit person"
    is Screen.Config -> "Edit widget"
}

/**
 * The app shell: one Scaffold, one snackbar, four screens.
 *
 * Deliberately not a navigation graph. There are four destinations and no deep links; a library
 * would add a dependency and a back-stack abstraction without removing a line of code here.
 *
 * What Compose does not provide for free is the back bridge that Flutter installs with `WidgetsApp`.
 * The system gesture reaches the activity's `OnBackPressedDispatcher`; with no callback registered
 * it falls through to `finish()`, which is why back used to close the app from any screen. One
 * `BackHandler` is the whole fix at this depth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MementoApp(viewModel: MainViewModel) {
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }
    val snackbar = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title()) },
                navigationIcon = {
                    if (screen !is Screen.Home) {
                        TextButton(onClick = { screen = Screen.Home }) { Text("Back") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val current = screen) {
                Screen.Home -> HomeScreen(
                    viewModel = viewModel,
                    onProfile = { screen = Screen.Profile(it) },
                    onConfig = { screen = Screen.Config(it) },
                    onWidgets = { screen = Screen.Widgets },
                )

                Screen.Widgets -> WidgetsScreen(viewModel)

                is Screen.Profile -> ProfileEditorScreen(
                    viewModel = viewModel,
                    profileId = current.profileId,
                    onDone = { screen = Screen.Home },
                )

                is Screen.Config -> ConfigEditorScreen(
                    viewModel = viewModel,
                    initial = current.initial,
                    onDone = { screen = Screen.Home },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    viewModel: MainViewModel,
    onProfile: (String?) -> Unit,
    onConfig: (WidgetConfigRecord) -> Unit,
    onWidgets: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle("People") }
        items(profiles, key = { it.profileId }) { profile ->
            PersonCard(profile, viewModel.today(), viewModel::countryLabel) { onProfile(profile.profileId) }
        }
        item {
            OutlinedButton(onClick = { onProfile(null) }) { Text("Add a person") }
        }

        item { SectionTitle("Widgets") }
        items(configs, key = { it.configId }) { config ->
            ConfigCard(
                config = config,
                onEdit = { onConfig(config) },
                onDuplicate = { viewModel.duplicateConfig(config) },
                onDelete = { viewModel.deleteConfig(config.configId) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onConfig(viewModel.newConfig(WidgetType.COUNTDOWN)) }) {
                    Text("New countdown")
                }
                OutlinedButton(onClick = { onConfig(viewModel.newConfig(WidgetType.LIFE_CLOCK)) }) {
                    Text("New life clock")
                }
            }
        }

        item { SectionTitle("Home screen") }
        item {
            OutlinedButton(onClick = onWidgets) { Text("Your widgets") }
        }
    }
}

@Composable
private fun PersonCard(
    profile: PersonProfile,
    today: LocalDate,
    countryLabel: (String) -> String,
    onClick: () -> Unit,
) {
    val age = ChronoUnit.YEARS.between(LocalDate.ofEpochDay(profile.birthEpochDay), today)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(profile.name, fontWeight = FontWeight.Medium)
            Text(
                "$age years · ${countryLabel(profile.countryIso3)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ConfigCard(
    config: WidgetConfigRecord,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp)) {
            Text(config.name, fontWeight = FontWeight.Medium)
            Text(
                "${if (config.type == WidgetType.LIFE_CLOCK) "Life clock" else "Countdown"} · " +
                    "${config.layout.name.lowercase()} · ${config.rings.size} ring(s)",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDuplicate) { Text("Duplicate") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun WidgetsScreen(viewModel: MainViewModel) {
    val placed by viewModel.placed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshPlaced() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Each placed widget points at a config. Choosing a different one here changes " +
                    "what that instance draws.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (placed.isEmpty()) {
            item {
                Text(
                    "Nothing placed yet. Long-press the home screen, choose Widgets, and pick " +
                        "Memento Mori Countdown.",
                )
            }
        }
        items(placed, key = { it.appWidgetId }) { widget ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Widget #${widget.appWidgetId}", fontWeight = FontWeight.Medium)
                    Text(
                        widget.configName ?: "Not configured - press Choose config",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                context.startActivity(configureIntent(context, widget.appWidgetId))
                            },
                        ) { Text("Choose config") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.redrawNow() }) { Text("Redraw now") }
                OutlinedButton(onClick = { viewModel.refreshPlaced() }) { Text("Refresh list") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}
