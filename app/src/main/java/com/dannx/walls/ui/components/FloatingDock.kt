package com.dannx.walls.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dannx.walls.ui.theme.LocalReduceMotion
import com.dannx.walls.ui.theme.Motion
import com.dannx.walls.ui.theme.MotionDuration

/** Una pestaña de la barra flotante. */
data class DockItem(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val label: String,
)

/**
 * Barra inferior flotante al estilo Expressive: una píldora con contorno que
 * deja ver el fondo de la app y agranda solo la pestaña activa.
 *
 * No usa los inset del sistema: la pantalla que la coloca se encarga del
 * `navigationBarsPadding` para que la píldora quede separada del borde.
 */
@Composable
fun FloatingDock(
    items: List<DockItem>,
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Row(
            modifier = Modifier
                .height(60.dp)
                .animateContentSize(animationSpec = Motion.spatial(reduceMotion))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                DockEntry(
                    item = item,
                    selected = selectedRoute == item.route,
                    onClick = { onNavigate(item.route) },
                )
            }
        }
    }
}

@Composable
private fun DockEntry(
    item: DockItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.94f else 1f,
        animationSpec = Motion.press(reduceMotion),
        label = "dockPress",
    )
    val spec = Motion.fade<Color>(reduceMotion, MotionDuration.Medium)
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else Color.Transparent,
        animationSpec = spec,
        label = "dockContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        animationSpec = spec,
        label = "dockContent",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = contentColor,
        interactionSource = interactionSource,
        modifier = Modifier
            .scale(pressScale)
            .semantics { role = Role.Tab },
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Relleno solo en la pestaña activa; outlined en el resto.
            Crossfade(
                targetState = selected,
                animationSpec = Motion.fade(reduceMotion, MotionDuration.Fast),
                label = "dockIcon",
            ) { isSelected ->
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.icon,
                    // Con la etiqueta a la vista, TalkBack ya la lee del texto.
                    contentDescription = if (isSelected) null else item.label,
                    modifier = Modifier.size(22.dp),
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(Motion.fade(reduceMotion, MotionDuration.Fast)) +
                    expandHorizontally(
                        animationSpec = Motion.spatial(reduceMotion),
                        expandFrom = Alignment.Start,
                    ),
                exit = fadeOut(Motion.fade(reduceMotion, MotionDuration.Fast)) +
                    shrinkHorizontally(
                        animationSpec = Motion.spatial(reduceMotion),
                        shrinkTowards = Alignment.Start,
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
