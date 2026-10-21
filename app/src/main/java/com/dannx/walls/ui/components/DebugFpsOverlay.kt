package com.dannx.walls.ui.components

import android.os.Build
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dannx.walls.BuildConfig

@Composable
fun DebugFpsOverlay(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    var fps by remember { mutableIntStateOf(0) }
    var worstMs by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        var lastNanos = -1L
        var windowStartNanos = -1L
        var frames = 0
        var worst = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (windowStartNanos < 0L) {
                    windowStartNanos = frameTimeNanos
                } else if (lastNanos >= 0L) {
                    val deltaMs = (frameTimeNanos - lastNanos) / 1_000_000L
                    if (deltaMs > worst) worst = deltaMs
                }
                lastNanos = frameTimeNanos
                frames++
                val elapsed = frameTimeNanos - windowStartNanos
                if (elapsed >= 1_000_000_000L) {
                    fps = (frames.toLong() * 1_000_000_000L / elapsed).toInt()
                    worstMs = worst
                    frames = 0
                    worst = 0L
                    windowStartNanos = frameTimeNanos
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp)
                .clip(CircleShape)
                .background(Color(0xCC1C1B22))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "FPS $fps",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF8BE28B),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "peor frame ${worstMs}ms",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFC86B),
                textAlign = TextAlign.Center,
            )
        }
    }
}
