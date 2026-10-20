package dev.mementomori.lifeclock.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * T2.1 - these records are the on-disk format, so the tests are about the format, not the code.
 */
class WidgetRecordsSerializationTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    fun sample() = WidgetConfigRecord(
        configId = "cfg-1",
        name = "Project X",
        type = WidgetType.COUNTDOWN,
        startEpochMillis = 1_767_225_600_000,
        endEpochMillis = 1_770_000_000_000,
        profileRef = null,
        template = RingTemplate.BOLD,
        layout = WidgetLayout.CONCENTRIC,
        rings = listOf(
            RingConfigRecord(TimeCategory.DAYS),
            RingConfigRecord(TimeCategory.WEEKS, RingStyleRecord(strokeRatio = 0.3)),
        ),
        granularity = RefreshIntent.SIX_HOURLY,
    )

    test("round-trips without losing a field") {
        val decoded = json.decodeFromString<WidgetConfigRecord>(json.encodeToString(sample()))
        decoded shouldBe sample()
    }

    test("null optionals stay null") {
        val decoded = json.decodeFromString<WidgetConfigRecord>(
            json.encodeToString(sample().copy(endEpochMillis = null)),
        )
        decoded.endEpochMillis shouldBe null
    }

    test("enums persist as names, not ordinals") {
        // Ordinals would silently change meaning if an enum constant were reordered later.
        json.encodeToString(sample()) shouldContain "\"COUNTDOWN\""
        json.encodeToString(sample()) shouldContain "\"SIX_HOURLY\""
    }

    test("unknown keys are ignored, so an older build can read a newer file") {
        val raw = """{"glanceId":"g-1","configId":"cfg-1","addedInV2":"ignored"}"""
        json.decodeFromString<WidgetInstanceBinding>(raw).configId shouldBe "cfg-1"
    }
})
