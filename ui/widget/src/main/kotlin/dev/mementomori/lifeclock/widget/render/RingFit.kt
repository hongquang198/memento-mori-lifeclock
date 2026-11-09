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

/** The 4 dp padding on each side of [WidgetContent]'s column. */
internal const val CELL_PADDING_DP: Int = 8

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

/**
 * The label's font size for a ring of [ringSizeDp].
 *
 * A fixed 13 sp reads well on a 97 dp ring and overflows a 1x1 one ("10 weeks" is wider than a
 * 32 dp circle). Scaling with the ring keeps the number inside the arc at every cell size the
 * launcher allows, down to a floor that is still legible.
 */
internal fun labelFontSp(ringSizeDp: Int): Float =
    (ringSizeDp * 0.15f).coerceIn(9f, 15f)
