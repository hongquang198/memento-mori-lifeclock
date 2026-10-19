package dev.mementomori.lifeclock

import android.os.Bundle
import android.os.SystemClock
import android.widget.Chronometer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dagger.hilt.android.AndroidEntryPoint
import dev.mementomori.lifeclock.widget.renderSpikeRing

/**
 * M0 spike host. Shows, on a real device, the two mechanisms the architecture depends on:
 *  - the **determinate ring bitmap** (drawn here the same way the widget draws it), and
 *  - a **Chronometer** counting down on its own.
 *
 * This screen is throwaway; the real app UI arrives in M2/M4.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ring = renderSpikeRing(fraction = 0.73f).asImageBitmap()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    ) {
                        Text("M0 spike — determinate ring + live Chronometer")

                        Image(
                            bitmap = ring,
                            contentDescription = "determinate ring, 73%",
                            modifier = Modifier.size(240.dp),
                        )

                        AndroidView(factory = { context ->
                            Chronometer(context).apply {
                                isCountDown = true
                                base = SystemClock.elapsedRealtime() + 7L * 24 * 60 * 60 * 1000
                                textSize = 22f
                                start()
                            }
                        })
                    }
                }
            }
        }
    }
}
