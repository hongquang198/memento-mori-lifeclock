package dev.mementomori.lifeclock.model.editor

import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.RingTemplate
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetLayout
import dev.mementomori.lifeclock.model.WidgetType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/** T4.2 / T4.9 (decision half) - what each edit does, including the CUSTOM rule and the clamps. */
class ConfigDraftTest : StringSpec({

    fun draft(type: WidgetType = WidgetType.COUNTDOWN) = ConfigDraft(
        WidgetConfigRecord(
            configId = "c1",
            name = "Mine",
            type = type,
            startEpochMillis = 1_000,
            endEpochMillis = 2_000,
            profileRef = if (type == WidgetType.LIFE_CLOCK) "p1" else null,
            rings = listOf(
                RingConfigRecord(TimeCategory.DAYS),
            ),
        ),
    )

    "choosing a preset replaces the look without marking it custom" {
        val seeded = draft().withTemplate(RingTemplate.CONCENTRIC)

        seeded.value.template shouldBe RingTemplate.CONCENTRIC
        seeded.value.layout shouldBe WidgetLayout.CONCENTRIC
        seeded.value.rings.size shouldBe 3
    }

    "editing any knob marks the config custom" {
        draft()
            .withLayout(WidgetLayout.GRID)
            .value.template shouldBe RingTemplate.CUSTOM

        draft()
            .withStyle(0) { it.copy(diameterDp = 200) }
            .value.template shouldBe RingTemplate.CUSTOM

        draft()
            .withCategory(TimeCategory.WEEKS, enabled = true)
            .value.template shouldBe RingTemplate.CUSTOM
    }

    "adding a category inherits the existing ring's look" {
        val styled = draft().withStyle(0) { it.copy(diameterDp = 200, strokeRatio = 0.3) }
        val both = styled.withCategory(TimeCategory.WEEKS, enabled = true)

        both.value.rings.map { it.category } shouldBe listOf(TimeCategory.DAYS, TimeCategory.WEEKS)
        both.value.rings[1].style.diameterDp shouldBe 200
        both.value.rings[1].style.strokeRatio shouldBe 0.3
    }

    "adding an already present category changes nothing" {
        val once = draft().withCategory(TimeCategory.DAYS, enabled = true)
        once.value.rings.size shouldBe 1
    }

    "removing a category drops its ring" {
        val both = draft().withCategory(TimeCategory.WEEKS, enabled = true)
        both.withCategory(TimeCategory.WEEKS, enabled = false).value.rings.map { it.category } shouldBe
            listOf(TimeCategory.DAYS)
    }

    "style edits are clamped, so a loaded out-of-range config is repaired" {
        val wide = draft().withStyle(0) { it.copy(diameterDp = 10_000, strokeRatio = 9.0) }

        wide.value.rings[0].style.diameterDp shouldBe ConfigDraft.MAX_DIAMETER_DP
        wide.value.rings[0].style.strokeRatio shouldBe ConfigDraft.MAX_STROKE_RATIO

        val thin = draft().withStyle(0) { it.copy(diameterDp = 1, strokeRatio = 0.0) }
        thin.value.rings[0].style.diameterDp shouldBe ConfigDraft.MIN_DIAMETER_DP
        thin.value.rings[0].style.strokeRatio shouldBe ConfigDraft.MIN_STROKE_RATIO
    }

    "an out-of-range ring index is ignored rather than crashing" {
        draft().withStyle(7) { it.copy(diameterDp = 200) }.value.rings.size shouldBe 1
    }

    "a countdown needs a deadline after its start" {
        draft().withDeadline(null).validate().shouldContain(ConfigProblem.MISSING_DEADLINE)
        draft().withDeadline(1_000).validate()
            .shouldContain(ConfigProblem.DEADLINE_NOT_AFTER_START)
        draft().validate().shouldBeEmpty()
    }

    "a life clock needs a profile" {
        draft(WidgetType.LIFE_CLOCK).withProfile(null).validate()
            .shouldContain(ConfigProblem.MISSING_PROFILE)
        draft(WidgetType.LIFE_CLOCK).validate().shouldBeEmpty()
    }

    "a blank name and an empty ring list are refused" {
        draft().named("  ").validate().shouldContain(ConfigProblem.NAME_BLANK)
        draft().withCategory(TimeCategory.DAYS, enabled = false).validate()
            .shouldContain(ConfigProblem.NO_RINGS)
    }
})
