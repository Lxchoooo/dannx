package com.dannx.walls.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.ui.theme.EmphasizedEasing
import com.dannx.walls.ui.theme.LocalLiteMode
import com.dannx.walls.ui.theme.LocalReduceMotion
import com.dannx.walls.ui.theme.MotionDuration

/**
 * Marcador de carga liviano: bloques que simulan la grilla y una banda de luz
 * que los recorre. Cuando hay modo lite queda estático, sin animación, para no
 * gastar CPU en equipos lentos.
 */
@Composable
fun FeedSkeleton(modifier: Modifier = Modifier) {
    val lite = LocalLiteMode.current
    val base = MaterialTheme.colorScheme.surfaceVariant
    val sweep = if (lite) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "feedSkeleton")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1350, easing = LinearEasing),
            ),
            label = "feedSkeletonSweep",
        )
        value
    }
    // Alturas desparejas para que se parezca a la grilla real.
    val heights = listOf(
        230.dp to 190.dp,
        200.dp to 240.dp,
        220.dp to 200.dp,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        heights.forEach { (left, right) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(left),
                    base = base,
                    sweep = sweep,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(right),
                    base = base,
                    sweep = sweep,
                )
            }
        }
    }
}

/**
 * Un bloque del esqueleto. La banda de luz se dibuja con un degradado
 * transparente → claro → transparente que se corre de izquierda a derecha y
 * se reinicia fuera de los bordes, por eso no se ve el salto.
 */
@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    base: Color,
    sweep: Float?,
) {
    val highlight = if (base.luminance() > 0.5f) {
        Color.White.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }
    val shimmer = if (sweep == null) {
        Modifier
    } else {
        Modifier.drawBehind {
            val band = size.width * 0.9f
            val travel = size.width + band * 2f
            val x = -band + sweep * travel
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(x - band, 0f),
                    end = Offset(x + band, 0f),
                ),
            )
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(base)
            .then(shimmer),
    )
}

/**
 * Estado vacío con texto útil.
 */
@Composable
fun EmptyState(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Wallpaper,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyStateStage(index = 0) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        EmptyStateStage(index = 1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (message != null) {
            EmptyStateStage(index = 2) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (actionLabel != null && onAction != null) {
            EmptyStateStage(index = 3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/**
 * Entrada escalonada de las partes de un estado vacío: aparecen de arriba
 * abajo con un desplazamiento corto. Con "reducir movimiento" se muestran de
 * una, ya en su posición final.
 */
@Composable
private fun EmptyStateStage(
    index: Int,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion) {
        content()
        return
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = MotionDuration.Slow,
            delayMillis = index * 55,
            easing = EmphasizedEasing,
        ),
        label = "emptyStateStage",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 16.dp.toPx()
        },
    ) {
        content()
    }
}

/**
 * Estado de error con botón de reintento.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.state_error_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.state_retry))
        }
    }
}

/**
 * Aviso discreto de que se está mostrando contenido guardado.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.state_offline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
