package com.dannx.walls.wallpaper

import kotlin.math.max

/**
 * Movimiento de las dos capas del fondo animado.
 *
 * El launcher avisa el desplazamiento como `xOffset` entre 0 y 1. La foto
 * nítida se corre sobre una copia desenfocada y más grande de sí misma: el
 * hueco que deja a un costado lo tapa la copia, así el borde de la pantalla
 * nunca muestra un espacio vacío.
 */
object LiveWallpaperGeometry {
    /** Recorrido máximo de la foto nítida, como fracción del ancho. */
    const val ForegroundShift = 0.05f

    /** La copia de fondo acompaña una fracción del recorrido: eso da la profundidad. */
    const val BackgroundShift = 0.018f

    /** Margen extra de la copia de fondo para que tape los costados descubiertos. */
    const val BackgroundSlack = 0.10f

    /** Lleva el `xOffset` del launcher a -1 (izquierda) … 1 (derecha). */
    fun normalizedOffset(xOffset: Float): Float =
        ((xOffset - 0.5f) * 2f).coerceIn(-1f, 1f)

    /** Desplazamiento en píxeles para una capa con la fracción indicada. */
    fun shift(surfaceWidth: Float, xOffset: Float, fraction: Float): Float =
        normalizedOffset(xOffset) * max(surfaceWidth, 0f) * fraction

    /** Escala que hace entrar la foto completa cubriendo el destino. */
    fun coverScale(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Float,
        targetHeight: Float,
    ): Float {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0f || targetHeight <= 0f) {
            return 0f
        }
        return max(targetWidth / sourceWidth, targetHeight / sourceHeight)
    }
}
