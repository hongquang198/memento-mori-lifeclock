package dev.mementomori.lifeclock.widget.config

import dev.mementomori.lifeclock.widget.R

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.glance.appwidget.AppWidgetId
import androidx.lifecycle.lifecycleScope
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import kotlinx.coroutines.launch
import dev.mementomori.lifeclock.widget.refresh.CountdownRefreshWorker
import dev.mementomori.lifeclock.widget.WidgetDependenciesHolder
import dev.mementomori.lifeclock.widget.WidgetLog
import dev.mementomori.lifeclock.widget.refresh.WidgetRefreshScheduler
import dev.mementomori.lifeclock.widget.bindingKey

/**
 * The intent that opens the picker for one placed widget.
 *
 * Used twice: the launcher sends it at placement (`android:configure`), and the widget sends it on
 * tap. Same shape both ways, so both routes land on the same code path.
 */
internal fun configureIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, ConfigActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

/**
 * The screen the launcher opens when a widget is placed, and the one it opens again when the user
 * taps a placed widget to reconfigure it (`widgetFeatures="reconfigurable"`).
 *
 * Its whole job is to turn a choice into a binding. It never draws: the renderer's only entry point
 * is [CountdownRefreshWorker], so a slow frame can never make this Activity look hung (M0).
 *
 * A plain View screen on purpose. The picker is a list of names; Compose would add a UI toolkit to
 * a module whose other half is a bitmap.
 */
class ConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Anything that ends this screen without a choice - back, home, a crash - must leave the
        // widget unbound rather than half-configured.
        setResult(RESULT_CANCELED)

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetLog.w("config activity launched without EXTRA_APPWIDGET_ID")
            finish()
            return
        }

        val dependencies = WidgetDependenciesHolder.current
        if (dependencies == null) {
            WidgetLog.w("config activity started before the graph was published")
            finish()
            return
        }

        val picker = ConfigPicker(
            configs = dependencies.configs,
            bindings = dependencies.bindings,
            profiles = dependencies.profiles,
            clock = dependencies.clock,
        )
        val bindingKey = AppWidgetId(appWidgetId).bindingKey()
        title = getString(R.string.config_picker_title)

        lifecycleScope.launch {
            picker.seedIfEmpty(
                countdownName = getString(R.string.config_seed_name),
                lifeClockName = getString(R.string.config_seed_life_clock_name),
                profileName = getString(R.string.config_seed_profile_name),
            )
            showCatalog(
                picker = picker,
                catalog = picker.catalog(),
                appWidgetId = appWidgetId,
                bindingKey = bindingKey,
                current = picker.currentConfig(bindingKey),
            )
        }
    }

    private fun showCatalog(
        picker: ConfigPicker,
        catalog: List<WidgetConfigRecord>,
        appWidgetId: Int,
        bindingKey: String,
        current: String?,
    ) {
        val list = ListView(this).apply {
            // A framework id, so the screen needs no layout file to be testable.
            id = android.R.id.list
            adapter = ArrayAdapter(
                this@ConfigActivity,
                android.R.layout.simple_list_item_1,
                // Reconfiguring must say which one is in force, or the user cannot tell what
                // "leaving it alone" means.
                catalog.map { config ->
                    if (config.configId == current) {
                        getString(R.string.config_selected, config.name)
                    } else {
                        config.name
                    }
                },
            )
            setOnItemClickListener { _, _, position, _ ->
                choose(picker, catalog[position], appWidgetId, bindingKey)
            }
        }

        // The app theme has no action bar and targetSdk 35 forces edge-to-edge, so the heading and
        // the status-bar inset are this screen's own job.
        val heading = TextView(this).apply {
            text = getString(R.string.config_picker_title)
            textSize = 20f
            setPadding(dp(24), dp(24), dp(24), dp(8))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading)
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun choose(
        picker: ConfigPicker,
        config: WidgetConfigRecord,
        appWidgetId: Int,
        bindingKey: String,
    ) {
        lifecycleScope.launch {
            val bound = runCatching {
                picker.choose(glanceId = bindingKey, configId = config.configId)
            }
            if (bound.isFailure) {
                // The config can be deleted from the app while this screen is open; the store
                // refuses to bind to a missing one. Cancelling is the honest answer.
                WidgetLog.w("could not bind widget $appWidgetId", bound.exceptionOrNull())
                finish()
                return@launch
            }

            // Ask for a frame now instead of waiting up to PERIODIC_MINUTES for the heartbeat.
            // Enqueued, not drawn here: the worker survives this Activity going away.
            WidgetRefreshScheduler(this@ConfigActivity).refreshNow(reason = "configure", force = true)

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}
