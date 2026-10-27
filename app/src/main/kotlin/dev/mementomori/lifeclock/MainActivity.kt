package dev.mementomori.lifeclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.mementomori.lifeclock.ui.MainViewModel
import dev.mementomori.lifeclock.ui.MementoApp

/**
 * The app's entry point.
 *
 * It owns no state: [MainViewModel] reads the same graph the widget uses, so a config saved here is
 * the config the next draw reads. The widget itself is redrawn through its worker, never from here.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Target 35 draws behind the system bars by default; this opts in explicitly and lets
        // Scaffold own the insets instead of leaving a white band under a light status bar.
        // The app is light-only for now, so the icons must stay dark even in system dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MementoApp(viewModel)
                }
            }
        }
    }
}


