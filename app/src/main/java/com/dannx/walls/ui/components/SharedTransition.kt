package com.dannx.walls.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.dannx.walls.ui.theme.EmphasizedEasing
import com.dannx.walls.ui.theme.LocalReduceMotion
import com.dannx.walls.ui.theme.MotionDuration

/** Scope del `SharedTransitionLayout` que envuelve al NavHost. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Scope de animación de la pantalla del NavHost que se está componiendo. */
private val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Publica el scope de animación de la pantalla actual para que las tarjetas y
 * el detalle puedan compartir la foto. Se llama dentro de cada destino del
 * NavHost: fuera de una transición no hay nada que compartir.
 */
@Composable
fun AnimatedVisibilityScope.ProvideSharedVisibilityScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
        content()
    }
}

/**
 * Clave de la foto de un fondo. La usan la tarjeta y el detalle; si los dos
 * están compuestos al mismo tiempo, Compose mueve la imagen de una posición a
 * la otra en vez de saltar.
 */
fun wallpaperImageKey(id: String): String = "wallpaper-image-$id"

/**
 * Hace que la foto viaje entre la tarjeta y el detalle. Sin los scopes (o con
 * reducir movimiento) devuelve el modificador tal cual, así el resto de la app
 * sigue funcionando igual.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedWallpaperImage(id: String): Modifier {
    if (LocalReduceMotion.current) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedWallpaperImage.sharedBounds(
            rememberSharedContentState(key = wallpaperImageKey(id)),
            visibilityScope,
            boundsTransform = { _, _ ->
                tween(durationMillis = MotionDuration.Slow, easing = EmphasizedEasing)
            },
        )
    }
}
