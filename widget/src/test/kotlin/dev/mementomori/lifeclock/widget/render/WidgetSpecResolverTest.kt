package dev.mementomori.lifeclock.widget.render

import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.Sex
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import dev.mementomori.lifeclock.mortality.CsvLifeTableSource
import dev.mementomori.lifeclock.mortality.LifeStatsEngine
import dev.mementomori.lifeclock.time.Clock
import dev.mementomori.lifeclock.time.DefaultCountdownModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T2.8 (decision half) + M3 - which configs are renderable, and what fraction each arc gets. */
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
        profileRef = if (type == WidgetType.LIFE_CLOCK) "p1" else null,
        rings = rings,
    )

    /** The real engine over the real table: a stub here would let a broken asset pass. */
    private fun snapshot() = LifeStatsEngine(CsvLifeTableSource.bundled()).compute(
        PersonProfile(
            profileId = "p1",
            name = "Test",
            birthEpochDay = LocalDate.of(1986, 1, 1).toEpochDay(),
            sex = Sex.MALE,
            countryIso3 = "USA",
        ),
        now,
        ZoneId.of("UTC"),
    )!!

    @Test
    fun `an arc is drawn at the fraction of the countdown that remains`() {
        val spec = resolver.resolve(config())!!
        assertEquals(0.5, spec.rings.single().fraction, 1e-9)
    }

    @Test
    fun `remaining millis drives the live chronometer`() {
        assertEquals(Duration.ofDays(5).toMillis(), resolver.resolve(config())!!.remainingMillis!!)
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
    fun `a life clock without a profile snapshot is not renderable`() {
        assertNull(resolver.resolve(config(type = WidgetType.LIFE_CLOCK)))
    }

    @Test
    fun `a life clock draws the engine's fraction and has no chronometer`() {
        val snapshot = snapshot()
        val spec = resolver.resolve(config(type = WidgetType.LIFE_CLOCK), snapshot)!!

        // The resolver maps, it never recomputes: one engine, one fraction, every ring.
        assertEquals(
            snapshot.stats.getValue(TimeCategory.DAYS).fractionRemaining,
            spec.rings.single().fraction,
            0.0,
        )
        // A zero here would render a stopwatch counting up from the last draw.
        assertNull(spec.remainingMillis)
    }

    @Test
    fun `a life clock asking for an uncomputed category is not renderable`() {
        val spec = resolver.resolve(
            config(type = WidgetType.LIFE_CLOCK, rings = listOf(RingConfigRecord(TimeCategory.NET_FREE_HOURS))),
            snapshot(),
        )
        assertNull(spec)
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
