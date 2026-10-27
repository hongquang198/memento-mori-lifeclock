package dev.mementomori.lifeclock.widget.refresh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import dev.mementomori.lifeclock.data.InMemoryLifeClockStore
import dev.mementomori.lifeclock.data.LifeClockStore
import dev.mementomori.lifeclock.data.WidgetBindingRepository
import dev.mementomori.lifeclock.model.WidgetInstanceBinding
import dev.mementomori.lifeclock.widget.TestDependencies
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A failed draw used to be terminal: `tick` threw before `armBoundary`, the worker returned
 * `failure()`, and the next attempt was the 15 minute heartbeat - so a transient error cost up to a
 * quarter of an hour of a stale frame. The gate keeps the boundary REFRESHED, so the work is still
 * owed; `retry()` is what asks for it back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CountdownRefreshWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        ProcessRefreshGate.resetForTest()
    }

    @After
    fun tearDown() {
        WidgetDependenciesHolder.current = null
        ProcessRefreshGate.resetForTest()
    }

    @Test
    fun `a failed draw asks WorkManager to come back`() = runBlocking {
        WidgetDependenciesHolder.current = TestDependencies(ExplodingStore())

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    }

    @Test
    fun `no dependency graph is terminal, not a retry loop`() = runBlocking {
        WidgetDependenciesHolder.current = null

        assertEquals(ListenableWorker.Result.failure(), worker().doWork())
    }

    private fun worker(): CountdownRefreshWorker =
        TestListenableWorkerBuilder<CountdownRefreshWorker>(context)
            .setInputData(
                workDataOf(
                    WidgetRefreshScheduler.KEY_REASON to "test",
                    WidgetRefreshScheduler.KEY_FORCE to true,
                ),
            )
            .build()
}

/** Bindings that throw: stands in for any failure on the render path. */
private class ExplodingStore : LifeClockStore by InMemoryLifeClockStore() {
    override val bindings = object : WidgetBindingRepository {
        override fun observeBinding(glanceId: String): Flow<WidgetInstanceBinding?> = flow { error("boom") }

        override fun observeAllBindings(): Flow<List<WidgetInstanceBinding>> = flow { error("boom") }

        override suspend fun bind(glanceId: String, configId: String) = error("boom")

        override suspend fun unbind(glanceId: String) = error("boom")
    }
}
