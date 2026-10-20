package dev.mementomori.lifeclock.model

import kotlinx.serialization.Serializable

/**
 * How a countdown is expressed. Each is its own unit, not four views of one number:
 * days/weeks are elapsed, months are calendar, net-free-hours is a budget.
 *
 * Lives in `:core:model` because persisted configs and the UI reference it too, while
 * `:core:time` must not be a dependency of either.
 */
@Serializable
enum class TimeCategory { DAYS, WEEKS, MONTHS, NET_FREE_HOURS }
