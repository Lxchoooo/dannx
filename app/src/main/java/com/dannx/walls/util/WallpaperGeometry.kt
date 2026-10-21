package com.dannx.walls.util

import kotlin.math.roundToInt

data class CropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Mayor potencia de dos que mantiene la imagen por encima del tamaño pedido.
 * Se usa para decodificar con muestreo y no traer el bitmap completo a memoria.
 */
fun calculateInSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
        return 1
    }
    var sampleSize = 1
    while (
        sourceWidth / (sampleSize * 2) >= targetWidth &&
        sourceHeight / (sampleSize * 2) >= targetHeight
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Recorte central que deja la imagen con la proporción de la pantalla,
 * sin estirar ni agregar bandas.
 */
fun centerCropRect(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): CropRect {
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        return CropRect(0, 0, 0, 0)
    }
    if (targetWidth <= 0 || targetHeight <= 0) {
        return CropRect(0, 0, sourceWidth, sourceHeight)
    }
    val sourceRatio = sourceWidth.toDouble() / sourceHeight.toDouble()
    val targetRatio = targetWidth.toDouble() / targetHeight.toDouble()
    return if (sourceRatio > targetRatio) {
        val newWidth = (sourceHeight * targetRatio).roundToInt().coerceIn(1, sourceWidth)
        val left = (sourceWidth - newWidth) / 2
        CropRect(left, 0, left + newWidth, sourceHeight)
    } else {
        val newHeight = (sourceWidth / targetRatio).roundToInt().coerceIn(1, sourceHeight)
        val top = (sourceHeight - newHeight) / 2
        CropRect(0, top, sourceWidth, top + newHeight)
    }
}
