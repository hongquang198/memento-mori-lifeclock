package dev.mementomori.lifeclock.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.mementomori.lifeclock.model.RingConfigRecord
import dev.mementomori.lifeclock.model.TimeCategory
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * T2.3 - one contract, run against both implementations.
 *
 * The in-memory store is what tests and previews use; the DataStore store is what ships. Running
 * the same assertions against both is what stops "tested" from meaning "tested in the other one".
 */
class RepositoriesContractTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private fun implementations(): List<Pair<String, LifeClockStore>> = listOf(
        "in-memory" to InMemoryLifeClockStore(),
        "datastore" to DataStoreLifeClockStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmp.root, "store-${System.nanoTime()}.preferences_pb")
            },
        ),
    )

    private fun contract(body: suspend (LifeClockStore) -> Unit) = runBlocking {
        implementations().forEach { (name, store) ->
            // Rebuild the store per implementation so each behaves like a fresh start.
            try {
                body(store)
            } catch (t: Throwable) {
                throw AssertionError("[impl=$name] ${t.message}", t)
            }
        }
    }

    private fun config(id: String) = WidgetConfigRecord(
        configId = id,
        name = "Config $id",
        type = WidgetType.COUNTDOWN,
        startEpochMillis = 0L,
        endEpochMillis = 1_000L,
        rings = listOf(RingConfigRecord(TimeCategory.DAYS)),
    )

    @Test
    fun `save then observe returns the config`() = contract { store ->
        val saved = config("cfg-1")
        store.configs.save(saved)

        assertEquals(saved, store.configs.observe("cfg-1").first())
        assertEquals(listOf(saved), store.configs.observeAll().first())
    }

    @Test
    fun `editing a config keeps its position in the catalog`() = contract { store ->
        store.configs.save(config("a"))
        store.configs.save(config("b"))
        store.configs.save(config("a").copy(name = "renamed"))

        assertEquals(listOf("a", "b"), store.configs.observeAll().first().map { it.configId })
        assertEquals("renamed", store.configs.observe("a").first()!!.name)
    }

    @Test
    fun `two widgets share one config and both see an edit`() = contract { store ->
        store.configs.save(config("shared"))
        store.bindings.bind("glance-1", "shared")
        store.bindings.bind("glance-2", "shared")

        store.configs.save(config("shared").copy(name = "edited"))

        // Both instances resolve through their binding to the same record.
        val configIds = store.bindings.observeAllBindings().first().map { it.configId }
        assertEquals(listOf("shared", "shared"), configIds)
        configIds.distinct().forEach {
            assertEquals("edited", store.configs.observe(it).first()!!.name)
        }
    }

    @Test
    fun `deleting a bound config is blocked and names the widgets`() = contract { store ->
        store.configs.save(config("cfg"))
        store.bindings.bind("glance-1", "cfg")
        store.bindings.bind("glance-2", "cfg")

        assertEquals(
            DeleteConfigResult.BlockedByBindings(listOf("glance-1", "glance-2")),
            store.configs.delete("cfg"),
        )
        assertNotNull(store.configs.observe("cfg").first())
    }

    @Test
    fun `unbinding the last widget allows deletion`() = contract { store ->
        store.configs.save(config("cfg"))
        store.bindings.bind("glance-1", "cfg")
        store.bindings.unbind("glance-1")

        assertEquals(DeleteConfigResult.Deleted, store.configs.delete("cfg"))
        assertNull(store.configs.observe("cfg").first())
    }

    @Test
    fun `deleting an unknown config reports not found`() = contract { store ->
        assertEquals(DeleteConfigResult.NotFound, store.configs.delete("ghost"))
    }

    @Test
    fun `rebinding moves the instance instead of adding a row`() = contract { store ->
        store.configs.save(config("a"))
        store.configs.save(config("b"))
        store.bindings.bind("glance-1", "a")
        store.bindings.bind("glance-1", "b")

        val all = store.bindings.observeAllBindings().first()
        assertEquals(1, all.size)
        assertEquals("b", all.single().configId)
    }

    @Test
    fun `binding to an unknown config fails instead of dangling`() = contract { store ->
        val error = runCatching { store.bindings.bind("glance-1", "ghost") }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $error", error is IllegalArgumentException)
        assertNull(store.bindings.observeBinding("glance-1").first())
    }

    @Test
    fun `data is written to disk, not just held in memory`() = runBlocking {
        val file = File(tmp.root, "persist.preferences_pb")
        val store = DataStoreLifeClockStore(
            PreferenceDataStoreFactory.create(scope = scope) { file },
        )
        store.configs.save(config("cfg-1"))

        assertTrue("preferences file was not created", file.exists())
        assertTrue("config id not found on disk", file.readText(Charsets.ISO_8859_1).contains("cfg-1"))
    }
}
