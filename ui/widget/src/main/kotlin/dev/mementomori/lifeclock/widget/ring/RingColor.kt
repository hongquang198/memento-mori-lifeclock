package dev.mementomori.lifeclock.widget.ring

/** A colour as stored in a config: literal ARGB, or a theme role resolved at compose time. */
sealed interface RingColor {
    data class Fixed(val argb: Int) : RingColor
    data class Theme(val role: String) : RingColor

    companion object {
        private const val THEME_PREFIX = "theme:"

        /** Null when the string is neither `#RRGGBB` / `#AARRGGBB` nor `theme:<role>`. */
        fun parse(raw: String): RingColor? {
            val value = raw.trim()
            return when {
                value.startsWith(THEME_PREFIX) -> value.removePrefix(THEME_PREFIX)
                    .trim()
                    .lowercase()
                    .takeIf { it.isNotEmpty() }
                    ?.let(::Theme)

                value.startsWith("#") -> hex(value)?.let(::Fixed)
                else -> null
            }
        }

        private fun hex(value: String): Int? {
            val digits = value.removePrefix("#")
            if (digits.length != 6 && digits.length != 8) return null
            val parsed = digits.toLongOrNull(16) ?: return null
            // Six digits mean RRGGBB, so alpha defaults to opaque.
            return if (digits.length == 6) (0xFF000000L or parsed).toInt() else parsed.toInt()
        }
    }
}

/**
 * Reads a theme without Compose or Android, so colour resolution stays unit-testable.
 * Production supplies the Glance/Material palette; tests supply a map.
 */
fun interface ThemePalette {
    /** Null when the theme has no such role. */
    fun argbFor(role: String): Int?
}

object RingColors {

    /** Parses and resolves in one step; anything unparseable falls back instead of crashing a render. */
    fun resolve(raw: String, palette: ThemePalette, fallbackArgb: Int): Int =
        RingColor.parse(raw)?.let { resolve(it, palette, fallbackArgb) } ?: fallbackArgb

    fun resolve(color: RingColor, palette: ThemePalette, fallbackArgb: Int): Int = when (color) {
        is RingColor.Fixed -> color.argb
        is RingColor.Theme -> palette.argbFor(color.role) ?: fallbackArgb
    }
}
