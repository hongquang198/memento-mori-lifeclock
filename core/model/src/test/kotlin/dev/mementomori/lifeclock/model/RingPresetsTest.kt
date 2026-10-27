package dev.mementomori.lifeclock.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * T4.1 - one assertion block per preset, pinning the table in [RingPresets]' KDoc.
 *
 * A preset is a promise about what the user will see; if a number moves, that promise changed and
 * the diff must say so.
 */
class RingPresetsTest : StringSpec({

    fun styleOf(template: RingTemplate) = RingPresets.rings(template).first().style

    "classic is one wide ring with an under-label" {
        val style = styleOf(RingTemplate.CLASSIC)
        style.diameterDp shouldBe 160
        style.strokeRatio shouldBe 0.12
        style.labelPlacement shouldBe LabelPlacement.UNDER
        RingPresets.layout(RingTemplate.CLASSIC) shouldBe WidgetLayout.SINGLE
        RingPresets.rings(RingTemplate.CLASSIC).size shouldBe 1
    }

    "compact is small, thick and unlabelled" {
        val style = styleOf(RingTemplate.COMPACT)
        style.diameterDp shouldBe 100
        style.strokeRatio shouldBe 0.18
        style.labelPlacement shouldBe LabelPlacement.NONE
    }

    "bold is large, thick and labelled inside" {
        val style = styleOf(RingTemplate.BOLD)
        style.diameterDp shouldBe 180
        style.strokeRatio shouldBe 0.30
        style.labelPlacement shouldBe LabelPlacement.INSIDE
    }

    "minimal is a thin hairline" {
        val style = styleOf(RingTemplate.MINIMAL)
        style.strokeRatio shouldBe 0.06
        style.labelPlacement shouldBe LabelPlacement.NONE
    }

    "concentric seeds three categories in a nested layout" {
        RingPresets.rings(RingTemplate.CONCENTRIC).map { it.category } shouldBe
            listOf(TimeCategory.DAYS, TimeCategory.WEEKS, TimeCategory.MONTHS)
        RingPresets.layout(RingTemplate.CONCENTRIC) shouldBe WidgetLayout.CONCENTRIC
        RingPresets.rings(RingTemplate.CONCENTRIC).map { it.style.strokeRatio }.distinct() shouldBe listOf(0.10)
    }

    "gallery seeds two rings in a grid" {
        RingPresets.rings(RingTemplate.GALLERY).map { it.category } shouldBe
            listOf(TimeCategory.DAYS, TimeCategory.WEEKS)
        RingPresets.layout(RingTemplate.GALLERY) shouldBe WidgetLayout.GRID
    }

    "custom is an outcome, not a seed" {
        shouldThrow<IllegalStateException> { RingPresets.rings(RingTemplate.CUSTOM) }
    }

    "applying a preset replaces appearance and keeps identity and target" {
        val original = WidgetConfigRecord(
            configId = "c1",
            name = "Mine",
            type = WidgetType.LIFE_CLOCK,
            startEpochMillis = 1_000,
            profileRef = "p1",
            rings = listOf(RingConfigRecord(TimeCategory.DAYS)),
        )

        val seeded = RingPresets.apply(RingTemplate.CONCENTRIC, original)

        seeded.configId shouldBe "c1"
        seeded.name shouldBe "Mine"
        seeded.profileRef shouldBe "p1"
        seeded.startEpochMillis shouldBe 1_000
        seeded.template shouldBe RingTemplate.CONCENTRIC
        seeded.layout shouldBe WidgetLayout.CONCENTRIC
        seeded.rings.size shouldBe 3
    }

    "applying custom changes nothing" {
        val original = WidgetConfigRecord(
            configId = "c1",
            name = "Mine",
            type = WidgetType.COUNTDOWN,
            startEpochMillis = 0,
        )
        RingPresets.apply(RingTemplate.CUSTOM, original) shouldBe original
    }
})
