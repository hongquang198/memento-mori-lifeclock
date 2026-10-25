package dev.mementomori.lifeclock.widget.config

import dev.mementomori.lifeclock.data.InMemoryLifeClockStore
import dev.mementomori.lifeclock.model.RefreshIntent
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.mementomori.lifeclock.widget.TestDependencies

/**
 * T2.15 — the picker's rules, without Android.
 *
 * The Activity is a shell: it reads an intent and shows a list. Everything that decides *what* is
 * offered and *what* a choice means lives here, so it can be tested without a device.
 */
class ConfigPickerTest {

    private val store = InMemoryLifeClockStore()
    private val picker = ConfigPicker(store.configs, store.bindings, TestDependencies(store).clock)

    private suspend fun existing(id: String) = WidgetConfigRecord(
        configId = id,
        name = id,
        type = WidgetType.COUNTDOWN,
        startEpochMillis = 0,
        endEpochMillis = 1,
        granularity = RefreshIntent.DAILY,
    ).also { store.configs.save(it) }

    @Test
    fun `an empty catalog is seeded with one countdown that has a ring`() = runBlocking {
        val seed = picker.seedIfEmpty("First year")

        assertNotNull(seed)
        val catalog = picker.catalog()
        assertEquals(1, catalog.size)
        assertEquals("First year", catalog.single().name)
        assertEquals(WidgetType.COUNTDOWN, catalog.single().type)
        assertEquals(RefreshIntent.DAILY, catalog.single().granularity)
        assertTrue(catalog.single().rings.isNotEmpty())
        assertTrue(catalog.single().endEpochMillis!! > catalog.single().startEpochMillis)
    }

    @Test
    fun `seeding twice still leaves one config`() = runBlocking {
        picker.seedIfEmpty("First year")
        val second = picker.seedIfEmpty("First year")

        assertNull(second)
        assertEquals(1, picker.catalog().size)
    }

    @Test
    fun `a catalog with configs is never seeded`() = runBlocking {
        existing("mine")

        assertNull(picker.seedIfEmpty("First year"))
        assertEquals(listOf("mine"), picker.catalog().map { it.configId })
    }

    @Test
    fun `choosing binds the instance to the config`() = runBlocking {
        existing("mine")

        picker.choose(glanceId = "17", configId = "mine")

        val binding = store.bindings.observeBinding("17").first()
        assertEquals("mine", binding?.configId)
    }

    @Test
    fun `the current config is reported for a placed widget`() = runBlocking {
        existing("mine")

        assertNull(picker.currentConfig("17"))
        picker.choose(glanceId = "17", configId = "mine")
        assertEquals("mine", picker.currentConfig("17"))
    }

    @Test
    fun `rebinding the same instance moves it instead of duplicating it`() = runBlocking {
        existing("one")
        existing("two")

        picker.choose(glanceId = "17", configId = "one")
        picker.choose(glanceId = "17", configId = "two")

        assertEquals(1, store.bindings.observeAllBindings().first().size)
        assertEquals("two", store.bindings.observeBinding("17").first()?.configId)
    }
}
