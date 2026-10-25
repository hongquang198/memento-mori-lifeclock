package dev.mementomori.lifeclock.widget.config

import dev.mementomori.lifeclock.widget.R

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Looper
import android.widget.ListView
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dev.mementomori.lifeclock.data.InMemoryLifeClockStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import dev.mementomori.lifeclock.widget.TestDependencies
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder
import dev.mementomori.lifeclock.widget.refresh.WidgetRefreshScheduler

/**
 * T2.15 — the Activity's half of the contract: the widget id comes in, a binding and a result go
 * out, and cancelling writes nothing.
 *
 * The rules themselves are covered by [ConfigPickerTest]; this only proves the wiring, which is
 * the part the launcher actually talks to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConfigActivityTest {

    private val store = InMemoryLifeClockStore()

    @Before
    fun publish() {
        WidgetDependenciesHolder.current = TestDependencies(store)
        // The picker asks for a frame through WorkManager, which needs an explicit test instance.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
        )
    }

    @After
    fun unpublish() {
        WidgetDependenciesHolder.current = null
    }

    /** The Activity does its work in `lifecycleScope`, so the looper needs draining. */
    private fun settle() = repeat(20) { Shadows.shadowOf(Looper.getMainLooper()).idle() }

    private fun launch(id: Int?) = Robolectric
        .buildActivity(ConfigActivity::class.java, intent(id))
        .setup()
        .get()

    private fun intent(id: Int?) = Intent(
        ApplicationProvider.getApplicationContext(),
        ConfigActivity::class.java,
    ).apply { if (id != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) }

    @Test
    fun `the tap intent carries the widget id and the configure action`() {
        val intent = configureIntent(ApplicationProvider.getApplicationContext(), 17)

        assertEquals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, intent.action)
        assertEquals(ConfigActivity::class.java.name, intent.component?.className)
        assertEquals(17, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test
    fun `choosing a config binds it and returns the widget id`() {
        val activity = launch(17)
        settle()

        val list = activity.findViewById<ListView>(android.R.id.list)
        assertNotNull("the picker must show the catalog", list)
        assertEquals("an empty catalog is seeded first", 1, list.adapter.count)

        list.performItemClick(null, 0, 0)
        settle()

        val shadow = Shadows.shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)
        assertEquals(
            17,
            shadow.resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
        )
        assertTrue(activity.isFinishing)
        assertEquals(
            ConfigPicker.SEED_CONFIG_ID,
            runBlocking { store.bindings.observeBinding("17").first() }?.configId,
        )
        assertEquals(
            "a frame must be requested, or the widget shows the placeholder until the heartbeat",
            1,
            WorkManager.getInstance(activity)
                .getWorkInfosForUniqueWork(WidgetRefreshScheduler.UNIQUE_IMMEDIATE)
                .get()
                .size,
        )
    }

    @Test
    fun `leaving without choosing writes nothing`() {
        val activity = launch(17)
        settle()

        activity.finish()
        settle()

        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).resultCode)
        assertTrue(runBlocking { store.bindings.observeAllBindings().first() }.isEmpty())
    }

    @Test
    fun `reconfiguring marks the config already in force`() {
        val first = launch(17)
        settle()
        first.findViewById<ListView>(android.R.id.list).performItemClick(null, 0, 0)
        settle()

        val again = launch(17)
        settle()

        val list = again.findViewById<ListView>(android.R.id.list)
        assertEquals(
            again.getString(R.string.config_selected, "First year"),
            list.adapter.getItem(0),
        )
    }

    @Test
    fun `a config deleted while the picker is open cancels instead of crashing`() {
        val activity = launch(17)
        settle()
        runBlocking { store.configs.delete(ConfigPicker.SEED_CONFIG_ID) }

        activity.findViewById<ListView>(android.R.id.list).performItemClick(null, 0, 0)
        settle()

        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).resultCode)
        assertTrue(activity.isFinishing)
        assertTrue(runBlocking { store.bindings.observeAllBindings().first() }.isEmpty())
    }

    @Test
    fun `a missing widget id finishes instead of binding something`() {
        val activity = launch(id = null)
        settle()

        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).resultCode)
        assertTrue(activity.isFinishing)
        assertTrue(runBlocking { store.bindings.observeAllBindings().first() }.isEmpty())
    }
}
