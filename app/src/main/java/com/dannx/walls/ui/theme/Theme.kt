package com.dannx.walls.ui.theme

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dannx.walls.data.ColorContrast
import com.dannx.walls.data.ColorPaletteStyle
import com.dannx.walls.data.WALLPAPER_COLOR_SEED

internal val DannXShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DannXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    liteMode: Boolean = false,
    colorSeed: String = WALLPAPER_COLOR_SEED,
    paletteStyle: ColorPaletteStyle = ColorPaletteStyle.TONAL_SPOT,
    contrastLevel: ColorContrast = ColorContrast.STANDARD,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val seed = rememberSeedColor(colorSeed, darkTheme)
    // Con el color del sistema y el estilo por defecto no hace falta volver a
    // generar nada: Android ya calculó el esquema que el usuario espera.
    val useSystemScheme = dynamicColor &&
        colorSeed.isBlank() &&
        paletteStyle == ColorPaletteStyle.TONAL_SPOT &&
        contrastLevel == ColorContrast.STANDARD &&
        !amoled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val target = remember(
        dynamicColor,
        darkTheme,
        seed,
        paletteStyle,
        contrastLevel,
        amoled,
        useSystemScheme,
    ) {
        when {
            !dynamicColor -> if (darkTheme) DarkColors else LightColors
            useSystemScheme -> if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }

            else -> materialScheme(
                seed = seed,
                dark = darkTheme,
                style = paletteStyle,
                contrast = contrastLevel,
                amoled = amoled,
            )
        }
    }
    val colors = rememberAnimatedScheme(target, enabled = !liteMode)
    // Lite o "quitar animaciones" del sistema: los dos apagan los resortes.
    val systemAnimations = remember {
        runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
    }
    val reduceMotion = liteMode || !systemAnimations
    // En negro puro el tinte de elevación volvería a iluminar el fondo.
    CompositionLocalProvider(
        LocalLiteMode provides liteMode,
        LocalReduceMotion provides reduceMotion,
        LocalTonalElevationEnabled provides !amoled,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            typography = DannXTypography,
            shapes = DannXShapes,
            content = content,
        )
    }
}

/**
 * Resuelve de dónde sale el color semilla: un preset guardado, el fondo de
 * pantalla o, como último recurso, el acento del sistema.
 */
@Composable
internal fun rememberSeedColor(colorSeed: String, darkTheme: Boolean): Color {
    val context = LocalContext.current
    return remember(context, colorSeed, darkTheme) {
        parseSeedColor(colorSeed) ?: wallpaperSeed(context) ?: systemSeed(context, darkTheme)
    }
}

private fun wallpaperSeed(context: android.content.Context): Color? =
    runCatching {
        WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
    }.getOrNull()?.let { Color(it.toArgb()) }

private fun systemSeed(context: android.content.Context, darkTheme: Boolean): Color {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return DefaultSeed
    return if (darkTheme) dynamicDarkColorScheme(context).primary else {
        dynamicLightColorScheme(context).primary
    }
}

/**
 * Cambia los colores con un spring corto. En Lite se devuelve el esquema
 * destino sin animar para no gastar frames.
 */
@Composable
private fun rememberAnimatedScheme(target: ColorScheme, enabled: Boolean): ColorScheme {
    if (!enabled) return target
    var previous by remember { mutableStateOf(target) }
    var current by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (current != target) {
            previous = current
            current = target
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    val fraction = progress.value
    return if (fraction >= 1f) current else interpolateScheme(previous, current, fraction)
}
