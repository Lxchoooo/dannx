package com.dannx.walls.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dannx.walls.R

private const val SECRET_TAP_COUNT = 5

/** Máximo tiempo entre toques seguidos antes de reiniciar el contador. */
private const val SECRET_TAP_WINDOW_MS = 2_000L

/**
 * Pantalla de espera: parece una sala vacía y en el borde derecho hay una zona
 * chica que acumula toques seguidos. La zona no se anuncia a lectores de
 * pantalla y el contador solo vive en composición.
 */
@Composable
fun DecoyScreen(
    onBack: () -> Unit,
    onOpenStudio: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var consecutiveTaps by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DecoyIllustration()
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.decoy_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.decoy_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        // Zona secreta. Sin marca, sin contador y sin semántica: el contador
        // se reinicia si pasan más de dos segundos entre toques.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 28.dp, height = 160.dp)
                .clearAndSetSemantics { }
                .pointerInput(onOpenStudio) {
                    detectTapGestures(
                        onTap = {
                            val now = SystemClock.elapsedRealtime()
                            consecutiveTaps = if (now - lastTapAt > SECRET_TAP_WINDOW_MS) {
                                1
                            } else {
                                consecutiveTaps + 1
                            }
                            lastTapAt = now
                            if (consecutiveTaps >= SECRET_TAP_COUNT) {
                                consecutiveTaps = 0
                                lastTapAt = 0L
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenStudio()
                            }
                        },
                    )
                },
        )
    }
}

/**
 * Ilustración liviana dibujada con formas vectoriales: un marco vacío con tres
 * motas, para reforzar la idea de que acá no hay nada que ver.
 */
@Composable
private fun DecoyIllustration() {
    val frameColor = MaterialTheme.colorScheme.outline
    val spotColor = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.decoy_illustration)
    Box(
        modifier = Modifier.size(168.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                },
        ) {
            drawRoundRect(
                color = frameColor,
                cornerRadius = CornerRadius(28.dp.toPx()),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )
            listOf(
                Offset(size.width * 0.32f, size.height * 0.42f),
                Offset(size.width * 0.50f, size.height * 0.58f),
                Offset(size.width * 0.68f, size.height * 0.44f),
            ).forEach { spot ->
                drawCircle(color = spotColor, radius = 5.dp.toPx(), center = spot)
            }
        }
    }
}
