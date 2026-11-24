package com.dannx.walls.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
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
import com.dannx.walls.ui.theme.EmphasizedEasing
import com.dannx.walls.ui.theme.LocalReduceMotion
import com.dannx.walls.ui.theme.MotionDuration
import com.dannx.walls.ui.theme.StaggerMaxMillis
import com.dannx.walls.ui.theme.StaggerStepMillis
import com.dannx.walls.ui.theme.StandardEasing
import kotlinx.coroutines.delay
import kotlin.math.min

/** Cantidad de tarjetas que entran con animación; el resto aparece directo. */
private const val StaggerItemLimit = 12

/**
 * Aparición escalonada para las tarjetas de una lista: entran con fade y una
 * subida corta, con un retraso por índice que nunca pasa de 300 ms.
 *
 * El retraso es corto a propósito: el acomodo de los ítems al filtrar o borrar
 * lo hace `animateItem()`; esto solo cubre la primera carga. En modo lite entra
 * enseguida con un fade simple.
 */
@Composable
fun StaggeredAppear(
    index: Int,
    modifier: Modifier = Modifier,
    resetKey: Any = Unit,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    // Solo la primera pantalla de tarjetas entra escalonada. Animar también las
    // que aparecen al scrollear corta el desplazamiento y no aporta nada.
    val animated = index < StaggerItemLimit
    var visible by rememberSaveable(resetKey) { mutableStateOf(false) }

    LaunchedEffect(resetKey) {
        if (!reduceMotion && animated) {
            delay(min(index * StaggerStepMillis, StaggerMaxMillis))
        }
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = when {
            !animated -> EnterTransition.None
            reduceMotion -> fadeIn(animationSpec = tween(MotionDuration.Fast))
            else -> fadeIn(animationSpec = tween(MotionDuration.Medium, easing = StandardEasing)) +
                slideInVertically(
                    animationSpec = tween(
                        durationMillis = MotionDuration.Slow,
                        easing = EmphasizedEasing,
                    ),
                    initialOffsetY = { it / 5 },
                )
        },
    ) {
        content()
    }
}
