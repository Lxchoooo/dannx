package com.dannx.walls.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cuando el modo lite está activo, las pantallas usan movimientos cortos o los
 * de Android por defecto, sin animaciones propias.
 */
val LocalLiteMode = staticCompositionLocalOf { false }
