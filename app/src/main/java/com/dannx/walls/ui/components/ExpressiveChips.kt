package com.dannx.walls.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.dannx.walls.ui.theme.LocalReduceMotion
import com.dannx.walls.ui.theme.Motion
import kotlin.math.roundToInt

/**
 * Chip de filtro con el morph de forma de la estética expresiva: mientras está
 * apagado se lee como píldora y, al activarse, la esquina se abre en un
 * rectángulo suave. El cambio viene acompañado de una vibración corta para que
 * el toque se sienta confirmado.
 *
 * Con "reducir movimiento" la forma salta directo al estado final; la
 * vibración se mantiene porque no es movimiento.
 */
@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val corner by animateFloatAsState(
        targetValue = if (selected) 32f else 50f,
        animationSpec = Motion.spatial(reduceMotion),
        label = "expressiveChipCorner",
    )
    FilterChip(
        selected = selected,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(percent = corner.roundToInt()),
    )
}
