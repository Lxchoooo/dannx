package com.dannx.walls.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.dannx.walls.R
import com.dannx.walls.ui.theme.LocalLiteMode

/**
 * Imagen remota con estados explícitos: un indicador pequeño mientras carga y
 * un ícono marcado si la imagen falló, para no dejar un rectángulo vacío a
 * adivinar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatefulImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val lite = LocalLiteMode.current
    val fade = if (lite) 0 else 220
    val request = remember(model, lite) {
        when (model) {
            is ImageRequest -> model.newBuilder().crossfade(fade).build()
            else -> ImageRequest.Builder(context)
                .data(model)
                .crossfade(fade)
                .build()
        }
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(25.dp))
            }
        },
        error = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = stringResource(R.string.image_load_error_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
