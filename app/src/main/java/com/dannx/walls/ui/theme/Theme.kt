package com.dannx.walls.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }
    // El estilo Expressive viene dado por la tipografía, las formas y los
    // springs que usan las pantallas, además del esquema de motion oficial.
    MaterialTheme(
        colorScheme = colors,
        motionScheme = if (liteMode) MotionScheme.standard() else MotionScheme.expressive(),
        typography = DannXTypography,
        shapes = DannXShapes,
        content = content,
    )
}
