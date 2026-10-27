package dev.mementomori.lifeclock.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mementomori.lifeclock.model.LabelPlacement
import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.RingStyleRecord
import dev.mementomori.lifeclock.model.RingTemplate
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetLayout
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.model.editor.ConfigDraft
import dev.mementomori.lifeclock.model.editor.ConfigProblem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * T4.7/T4.8 - the widget editor.
 *
 * The draft is the only state; every control is a `ConfigDraft` transformation, and the preview is
 * recomputed from the draft with the same resolver and rasterizer the widget uses.
 */
@Composable
fun ConfigEditorScreen(viewModel: MainViewModel, initial: WidgetConfigRecord, onDone: () -> Unit) {
    var draft by remember(initial.configId) { mutableStateOf(ConfigDraft(initial)) }
    val config = draft.value
    val profiles by viewModel.profiles.collectAsState()
    val configs by viewModel.configs.collectAsState()

    val spec = remember(config, profiles) { viewModel.specFor(config) }
    val snapshot = remember(config, profiles) { viewModel.snapshotFor(config) }
    val problems = draft.validate()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RingPreview(config, spec, snapshot) }

        item {
            OutlinedTextField(
                value = config.name,
                onValueChange = { draft = draft.named(it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            ChipRow(
                title = "Type",
                values = WidgetType.entries,
                selected = config.type,
                label = { if (it == WidgetType.LIFE_CLOCK) "Life clock" else "Countdown" },
                onPick = { draft = draft.withType(it) },
            )
        }

        item {
            if (config.type == WidgetType.COUNTDOWN) {
                DeadlineRow(config, context) { draft = draft.withDeadline(it) }
            } else {
                ProfileRow(profiles, config.profileRef) { draft = draft.withProfile(it) }
            }
        }

        item {
            ChipRow(
                title = "Template",
                values = RingTemplate.entries.filter { it != RingTemplate.CUSTOM },
                selected = config.template,
                label = { it.name.lowercase() },
                onPick = { draft = draft.withTemplate(it) },
            )
        }

        item {
            ChipRow(
                title = "Layout",
                values = WidgetLayout.entries,
                selected = config.layout,
                label = { it.name.lowercase() },
                onPick = { draft = draft.withLayout(it) },
            )
        }

        itemsIndexed(config.rings, key = { _, ring -> ring.category.name }) { index, ring ->
            RingEditor(
                ring = ring,
                onEdit = { transform -> draft = draft.withStyle(index, transform) },
                onRemove = { draft = draft.withCategory(ring.category, enabled = false) },
            )
        }

        item {
            AddRingRow(used = config.rings.map { it.category }.toSet()) {
                draft = draft.withCategory(it, enabled = true)
            }
        }

        if (problems.isNotEmpty()) {
            item {
                Text(
                    problems.joinToString(", ") { it.label() },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            Button(
                enabled = problems.isEmpty(),
                onClick = {
                    viewModel.saveConfig(config)
                    onDone()
                },
            ) { Text("Save widget") }
        }

        if (configs.any { it.configId == config.configId }) {
            item {
                TextButton(
                    onClick = {
                        viewModel.deleteConfig(config.configId)
                        onDone()
                    },
                ) { Text("Delete widget") }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onPick(value) },
                    label = { Text(label(value)) },
                )
            }
        }
    }
}

@Composable
private fun DeadlineRow(config: WidgetConfigRecord, context: Context, onPick: (Long) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val current = config.endEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    OutlinedButton(
        onClick = {
            val date = current ?: LocalDate.now(zone)
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    // End of the chosen day: a deadline of "the 3rd" means through the 3rd.
                    onPick(
                        LocalDate.of(year, month + 1, day)
                            .atTime(23, 59)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli(),
                    )
                },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            ).show()
        },
    ) { Text(if (current == null) "Pick a deadline" else "Deadline: $current") }
}

