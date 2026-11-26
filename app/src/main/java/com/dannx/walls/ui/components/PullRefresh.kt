package com.dannx.walls.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.ui.theme.LocalLiteMode
import com.dannx.walls.ui.theme.LocalReduceMotion

/**
 * Contenedor con gesto de actualizar compartido por Inicio, Favoritos y
 * Colecciones: el mismo indicador en las tres pantallas.
 *
 * Con Lite o "reducir movimiento" el indicador animado se reemplaza por una
 * píldora de texto que solo aparece mientras se recarga, para no gastar CPU
 * en equipos lentos.
 */
@Composable
fun PullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable BoxScope.() -> Unit,
) {
    val still = LocalLiteMode.current || LocalReduceMotion.current
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            if (still) {
                RefreshPill(
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else {
                PullToRefreshDefaults.Indicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
    ) {
        content()
    }
}

/** Píldora estática de "actualizando" para Lite y reducir movimiento. */
@Composable
private fun RefreshPill(isRefreshing: Boolean, modifier: Modifier = Modifier) {
    if (!isRefreshing) return
    Surface(
        modifier = modifier.padding(top = 8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Text(
            text = stringResource(R.string.feed_refreshing),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
