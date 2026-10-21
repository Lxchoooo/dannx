package com.dannx.walls.util

import kotlin.math.abs
import kotlin.math.roundToInt

const val MinZoom = 1f
const val MaxZoom = 5f
const val DoubleTapZoom = 2.5f

data class PanOffset(
    val x: Float,
    val y: Float,
)

data class ZoomState(
    val scale: Float = MinZoom,
    val offset: PanOffset = PanOffset(0f, 0f),
)

/**
 * Mantiene la escala dentro de los límites permitidos.
 */
fun clampScale(scale: Float, min: Float = MinZoom, max: Float = MaxZoom): Float =
    scale.coerceIn(min, max)

/**
 * Limita el desplazamiento para que la imagen nunca se salga de la vista.
 * Con escala 1 el desplazamiento siempre es cero.
 */
fun clampPan(
    offset: PanOffset,
    scale: Float,
    containerWidth: Float,
    containerHeight: Float,
): PanOffset {
    val safeScale = clampScale(scale)
    if (safeScale <= MinZoom) {
        return PanOffset(0f, 0f)
    }
    val maxX = (containerWidth * (safeScale - 1f)) / 2f
    val maxY = (containerHeight * (safeScale - 1f)) / 2f
    return PanOffset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

/**
 * Distancia mínima entre dos toques para considerarlos un doble toque.
 */
fun isDoubleTapDistance(distance: Float, threshold: Float = 120f): Boolean =
    abs(distance) <= threshold

data class CropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class ImageFormat(
    val mime: String,
    val extension: String,
)

data class SharedImage(
    val uri: android.net.Uri,
    val mime: String,
)

/**
 * Detecta el formato real de una imagen mirando los primeros bytes, para no
 * confiar solo en la cabecera HTTP.
 */
fun detectImageFormat(header: ByteArray): ImageFormat? {
    if (header.size >= 8 &&
        header[0] == 0x89.toByte() &&
        header[1] == 'P'.code.toByte() &&
        header[2] == 'N'.code.toByte() &&
        header[3] == 'G'.code.toByte()
    ) {
        return ImageFormat("image/png", "png")
    }
    if (header.size >= 3 &&
        header[0] == 0xFF.toByte() &&
        header[1] == 0xD8.toByte() &&
        header[2] == 0xFF.toByte()
    ) {
        return ImageFormat("image/jpeg", "jpg")
    }
    if (header.size >= 12 &&
        String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
    ) {
        return ImageFormat("image/webp", "webp")
    }
    if (header.size >= 6 &&
        String(header, 0, 3, Charsets.US_ASCII) == "GIF"
    ) {
        return ImageFormat("image/gif", "gif")
    }
    return null
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
