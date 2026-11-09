package dev.mementomori.lifeclock.time

import java.time.Duration
import java.time.Instant

/**
 * T1.0 test fixture: a clock frozen at whatever instant the test says.
 *
 * Lives in `src/test` on purpose — the production artifact must not be able to move time.
 */
class FakeClock(private var instant: Instant) : Clock {

    override fun now(): Instant = instant

    /** Moves time forward, which is how a test expresses "a day later". */
    fun advanceBy(duration: Duration) {
        instant = instant.plus(duration)
    }
}
