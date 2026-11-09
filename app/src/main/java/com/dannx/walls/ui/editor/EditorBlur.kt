package com.dannx.walls.ui.editor

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Desenfoque progresivo compartido por la vista previa y el render final.
 *
 * Trabaja sobre `IntArray` en ARGB para poder probarse sin Android. La vista
 * previa usa una pila de niveles premoldeados y mezcla entre los adyacentes; el
 * render final pide solo los dos niveles que necesita para una intensidad dada.
 */
internal object EditorBlur {
    /**
     * Radios objetivo como fracción del lado menor de la copia base. Crecen de
     * forma casi geométrica para que los escalones se noten parejos.
     */
    val RadiusFractions = floatArrayOf(0.017f, 0.046f, 0.10f)

    /** Tres pasadas de caja por eje aproximan bien a un gaussiano. */
    const val Passes = 3

    fun levelCount(): Int = RadiusFractions.size

    /** Posición continua dentro de la pila: 0 = nítido, `levelCount()` = máximo. */
    fun positionFor(blur: Float): Float = blur.coerceIn(0f, 1f) * RadiusFractions.size

    fun radiiFor(minSide: Int): IntArray = IntArray(RadiusFractions.size) { index ->
        max(1, (minSide * RadiusFractions[index]).roundToInt())
    }

    /**
     * Capas que componen la intensidad pedida.
     *
     * La capa base se dibuja opaca y la superpuesta con [overlayAlpha], de modo
     * que la suma de pesos es 1 y no aparece la imagen doble.
     */
    fun mixFor(blur: Float): BlurMix? {
        val position = positionFor(blur)
        if (position <= 0f) return null
        val whole = floor(position).toInt().coerceIn(0, RadiusFractions.size)
        val fraction = (position - whole).coerceIn(0f, 1f)
        val hasOverlay = fraction > 0f && whole < RadiusFractions.size
        return BlurMix(
            baseLevel = if (whole >= 1) whole - 1 else null,
            overlayLevel = if (hasOverlay) whole else null,
            overlayAlpha = if (hasOverlay) fraction else 0f,
        )
    }

    /**
     * Box blur de [passes] pasadas por eje. Cada pasada es O(ancho × alto), así
     * que subir la fuerza no encarece el cálculo.
     */
    fun boxBlur(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        passes: Int = Passes,
    ) {
        if (radius <= 0 || passes <= 0 || width <= 0 || height <= 0) return
        if (pixels.size < width * height) return
        val temporary = IntArray(width * height)
        repeat(passes) {
            blurHorizontal(pixels, temporary, width, height, radius)
            blurVertical(temporary, pixels, width, height, radius)
        }
    }

    private fun blurHorizontal(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val diameter = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val pixel = source[row + offset.coerceIn(0, width - 1)]
                alpha += (pixel ushr 24) and 0xFF
                red += (pixel ushr 16) and 0xFF
                green += (pixel ushr 8) and 0xFF
                blue += pixel and 0xFF
            }
            for (x in 0 until width) {
                target[row + x] =
                    ((alpha / diameter) shl 24) or
                        ((red / diameter) shl 16) or
                        ((green / diameter) shl 8) or
                        (blue / diameter)
                val remove = source[row + (x - radius).coerceIn(0, width - 1)]
                val add = source[row + (x + radius + 1).coerceIn(0, width - 1)]
                alpha += ((add ushr 24) and 0xFF) - ((remove ushr 24) and 0xFF)
                red += ((add ushr 16) and 0xFF) - ((remove ushr 16) and 0xFF)
                green += ((add ushr 8) and 0xFF) - ((remove ushr 8) and 0xFF)
                blue += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }

    private fun blurVertical(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val diameter = radius * 2 + 1
        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val pixel = source[offset.coerceIn(0, height - 1) * width + x]
                alpha += (pixel ushr 24) and 0xFF
                red += (pixel ushr 16) and 0xFF
                green += (pixel ushr 8) and 0xFF
                blue += pixel and 0xFF
            }
            for (y in 0 until height) {
                target[y * width + x] =
                    ((alpha / diameter) shl 24) or
                        ((red / diameter) shl 16) or
                        ((green / diameter) shl 8) or
                        (blue / diameter)
                val remove = source[(y - radius).coerceIn(0, height - 1) * width + x]
                val add = source[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                alpha += ((add ushr 24) and 0xFF) - ((remove ushr 24) and 0xFF)
                red += ((add ushr 16) and 0xFF) - ((remove ushr 16) and 0xFF)
                green += ((add ushr 8) and 0xFF) - ((remove ushr 8) and 0xFF)
                blue += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }

    /** Capas a dibujar para una intensidad. */
    internal data class BlurMix(
        /** Nivel que se dibuja opaco, o `null` para usar la imagen nítida. */
        val baseLevel: Int?,
        /** Nivel que se mezcla encima, o `null` si no hace falta. */
        val overlayLevel: Int?,
        /** Alfa del nivel superpuesto, entre 0 y 1. */
        val overlayAlpha: Float,
    )
}
