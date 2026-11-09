package dev.mementomori.lifeclock.widget.render

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts how often Glance actually composed a widget, per placed instance.
 *
 * `GlanceAppWidget.update()` does not compose: it hands the request to a per-widget session and
 * returns. On the Pixel 8a (2026-10-27) those hand-offs were dropped at random - eight identical
 * draws 15 s apart produced five compositions, some for one widget, some for the other, and the
 * dropped ones left the widget on the frame it had before the user picked a config. Nothing threw
 * and nothing logged, so a draw has to be *observed*, not assumed.
 */
internal class CompositionProbe {

    private val counts = ConcurrentHashMap<Int, AtomicInteger>()

    /** Called from `provideGlance`: one composition started for [appWidgetId]. */
    fun mark(appWidgetId: Int) {
        counts.computeIfAbsent(appWidgetId) { AtomicInteger() }.incrementAndGet()
    }

    fun count(appWidgetId: Int): Int = counts[appWidgetId]?.get() ?: 0

    /** True when a composition started after the caller read [above]. */
    suspend fun awaitIncrease(appWidgetId: Int, above: Int, withinMs: Long): Boolean =
        withTimeoutOrNull(withinMs) {
            while (count(appWidgetId) <= above) delay(POLL_MS)
            true
        } ?: false

    private companion object {
        const val POLL_MS = 25L
    }
}

/** One probe for the process: sessions are process-wide, so the count has to be too. */
internal val processCompositions = CompositionProbe()

/**
 * A draw that keeps asking until Glance composes.
 *
 * The retry is not belt-and-braces: it is the only way to make the frame deterministic when the
 * hand-off is lossy. A draw that never composed is retried after the previous attempt has settled,
 * and the result is reported so a persistent failure shows up in the log instead of on the screen.
 */
internal class ComposedDraw(
    private val probe: CompositionProbe,
    private val attempts: Int = 3,
    private val settleMs: Long = 2_000,
) {

    /**
     * @param beforeRetry runs between attempts, for whatever makes the next hand-off land
     *   (see `drawFresh`: a session that is winding down swallows every event sent to it).
     * @return true when Glance started at least one composition for [appWidgetId].
     */
    suspend fun run(
        appWidgetId: Int,
        beforeRetry: suspend () -> Unit = {},
        update: suspend () -> Unit,
    ): Boolean {
        repeat(attempts) { attempt ->
            if (attempt > 0) beforeRetry()
            val before = probe.count(appWidgetId)
            update()
            if (probe.awaitIncrease(appWidgetId, before, settleMs)) return true
        }
        return false
    }
}
