package dev.mementomori.lifeclock.model

import kotlinx.serialization.Serializable

/**
 * How a ring is measured. Each is its own unit, not several views of one number:
 * hours/days/weeks are elapsed, months are calendar, net-free-hours is a budget.
 *
 * [emitted] is the contract between authoring and rendering: the editor offers only categories an
 * engine actually produces, and a config left with none of them is refused. Offering a category
 * nobody emits is how a widget ends up asking the user to "finish setting this up" with nothing
 * left to set up - it saved fine, then rendered as an empty spec.
 *
 * Lives in `:core:model` because persisted configs and the UI reference it too, while
 * `:core:time` must not be a dependency of either.
 */
@Serializable
enum class TimeCategory(val emitted: Boolean) {
    HOURS(true),
    DAYS(true),
    WEEKS(true),
    MONTHS(true),

    /** Needs the ATUS time-use split (T3.7/T3.8): flip to true in the change that emits it. */
    NET_FREE_HOURS(false),
}
