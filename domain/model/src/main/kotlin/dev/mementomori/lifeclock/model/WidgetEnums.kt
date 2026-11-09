package dev.mementomori.lifeclock.model

import kotlinx.serialization.Serializable

/** What a configuration counts down to. */
@Serializable
enum class WidgetType { COUNTDOWN, LIFE_CLOCK }

/** How often the arc needs redrawing. The Chronometer text ticks independently. */
@Serializable
enum class RefreshIntent { HOURLY, SIX_HOURLY, DAILY }

/** How multiple arcs are arranged inside one widget. */
@Serializable
enum class WidgetLayout { SINGLE, STACK, GRID, CONCENTRIC }

/** Where a ring's label is drawn relative to the arc. */
@Serializable
enum class LabelPlacement { INSIDE, UNDER, NONE }

/** Appearance presets. `CUSTOM` means "seeded from a preset, then edited". */
@Serializable
enum class RingTemplate { CLASSIC, COMPACT, BOLD, MINIMAL, CONCENTRIC, GALLERY, CUSTOM }
