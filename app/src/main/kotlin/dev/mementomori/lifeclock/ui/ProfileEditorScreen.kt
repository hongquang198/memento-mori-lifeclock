package dev.mementomori.lifeclock.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mementomori.lifeclock.model.Sex
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.mortality.ProfileDraft
import dev.mementomori.lifeclock.widget.render.LifeStatText
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * T4.7/T4.8 - the person editor.
 *
 * The preview is not a mock: it calls the same engine and the same table the widget does, so the
 * number the user sees while typing is the number the widget will show.
 */
@Composable
fun ProfileEditorScreen(viewModel: MainViewModel, profileId: String?, onDone: () -> Unit) {
    val existing = remember(profileId) {
        viewModel.profiles.value.firstOrNull { it.profileId == profileId }
    }
    var draft by remember(profileId) {
        mutableStateOf(
            existing?.let {
                ProfileDraft(
                    profileId = it.profileId,
                    name = it.name,
                    birthEpochDay = it.birthEpochDay,
                    sex = it.sex,
                    countryIso3 = it.countryIso3,
                    sleepHoursPerNight = it.sleepHoursPerNight,
                    screenHoursPerDay = it.screenHoursPerDay,
                )
            } ?: viewModel.newProfile(),
        )
    }

    val today = remember { viewModel.today() }
    val knownCountries = remember { viewModel.countries.toSet() }
    val problems = draft.validate(today, knownCountries)
    val snapshot = remember(draft) { viewModel.snapshotFor(draft) }
    var showCountries by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val birth = LocalDate.ofEpochDay(draft.birthEpochDay)
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        draft = draft.copy(
                            birthEpochDay = LocalDate.of(year, month + 1, day).toEpochDay(),
                        )
                    },
                    birth.year,
                    birth.monthValue - 1,
                    birth.dayOfMonth,
                ).show()
            },
        ) { Text("Born $birth") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { sex ->
                FilterChip(
                    selected = draft.sex == sex,
                    onClick = { draft = draft.copy(sex = sex) },
                    label = { Text(sex.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        OutlinedButton(onClick = { showCountries = true }) {
            Text("Country: ${viewModel.countryLabel(draft.countryIso3)}")
        }

        Text(
            "Sleep ${draft.sleepHoursPerNight.roundToInt()} h / night",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = draft.sleepHoursPerNight.toFloat(),
            onValueChange = { draft = draft.copy(sleepHoursPerNight = it.roundToInt().toDouble()) },
            valueRange = 0f..12f,
        )

        Text(
            "Screen ${draft.screenHoursPerDay.roundToInt()} h / day",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = draft.screenHoursPerDay.toFloat(),
            onValueChange = { draft = draft.copy(screenHoursPerDay = it.roundToInt().toDouble()) },
            valueRange = 0f..16f,
        )

        if (snapshot != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${snapshot.stats.getValue(TimeCategory.DAYS).remaining} days left",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "at age ${snapshot.ageYears} · ${snapshot.lifeTable.source}, " +
                            snapshot.lifeTable.vintage,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (problems.isNotEmpty()) {
            Text(
                problems.joinToString(", ") { it.name.replace('_', ' ').lowercase() },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            enabled = problems.isEmpty(),
            onClick = {
                viewModel.saveProfile(draft)
                onDone()
            },
        ) { Text("Save person") }

        if (existing != null) {
            TextButton(
                onClick = {
                    viewModel.deleteProfile(existing.profileId)
                    onDone()
                },
            ) { Text("Delete person") }
        }
    }

    if (showCountries) {
        CountryDialog(
            countries = viewModel.countries,
            selected = draft.countryIso3,
            label = viewModel::countryLabel,
            onDismiss = { showCountries = false },
            onPick = {
                draft = draft.copy(countryIso3 = it)
                showCountries = false
            },
        )
    }
}

@Composable
private fun CountryDialog(
    countries: List<String>,
    selected: String,
    label: (String) -> String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, countries) {
        if (query.isBlank()) {
            countries
        } else {
            countries.filter {
                label(it).contains(query, ignoreCase = true) || it.contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Country") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    // 237 rows is fine to filter; showing them all at once is not.
                    items(filtered.take(60), key = { it }) { iso3 ->
                        TextButton(
                            onClick = { onPick(iso3) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label(iso3) + if (iso3 == selected) "  ✓" else "")
                        }
                    }
                }
            }
        },
    )
}

