package com.dannx.walls.util

import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * Cuánto más ancho que la pantalla se guarda un fondo estático.
 *
 * Android mueve la ventana visible sobre el bitmap usando el desplazamiento
 * del launcher. Si el bitmap mide exactamente la pantalla no hay margen que
 * mover; con un 15 % extra el fondo puede acompañar el gesto sin descubrir
 * los bordes. Es el mismo mecanismo que usan Google Fotos y los launchers
 * que ofrecen parallax con fondos estáticos.
 */
internal const val StaticWallpaperSlack = 1.15f

/**
 * Tamaño del bitmap que se aplica y porción visible en reposo.
 *
 * Se guarda como números enteros para poder probar la geometría en la JVM;
 * el [Rect] que pide Android se arma recién al aplicar ([cropHint]).
 */
internal data class StaticWallpaperLayout(
    val width: Int,
    val height: Int,
    val visibleWidth: Int,
    val visibleHeight: Int,
) {
    /** Borde izquierdo de la porción visible, con el margen repartido parejo. */
    val left: Int get() = ((width - visibleWidth) / 2).coerceAtLeast(0)

    /** Borde superior de la porción visible; hoy el margen es solo lateral. */
    val top: Int get() = ((height - visibleHeight) / 2).coerceAtLeast(0)

    /**
     * Porción que el launcher muestra quieto. El resto del bitmap queda como
     * margen para que pueda desplazarlo.
     */
    fun cropHint(): Rect = Rect(left, top, left + visibleWidth, top + visibleHeight)
}

/**
 * Calcula el bitmap con margen lateral y la porción visible centrada que
 * espera [android.app.WallpaperManager.setBitmap].
 */
internal fun staticWallpaperLayout(
    screenWidth: Int,
    screenHeight: Int,
    slack: Float = StaticWallpaperSlack,
): StaticWallpaperLayout {
    val safeWidth = screenWidth.coerceAtLeast(1)
    val safeHeight = screenHeight.coerceAtLeast(1)
    val factor = slack.coerceAtLeast(1f)
    val width = (safeWidth * factor).roundToInt().coerceAtLeast(safeWidth)
    return StaticWallpaperLayout(
        width = width,
        height = safeHeight,
        visibleWidth = safeWidth,
        visibleHeight = safeHeight,
    )
}
