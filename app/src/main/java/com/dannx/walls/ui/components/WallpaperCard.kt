package com.dannx.walls.ui.components

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.theme.LocalLiteMode

internal const val ThumbWidth = 540
internal const val ThumbHeight = 960

/**
 * Altura del hero en el feed. Se comparte con la pantalla que lo dibuja para
 * pedir la imagen con el tamaño exacto y no decodificar de más.
 */
internal val HeroHeight = 400.dp

/**
 * Calcula el tamaño en píxeles que ocupa una imagen en pantalla, para pedirle a
 * Coil exactamente esa resolución.
 */
@Composable
internal fun screenImageSize(height: androidx.compose.ui.unit.Dp): IntSize {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val heightPx = with(LocalDensity.current) { height.toPx() }
    return IntSize(widthPx, heightPx.toInt())
}

/**
 * RGB_565 usa la mitad de memoria (2 bytes por pixel) y decodifica mas rapido,
 * a costa de perder precision de color: se ven bandas en degradados y
 * degradaciones de color en cielos y piel.
 *
 * En una pantalla de 720 px las miniaturas se ven bien, pero en el hero y en
 * las fotos con degradados se nota. Por eso queda como opcion y no se aplica
 * por defecto: se puede activar con USE_RGB565_FOR_THUMBS.
 */
private const val USE_RGB565_FOR_THUMBS = false

internal fun wallpaperThumbRequest(context: Context, wallpaper: Wallpaper): ImageRequest =
    ImageRequest.Builder(context)
        .data(wallpaper.thumbnailUrl)
        .size(ThumbWidth, ThumbHeight)
        // La grilla y el Detalle comparten esta clave para que la miniatura
        // cacheada se pueda mostrar al instante como imagen provisoria.
        .memoryCacheKey("dannx-thumb:${wallpaper.id}")
        .apply {
            if (USE_RGB565_FOR_THUMBS) {
                allowRgb565(true)
            }
        }
        .build()

@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liteMode = LocalLiteMode.current
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !liteMode) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 650f),
        label = "cardPress",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Una sola frase para TalkBack en cada tarjeta, en vez de leer
            // piezas sueltas.
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Fondo ${wallpaper.title}, ${wallpaper.category}, de ${wallpaper.author}"
            }
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            },
    ) {
        WallpaperImage(wallpaper, isHero = false)
        val scrim = remember {
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.15f),
                    Color.Black.copy(alpha = 0.78f),
                ),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(scrim),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = wallpaper.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = wallpaper.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FavoriteButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        )
    }
}

@Composable
fun FeaturedCard(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    heroHeight: Dp = HeroHeight,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liteMode = LocalLiteMode.current
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !liteMode) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 650f),
        label = "featuredPress",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Fondo destacado ${wallpaper.title}, ${wallpaper.category}, de ${wallpaper.author}"
            }
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            },
    ) {
        WallpaperImage(wallpaper, isHero = true, heroHeight = heroHeight)
        val scrim = remember {
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.12f),
                    Color.Black.copy(alpha = 0.82f),
                ),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(scrim),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.22f),
        ) {
            Text(
                text = "Destacado",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        FavoriteButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Text(
                text = wallpaper.category.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = wallpaper.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = wallpaper.author.take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = wallpaper.author,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun WallpaperImage(wallpaper: Wallpaper, isHero: Boolean, heroHeight: Dp = HeroHeight) {
    val context = LocalContext.current
    val heroSize = if (isHero) screenImageSize(heroHeight) else null
    val model = remember(wallpaper.id, wallpaper.thumbnailUrl, isHero, heroSize) {
        if (heroSize != null) {
            ImageRequest.Builder(context)
                .data(wallpaper.imageUrl)
                .size(heroSize.width, heroSize.height)
                .build()
        } else {
            wallpaperThumbRequest(context, wallpaper)
        }
    }
    StatefulImage(
        model = model,
        // El tile ya describe el contenido por el texto superpuesto.
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    FilledIconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        // Mínimo 48 dp para que el dedo acierte y TalkBack lo tome como botón.
    modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.32f),
            contentColor = Color.White,
        ),
    ) {
        Icon(
            imageVector = if (isFavorite) {
                Icons.Rounded.Favorite
            } else {
                Icons.Rounded.FavoriteBorder
            },
            contentDescription = if (isFavorite) {
                "Quitar de favoritos"
            } else {
                "Añadir a favoritos"
            },
        )
    }
}
