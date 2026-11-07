package com.dannx.walls.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dannx.walls.ui.theme.LocalLiteMode
import kotlinx.coroutines.delay

/**
 * Aparición escalonada para las tarjetas de una lista: cada una entra con un
 * fade y una subida mínima. En modo lite entra de inmediato, sin espera.
 *
 * El estado se guarda con [rememberSaveable] para que al volver a pasar por la
 * lista no se repita la animación.
 */
@Composable
fun StaggeredAppear(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lite = LocalLiteMode.current
    var visible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!lite && !visible) {
            delay((index % 8) * 35L)
        }
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (lite) 120 else 280,
            easing = FastOutSlowInEasing,
        ),
        label = "appearAlpha",
    )
    val offset by animateFloatAsState(
        targetValue = if (visible) 0f else 24f,
        animationSpec = tween(
            durationMillis = if (lite) 120 else 320,
            easing = FastOutSlowInEasing,
        ),
        label = "appearOffset",
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offset.dp.toPx()
        },
    ) {
        content()
    }
}
