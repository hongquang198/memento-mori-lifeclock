package dev.mementomori.lifeclock.model

/**
 * The appearance presets, as data.
 *
 * A template is only a *seed*: choosing one replaces the rings and layout, and editing any knob
 * afterwards flips [WidgetConfigRecord.template] to [RingTemplate.CUSTOM]. That is why this lives
 * in the model module - the editor, the tests and a future importer all read the same table.
 *
 * | template   | dia | stroke | label   | layout     | rings            |
 * |------------|-----|--------|---------|------------|------------------|
 * | CLASSIC    | 160 | 0.12   | UNDER   | SINGLE     | DAYS             |
 * | COMPACT    | 100 | 0.18   | NONE    | SINGLE     | DAYS             |
 * | BOLD       | 180 | 0.30   | INSIDE  | SINGLE     | DAYS             |
 * | MINIMAL    | 140 | 0.06   | NONE    | SINGLE     | DAYS             |
 * | CONCENTRIC | 180 | 0.10   | INSIDE  | CONCENTRIC | DAYS, WEEKS, MONTHS |
 * | GALLERY    | 120 | 0.12   | UNDER   | GRID       | DAYS, WEEKS      |
 */
object RingPresets {

    /** Throws for [RingTemplate.CUSTOM]: it describes "edited", it cannot produce a look. */
    fun rings(template: RingTemplate): List<RingConfigRecord> = when (template) {
        RingTemplate.CLASSIC -> listOf(ring(160, 0.12, LabelPlacement.UNDER))
        RingTemplate.COMPACT -> listOf(ring(100, 0.18, LabelPlacement.NONE))
        RingTemplate.BOLD -> listOf(ring(180, 0.30, LabelPlacement.INSIDE))
        RingTemplate.MINIMAL -> listOf(ring(140, 0.06, LabelPlacement.NONE))
        RingTemplate.CONCENTRIC -> listOf(
            ring(180, 0.10, LabelPlacement.INSIDE, TimeCategory.DAYS),
            ring(180, 0.10, LabelPlacement.INSIDE, TimeCategory.WEEKS),
            ring(180, 0.10, LabelPlacement.INSIDE, TimeCategory.MONTHS),
        )
        RingTemplate.GALLERY -> listOf(
            ring(120, 0.12, LabelPlacement.UNDER, TimeCategory.DAYS),
            ring(120, 0.12, LabelPlacement.UNDER, TimeCategory.WEEKS),
        )
        RingTemplate.CUSTOM -> error("CUSTOM is an outcome of editing, not a seed")
    }

    fun layout(template: RingTemplate): WidgetLayout = when (template) {
        RingTemplate.CLASSIC, RingTemplate.COMPACT, RingTemplate.BOLD, RingTemplate.MINIMAL ->
            WidgetLayout.SINGLE
        RingTemplate.CONCENTRIC -> WidgetLayout.CONCENTRIC
        RingTemplate.GALLERY -> WidgetLayout.GRID
        RingTemplate.CUSTOM -> WidgetLayout.SINGLE
    }

    /** Re-seeds [config] from [template], keeping identity and target. */
    fun apply(template: RingTemplate, config: WidgetConfigRecord): WidgetConfigRecord {
        if (template == RingTemplate.CUSTOM) return config
        return config.copy(
            template = template,
            rings = rings(template),
            layout = layout(template),
        )
    }

    private fun ring(
        diameterDp: Int,
        strokeRatio: Double,
        labelPlacement: LabelPlacement,
        category: TimeCategory = TimeCategory.DAYS,
    ) = RingConfigRecord(
        category = category,
        style = RingStyleRecord(
            diameterDp = diameterDp,
            strokeRatio = strokeRatio,
            labelPlacement = labelPlacement,
        ),
    )
}
