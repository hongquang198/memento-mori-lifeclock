package dev.mementomori.lifeclock.time

import dev.mementomori.lifeclock.model.TimeCategory
import java.text.NumberFormat
import java.util.Locale

/**
 * A count in its own unit: "66 days", "10 weeks", "3 months", "1,569 hours".
 *
 * The widget's live `Chronometer` can only render `H:MM:SS`, so it is honest only inside the last
 * day (see `WidgetSpecResolver`). Above that, a 65-day countdown would read "1568:37:23" - measured
 * on device - which is both unreadable and unit-blind. The count passed here is the same
 * [TimeSlice.remaining] the arc was drawn from, so the text and the ring cannot disagree.
 */
object CountdownLabel {

    fun of(count: Long, category: TimeCategory, locale: Locale = Locale.getDefault()): String {
        val units = count.coerceAtLeast(0)
        val unit = when (category) {
            TimeCategory.DAYS -> "day"
            TimeCategory.WEEKS -> "week"
            TimeCategory.MONTHS -> "month"
            TimeCategory.NET_FREE_HOURS -> "hour"
        }
        val suffix = if (units == 1L) unit else "${unit}s"
        return "${NumberFormat.getIntegerInstance(locale).format(units)} $suffix"
    }
}
