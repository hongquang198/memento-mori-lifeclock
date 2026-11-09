package dev.mementomori.lifeclock.widget.refresh

import android.content.Context
import android.content.Intent
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * The receiver's only job is to enqueue, so that is the only thing worth asserting.
 *
 * `BOOT_COMPLETED` is the one branch the device never showed us: it is withheld until the first
 * unlock, and by the time the phone was unlocked the periodic heartbeat had already drawn
 * (2026-10-25). The platform's delivery is Android's contract; what is ours is that every handled
 * action produces exactly one immediate refresh, and that boot/reinstall also keep the heartbeat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetRefreshReceiverTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        // The default test executor is synchronous, so the worker also runs. That is fine: this
        // test asserts the enqueue happened, and the worker's own outcome is a separate concern.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `boot enqueues a refresh and keeps the heartbeat`() {
        deliver(Intent.ACTION_BOOT_COMPLETED)

        assertEquals(1, work(WidgetRefreshScheduler.UNIQUE_IMMEDIATE).size)
        assertEquals(1, work(WidgetRefreshScheduler.UNIQUE_PERIODIC).size)
    }

    @Test
    fun `reinstall re-arms the heartbeat`() {
        deliver(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertEquals(1, work(WidgetRefreshScheduler.UNIQUE_PERIODIC).size)
        assertEquals(1, work(WidgetRefreshScheduler.UNIQUE_IMMEDIATE).size)
    }

    @Test
    fun `every clock or calendar jump enqueues a refresh`() {
        listOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            WidgetRefreshScheduler.ACTION_BOUNDARY,
        ).forEach { action ->
            deliver(action)
            assertTrue("$action enqueued nothing", work(WidgetRefreshScheduler.UNIQUE_IMMEDIATE).isNotEmpty())
        }
    }

    @Test
    fun `an action we do not handle does nothing`() {
        deliver("dev.mementomori.lifeclock.NOT_OURS")

        assertTrue(work(WidgetRefreshScheduler.UNIQUE_IMMEDIATE).isEmpty())
        assertTrue(work(WidgetRefreshScheduler.UNIQUE_PERIODIC).isEmpty())
    }

    private fun deliver(action: String) {
        WidgetRefreshReceiver().onReceive(context, Intent(action))
    }

    private fun work(uniqueName: String): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(uniqueName)
            .get(5, TimeUnit.SECONDS)
}
