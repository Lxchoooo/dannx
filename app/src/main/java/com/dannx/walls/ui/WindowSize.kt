package com.dannx.walls.ui

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Tamaño de ventana calculado en [MainActivity] una sola vez. Las pantallas lo
 * leen para adaptar columnas y alturas a celulares, tablets y pantallas
 * divididas.
 */
val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> {
    error("LocalWindowSizeClass no fue provisto")
}
