package dev.mementomori.lifeclock.model.editor

import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.RingPresets
import dev.mementomori.lifeclock.model.RingStyleRecord
import dev.mementomori.lifeclock.model.RingTemplate
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetLayout
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.model.hasDrawableRing

/** Why a config cannot be saved. Named so the editor can point at the field, not a toast. */
enum class ConfigProblem {
    NAME_BLANK,
    NO_RINGS,

    /** Rings exist, but every one asks for a category no engine emits: nothing could ever draw. */
    NO_DRAWABLE_RINGS,
    MISSING_DEADLINE,
    DEADLINE_NOT_AFTER_START,
    MISSING_PROFILE,
}

/**
 * A config being edited.
 *
 * Every setter is a pure transformation, so the editor's behaviour is testable without Compose -
 * the same split the picker uses. One rule runs through the **look** knobs: editing any of them
 * makes the config [RingTemplate.CUSTOM]. A preset describes an untouched look; once the user
 * moves a slider, calling it "Classic" would be a lie. Schedule and identity knobs (name, type,
 * start, deadline, person) are not the look and leave the preset intact.
 */
data class ConfigDraft(val value: WidgetConfigRecord) {

    fun named(name: String): ConfigDraft = copy(value = value.copy(name = name))

    fun withType(type: WidgetType): ConfigDraft = copy(value = value.copy(type = type))

    /**
     * The reference the arc's `total` is measured from: "this countdown started on the 1st".
     *
     * Like the deadline, this is schedule, not appearance, so it leaves the preset alone - and it
     * must stay before the deadline, or the arc has no positive span to be a fraction of.
     */
    fun withStart(startEpochMillis: Long): ConfigDraft =
        copy(value = value.copy(startEpochMillis = startEpochMillis))

    fun withDeadline(endEpochMillis: Long?): ConfigDraft =
        copy(value = value.copy(endEpochMillis = endEpochMillis))

    fun withProfile(profileId: String?): ConfigDraft = copy(value = value.copy(profileRef = profileId))

    /** Choosing a preset is the one edit that is *not* custom: it replaces the look wholesale. */
    fun withTemplate(template: RingTemplate): ConfigDraft =
        copy(value = RingPresets.apply(template, value))

    fun withLayout(layout: WidgetLayout): ConfigDraft = edited(value.copy(layout = layout))

    /**
     * Adds or removes a category. A new ring inherits the first ring's look, so adding "weeks" to a
     * bold widget does not silently produce a thin default ring next to it.
     */
    fun withCategory(category: TimeCategory, enabled: Boolean): ConfigDraft {
        // A ring nobody can draw is not a choice, it is a trap: it saves fine and renders empty.
        if (!category.emitted) return this

        val existing = value.rings.any { it.category == category }
        val rings = when {
            enabled && !existing -> value.rings + RingConfigRecord(
                category = category,
                style = value.rings.firstOrNull()?.style ?: RingStyleRecord(),
            )
            !enabled && existing -> value.rings.filterNot { it.category == category }
            else -> return this
        }
        return edited(value.copy(rings = rings))
    }

    /** Edits one ring's style, clamped so the renderer never receives an impossible ring. */
    fun withStyle(index: Int, transform: (RingStyleRecord) -> RingStyleRecord): ConfigDraft {
        if (index !in value.rings.indices) return this
        val rings = value.rings.toMutableList()
        rings[index] = rings[index].copy(style = transform(rings[index].style).clamped())
        return edited(value.copy(rings = rings))
    }

    fun validate(): List<ConfigProblem> = buildList {
        if (value.name.isBlank()) add(ConfigProblem.NAME_BLANK)
        if (value.rings.isEmpty()) add(ConfigProblem.NO_RINGS)
        if (value.rings.isNotEmpty() && !value.hasDrawableRing) {
            add(ConfigProblem.NO_DRAWABLE_RINGS)
        }

        when (value.type) {
            WidgetType.COUNTDOWN -> {
                val end = value.endEpochMillis
                when {
                    end == null -> add(ConfigProblem.MISSING_DEADLINE)
                    end <= value.startEpochMillis -> add(ConfigProblem.DEADLINE_NOT_AFTER_START)
                }
            }

            WidgetType.LIFE_CLOCK -> {
                if (value.profileRef.isNullOrBlank()) add(ConfigProblem.MISSING_PROFILE)
            }
        }
    }

    fun isValid(): Boolean = validate().isEmpty()

    /**
     * Drops rings no engine emits, keeping one drawable ring if that empties the list.
     *
     * Opening the editor is the repair path for configs saved before this rule existed. The
     * substitute keeps the first ring's style, so the look survives and the user is not asked to
     * rebuild it from a blank draft.
     */
    fun sanitized(): ConfigDraft {
        val rings = value.rings.filter { it.category.emitted }
        if (rings.isNotEmpty()) return copy(value = value.copy(rings = rings))

        val style = value.rings.firstOrNull()?.style ?: RingStyleRecord()
        // Days, not `entries.first()`: the repair must not silently change when a unit is added.
        return copy(value = value.copy(rings = listOf(RingConfigRecord(TimeCategory.DAYS, style))))
    }

    /** Marks the look as edited. Identity, target and type are preserved. */
    private fun edited(next: WidgetConfigRecord): ConfigDraft =
        copy(value = next.copy(template = RingTemplate.CUSTOM))

    companion object {
        const val MIN_DIAMETER_DP = 60
        const val MAX_DIAMETER_DP = 240
        const val MIN_STROKE_RATIO = 0.05
        const val MAX_STROKE_RATIO = 0.5

        /** Bounds live here, not in the sliders: a loaded config can be out of range too. */
        fun RingStyleRecord.clamped(): RingStyleRecord = copy(
            diameterDp = diameterDp.coerceIn(MIN_DIAMETER_DP, MAX_DIAMETER_DP),
            strokeRatio = strokeRatio.coerceIn(MIN_STROKE_RATIO, MAX_STROKE_RATIO),
        )
    }
}
