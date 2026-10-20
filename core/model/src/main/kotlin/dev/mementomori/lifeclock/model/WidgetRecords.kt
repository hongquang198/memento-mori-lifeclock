package dev.mementomori.lifeclock.model

import kotlinx.serialization.Serializable

/**
 * Appearance of one arc.
 *
 * Colours are strings (`"#RRGGBB"` or `"theme:primary"`) so the record stays a pure value type:
 * resolving a theme role needs Android, and that happens in `:widget` at render time.
 */
@Serializable
data class RingStyleRecord(
    val diameterDp: Int = 160,
    val strokeRatio: Double = 0.12,
    val color: String = "theme:primary",
    val trackColor: String = "theme:outline",
    val label: String = "",
    val labelPlacement: LabelPlacement = LabelPlacement.UNDER,
    val startAngleDeg: Float = -90f,
)

/** One arc: which statistic, drawn how. */
@Serializable
data class RingConfigRecord(
    val category: TimeCategory,
    val style: RingStyleRecord = RingStyleRecord(),
)

/**
 * A named, reusable configuration authored **in the app**.
 *
 * A placed widget never owns settings; it points at one of these via [WidgetInstanceBinding].
 * That is why two widgets can share one config and both update when it is edited.
 */
@Serializable
data class WidgetConfigRecord(
    val configId: String,
    val name: String,
    val type: WidgetType,
    val startEpochMillis: Long,
    /** Countdown deadline. Null for [WidgetType.LIFE_CLOCK]. */
    val endEpochMillis: Long? = null,
    /** Profile key. Null for [WidgetType.COUNTDOWN]. */
    val profileRef: String? = null,
    val template: RingTemplate = RingTemplate.CLASSIC,
    val layout: WidgetLayout = WidgetLayout.SINGLE,
    val rings: List<RingConfigRecord> = emptyList(),
    val granularity: RefreshIntent = RefreshIntent.DAILY,
)

/** A placed widget instance pointing at the config it renders. */
@Serializable
data class WidgetInstanceBinding(
    /** Glance's id for the placed instance, as a string so no Android type leaks in here. */
    val glanceId: String,
    val configId: String,
)
