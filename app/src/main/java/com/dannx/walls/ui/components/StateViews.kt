package com.dannx.walls.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.ui.theme.LocalLiteMode

/**
 * Marcador de carga liviano: bloques que simulan la grilla. Cuando hay modo
 * lite queda estático, sin animación, para no gastar CPU en equipos lentos.
 */
@Composable
fun FeedSkeleton(modifier: Modifier = Modifier) {
    val lite = LocalLiteMode.current
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val alpha = if (lite) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "feedSkeleton")
        val pulse by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "feedSkeletonPulse",
        )
        pulse
    }
    val color = placeholder.copy(alpha = alpha)
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(left)
                        .clip(RoundedCornerShape(22.dp))
                        .background(color),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(right)
                        .clip(RoundedCornerShape(22.dp))
                        .background(color),
                )
            }
        }
    }
}

/**
 * Estado vacío con texto útil.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
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
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
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
