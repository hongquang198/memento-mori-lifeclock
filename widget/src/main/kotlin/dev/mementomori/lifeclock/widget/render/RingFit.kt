package dev.mementomori.lifeclock.widget.render

/**
 * The width a placed instance is allowed to draw in, in dp. Null when the platform has no answer,
 * in which case the config's own diameter is used unchanged.
 */
internal fun appWidgetWidthDp(context: android.content.Context, appWidgetId: Int): Int? =
    android.appwidget.AppWidgetManager.getInstance(context)
        .getAppWidgetOptions(appWidgetId)
        .getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        .takeIf { it > 0 }

/** The 8 dp padding on each side of [WidgetContent]'s column. */
internal const val CELL_PADDING_DP: Int = 16

/** Below this a ring is a dot; better a slightly clipped ring than an unreadable one. */
internal const val MIN_RING_DP: Int = 32

/**
 * A ring never draws wider than the cell it lives in.
 *
 * Measured on a Pixel 8a: a 2-column cell is ~114 dp, the default ring is 160 dp, so the arc was
 * clipped on both sides and the widget looked broken. The config keeps its own diameter - this only
 * caps the draw, so a bigger cell still uses the full size and the stored config is never rewritten.
 */
internal fun fitDiameterDp(configuredDp: Int, cellWidthDp: Int?): Int {
    if (cellWidthDp == null) return configuredDp
    val room = (cellWidthDp - CELL_PADDING_DP).coerceAtLeast(MIN_RING_DP)
    return minOf(configuredDp, room)
}
