package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.mortality.LifeStat
import java.text.NumberFormat

/**
 * The number under a life-clock ring: "14,367 days".
 *
 * Grouping follows the user's locale, so this is presentation only - the count itself comes from
 * the engine, already rounded up.
 */
object LifeStatText {

    fun of(stat: LifeStat): String {
        val count = NumberFormat.getIntegerInstance().format(stat.remaining)
        return "$count ${unit(stat.category, stat.remaining)}"
    }

    private fun unit(category: TimeCategory, count: Long): String {
        val (singular, plural) = when (category) {
            TimeCategory.DAYS -> "day" to "days"
            TimeCategory.WEEKS -> "week" to "weeks"
            TimeCategory.MONTHS -> "month" to "months"
            TimeCategory.NET_FREE_HOURS -> "hour" to "hours"
        }
        return if (count == 1L) singular else plural
    }
}
