package com.dannx.walls.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dannx.walls.ui.theme.LocalLiteMode
import kotlinx.coroutines.delay

/**
 * Escala del corazón: hace un pop elástico al marcar y vuelve con un resorte
 * más sutil al desmarcar. No anima al entrar a la pantalla ni en modo lite.
 */
@Composable
fun rememberFavoriteBounce(isFavorite: Boolean): Float {
    val lite = LocalLiteMode.current
    var pop by remember { mutableStateOf(false) }
    var firstValue by remember { mutableStateOf(true) }

    val spec = if (isFavorite) {
        // Al marcar el resorte bota más: se siente el corazón "saltar".
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )
    } else {
        // Al desmarcar alcanza con volver sin rebote.
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    }
    val scale by animateFloatAsState(
        targetValue = if (pop && !lite) 1.25f else 1f,
        animationSpec = spec,
        label = "favoriteBounce",
    )

    LaunchedEffect(isFavorite) {
        // El primer valor solo se registra: el corazón no late al abrir.
        if (firstValue) {
            firstValue = false
            return@LaunchedEffect
        }
        if (lite) return@LaunchedEffect
        pop = true
        delay(220)
        pop = false
    }
    return scale
}
