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
import dev.mementomori.lifeclock.widget.ring.CanvasRingRenderer
import dev.mementomori.lifeclock.widget.ring.RingStyle

/**
 * A preview of the two mechanisms the widget is built on: a ring drawn as a real bitmap, and a
 * `Chronometer` counting down on its own.
 *
 * The real settings UI arrives with the config Activity (T2.15); until then this screen is how
 * the drawing is checked without placing a widget.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ring = CanvasRingRenderer()
            .render(
                RingStyle(
                    diameterPx = 240,
                    strokeRatio = 0.12,
                    color = 0xFFE5484D.toInt(),
                    trackColor = 0xFF2A2A2E.toInt(),
                ),
                fraction = 0.73,
            )
            .asImageBitmap()

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
                        Text("Ring preview — 73%")

                        Image(
                            bitmap = ring,
                            contentDescription = "progress ring at 73%",
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
