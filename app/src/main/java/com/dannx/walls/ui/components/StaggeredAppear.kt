package com.dannx.walls.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dannx.walls.ui.theme.LocalLiteMode
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Aparición escalonada para las tarjetas de una lista: entran con fade y una
 * subida corta, con un retraso por índice que nunca pasa de 500 ms.
 *
 * [resetKey] permite repetir la entrada al cambiar de categoría o búsqueda. En
 * modo lite entra enseguida con un fade simple.
 */
@Composable
fun StaggeredAppear(
    index: Int,
    modifier: Modifier = Modifier,
    resetKey: Any = Unit,
    content: @Composable () -> Unit,
) {
    val lite = LocalLiteMode.current
    var visible by rememberSaveable(resetKey) { mutableStateOf(false) }

    LaunchedEffect(resetKey) {
        if (!lite) {
            delay(min(index * 50L, 500L))
        }
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (lite) {
            fadeIn(animationSpec = tween(durationMillis = 120))
        } else {
            fadeIn(animationSpec = tween(durationMillis = 220)) +
                slideInVertically(
                    animationSpec = tween(
                        durationMillis = 320,
                        easing = FastOutSlowInEasing,
                    ),
                    initialOffsetY = { it / 5 },
                )
        },
    ) {
        content()
    }
}
