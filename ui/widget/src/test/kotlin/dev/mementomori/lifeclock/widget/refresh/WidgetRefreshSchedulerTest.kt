package dev.mementomori.lifeclock.widget.refresh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * T5.2: re-arming must *replace* a stale registration, not keep the first one forever.
 *
 * The interval is the observable stand-in: a stale spec keeps its own period, a replaced one takes
 * the current one. (The charging + 10-year job visible in dumpsys jobscheduler on the test device
 * is Glance's own `SessionWorker` placeholder, not this spec - see docs/BatteryBudget.md.)
 */
@RunWith(RobolectricTestRunner::class)
class WidgetRefreshSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `re-arming replaces a stale periodic spec instead of keeping it`() {
        val stale = PeriodicWorkRequestBuilder<CountdownRefreshWorker>(60, TimeUnit.MINUTES)
            .setInitialDelay(3650, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WidgetRefreshScheduler.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            stale,
        ).result.get()

        WidgetRefreshScheduler(context).ensurePeriodic()

        val info = workManager
            .getWorkInfosForUniqueWork(WidgetRefreshScheduler.UNIQUE_PERIODIC)
            .get()
            .single()

        assertEquals(
            TimeUnit.MINUTES.toMillis(WidgetRefreshScheduler.PERIODIC_MINUTES),
            info.periodicityInfo?.repeatIntervalMillis,
        )
    }
}


