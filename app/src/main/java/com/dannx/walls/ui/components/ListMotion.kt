package com.dannx.walls.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dannx.walls.ui.theme.EmphasizedEasing
import com.dannx.walls.ui.theme.LocalReduceMotion
import com.dannx.walls.ui.theme.MotionDuration
import com.dannx.walls.ui.theme.StandardEasing

/**
 * Acomodo animado de los ítems de una lista: al filtrar, borrar o reordenar,
 * las tarjetas se deslizan a su nueva posición en vez de saltar.
 *
 * Con "reducir movimiento" devuelve un modificador vacío: la lista se reacomoda
 * al instante, sin desplazamientos.
 */
@Composable
fun LazyItemScope.animateItemMotion(): Modifier =
    if (LocalReduceMotion.current) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = tween(
                durationMillis = MotionDuration.Medium,
                easing = EmphasizedEasing,
            ),
            placementSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            fadeOutSpec = tween(
                durationMillis = MotionDuration.Fast,
                easing = StandardEasing,
            ),
        )
    }