@Composable
private fun ProfileRow(profiles: List<PersonProfile>, selectedId: String?, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val name = profiles.firstOrNull { it.profileId == selectedId }?.name ?: "Pick a person"

    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(name) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = {
                        onPick(profile.profileId)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RingEditor(
    ring: RingConfigRecord,
    onEdit: ((RingStyleRecord) -> RingStyleRecord) -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ring.category.label(),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = onRemove) { Text("Remove") }
            }

            Text("Diameter ${ring.style.diameterDp} dp", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = ring.style.diameterDp.toFloat(),
                onValueChange = { value ->
                    onEdit { it.copy(diameterDp = value.roundToInt()) }
                },
                valueRange = ConfigDraft.MIN_DIAMETER_DP.toFloat()..ConfigDraft.MAX_DIAMETER_DP.toFloat(),
            )

            Text(
                "Stroke ${(ring.style.strokeRatio * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = ring.style.strokeRatio.toFloat(),
                onValueChange = { value -> onEdit { it.copy(strokeRatio = value.toDouble()) } },
                valueRange = ConfigDraft.MIN_STROKE_RATIO.toFloat()..ConfigDraft.MAX_STROKE_RATIO.toFloat(),
            )

            OutlinedTextField(
                value = ring.style.label,
                onValueChange = { value -> onEdit { it.copy(label = value) } },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ChipRow(
                title = "Label placement",
                values = LabelPlacement.entries,
                selected = ring.style.labelPlacement,
                label = { it.name.lowercase() },
                onPick = { placement -> onEdit { it.copy(labelPlacement = placement) } },
            )

            ColorRow(ring.style.color) { color -> onEdit { it.copy(color = color) } }
        }
    }
}

@Composable
private fun AddRingRow(used: Set<TimeCategory>, onAdd: (TimeCategory) -> Unit) {
    val available = TimeCategory.entries.filterNot { it in used }
    if (available.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Add a ring", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            available.forEach { category ->
                AssistChip(
                    onClick = { onAdd(category) },
                    label = { Text(category.label()) },
                )
            }
        }
    }
}

@Composable
private fun ColorRow(selected: String, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        COLOR_CHOICES.forEach { choice ->
            val selectedNow = choice == selected
            Box(
                Modifier
                    .size(32.dp)
                    .background(choice.toColor(scheme), CircleShape)
                    .border(
                        width = if (selectedNow) 3.dp else 1.dp,
                        color = if (selectedNow) scheme.onSurface else scheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onPick(choice) },
            )
        }
    }
}

/** Swatches for the roles the widget understands, plus a few fixed colours. */
private val COLOR_CHOICES = listOf(
    "theme:primary",
    "theme:secondary",
    "theme:tertiary",
    "theme:error",
    "theme:onSurface",
    "#E5484D",
    "#30A46C",
    "#3E63DD",
    "#F5A524",
)

private fun String.toColor(scheme: androidx.compose.material3.ColorScheme): Color = when (this) {
    "theme:primary" -> scheme.primary
    "theme:secondary" -> scheme.secondary
    "theme:tertiary" -> scheme.tertiary
    "theme:error" -> scheme.error
    "theme:onSurface" -> scheme.onSurface
    else -> runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(Color.Gray)
}

private fun TimeCategory.label(): String = when (this) {
    TimeCategory.DAYS -> "Days"
    TimeCategory.WEEKS -> "Weeks"
    TimeCategory.MONTHS -> "Months"
    TimeCategory.NET_FREE_HOURS -> "Free hours"
}

private fun ConfigProblem.label(): String = when (this) {
    ConfigProblem.NAME_BLANK -> "name is empty"
    ConfigProblem.NO_RINGS -> "add at least one ring"
    ConfigProblem.MISSING_DEADLINE -> "pick a deadline"
    ConfigProblem.DEADLINE_NOT_AFTER_START -> "the deadline is in the past"
    ConfigProblem.MISSING_PROFILE -> "pick a person"
}
