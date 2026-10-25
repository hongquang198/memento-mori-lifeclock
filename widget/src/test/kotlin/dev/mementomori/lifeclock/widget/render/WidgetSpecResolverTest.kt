package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.time.Clock
import dev.mementomori.lifeclock.time.DefaultCountdownModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T2.8 (decision half) - which configs are renderable, and what fraction each arc gets. */
class WidgetSpecResolverTest {

    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val resolver = WidgetSpecResolver(
        countdownModel = DefaultCountdownModel(),
        clock = Clock { now },
        zone = ZoneId.of("UTC"),
    )

    private fun config(
        start: Instant = now.minus(Duration.ofDays(5)),
        end: Instant? = now.plus(Duration.ofDays(5)),
        type: WidgetType = WidgetType.COUNTDOWN,
        rings: List<RingConfigRecord> = listOf(RingConfigRecord(TimeCategory.DAYS)),
    ) = WidgetConfigRecord(
        configId = "c1",
        name = "Half way",
        type = type,
        startEpochMillis = start.toEpochMilli(),
        endEpochMillis = end?.toEpochMilli(),
        rings = rings,
    )

    @Test
    fun `an arc is drawn at the fraction of the countdown that remains`() {
        val spec = resolver.resolve(config())!!
        assertEquals(0.5, spec.rings.single().fraction, 1e-9)
    }

    @Test
    fun `remaining millis drives the live chronometer`() {
        assertEquals(Duration.ofDays(5).toMillis(), resolver.resolve(config())!!.remainingMillis)
    }

    @Test
    fun `a passed deadline reads as zero rather than negative`() {
        val spec = resolver.resolve(
            config(start = now.minus(Duration.ofDays(10)), end = now.minus(Duration.ofDays(2))),
        )!!
        assertEquals(0L, spec.remainingMillis)
        assertEquals(0.0, spec.rings.single().fraction, 1e-9)
    }

    @Test
    fun `each ring keeps its own category and style`() {
        val rings = listOf(
            RingConfigRecord(TimeCategory.DAYS),
            RingConfigRecord(TimeCategory.WEEKS),
        )
        val spec = resolver.resolve(config(rings = rings))!!
        assertEquals(listOf(TimeCategory.DAYS, TimeCategory.WEEKS), spec.rings.map { it.config.category })
    }

    @Test
    fun `a life clock is not renderable until profiles exist`() {
        assertNull(resolver.resolve(config(type = WidgetType.LIFE_CLOCK)))
    }

    @Test
    fun `a countdown with no deadline is not renderable`() {
        assertNull(resolver.resolve(config(end = null)))
    }

    @Test
    fun `a ring whose category produced no slice is dropped`() {
        // NET_FREE_HOURS needs a policy; with none the model emits no slice for it.
        val spec = resolver.resolve(config(rings = listOf(RingConfigRecord(TimeCategory.NET_FREE_HOURS))))
        assertNull(spec)
    }
}
