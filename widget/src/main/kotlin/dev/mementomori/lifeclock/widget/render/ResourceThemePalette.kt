package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.widget.R

import android.content.Context
import dev.mementomori.lifeclock.widget.ring.ThemePalette

/**
 * Maps `theme:<role>` to the widget's own colour resources.
 *
 * The bitmap is rasterized before composition, where `GlanceTheme.colors` does not exist yet, so
 * the roles must be resolvable from a plain [Context].
 */
class ResourceThemePalette(private val context: Context) : ThemePalette {
    override fun argbFor(role: String): Int? = when (role) {
        "primary" -> context.getColor(R.color.widget_primary)
        "secondary" -> context.getColor(R.color.widget_secondary)
        "outline" -> context.getColor(R.color.widget_outline)
        "surface" -> context.getColor(R.color.widget_surface)
        "on_surface" -> context.getColor(R.color.widget_on_surface)
        "error" -> context.getColor(R.color.widget_error)
        else -> null
    }
}
