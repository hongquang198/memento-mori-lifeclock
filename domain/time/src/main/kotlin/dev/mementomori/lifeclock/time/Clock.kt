package dev.mementomori.lifeclock.time

import java.time.Instant

/**
 * The single place the current time enters the system.
 *
 * Why this exists at all: a countdown engine that calls [Instant.now] internally cannot be
 * tested. Every boundary takes a `Clock` instead, so a test can decide what "now" means and the
 * result is exact rather than flaky.
 *
 * Production passes [systemUTC]; tests pass `FakeClock`.
 */
fun interface Clock {

    /** The current instant. Must be monotonic in practice, but tests may move it anywhere. */
    fun now(): Instant

    companion object {
        /**
         * The production clock (wall-clock UTC).
         *
         * Stored as a function, not a singleton object, so it can be replaced wholesale in a
         * test without any global state to reset.
         */
        fun systemUTC(): Clock = Clock { Instant.now() }
    }
}
