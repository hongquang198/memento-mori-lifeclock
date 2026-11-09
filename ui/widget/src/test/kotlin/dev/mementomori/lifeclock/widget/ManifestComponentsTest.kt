package dev.mementomori.lifeclock.widget

import android.content.pm.PackageManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The manifest names components by string, so the compiler cannot see them. A package move that
 * updates `package` but not `android:name` builds green and then crashes at runtime
 * (`ClassNotFoundException: ...WidgetRefreshReceiver`, 2026-10-25) - and only when the system
 * actually delivers a broadcast. This resolves every declared component against the real classpath.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestComponentsTest {

    @Test
    fun `every declared activity and receiver exists on the classpath`() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS,
        )
        val declared = buildList {
            info.activities.orEmpty().forEach { add(it.name) }
            info.receivers.orEmpty().forEach { add(it.name) }
        }

        assertTrue("manifest declares no components - the test would prove nothing", declared.isNotEmpty())
        declared.forEach { name ->
            // Throws ClassNotFoundException with the offending name, which is the whole point.
            Class.forName(name)
        }
    }
}
