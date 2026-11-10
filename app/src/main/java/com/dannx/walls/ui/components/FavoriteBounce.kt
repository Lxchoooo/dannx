package com.dannx.walls.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dannx.walls.ui.theme.LocalLiteMode

/**
 * Escala del corazón: late apenas cambia el estado de favorito. No anima al
 * entrar a la pantalla ni en modo lite, donde conviene ahorrar CPU.
 */
@Composable
fun rememberFavoriteBounce(isFavorite: Boolean): Float {
    val lite = LocalLiteMode.current
    val scale = remember { Animatable(1f) }
    var firstValue by remember { mutableStateOf(true) }
    LaunchedEffect(isFavorite) {
        // El primer valor solo se registra: el corazón no late al abrir.
        if (firstValue) {
            firstValue = false
            return@LaunchedEffect
        }
        if (lite) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(
            targetValue = 1.32f,
            animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        )
    }
    return scale.value
}
