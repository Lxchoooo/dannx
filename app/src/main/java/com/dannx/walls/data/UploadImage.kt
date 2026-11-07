package com.dannx.walls.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import kotlin.math.max

/**
 * Imagen lista para subir: archivo local, tipo, extensión y medidas reales.
 *
 * El almacenamiento de Supabase solo acepta JPG, PNG y WebP, y las fotos de
 * algunos teléfonos llegan en HEIC/HEIF. Por eso todo lo que no entra en esos
 * formatos (o supera el límite de peso) se convierte a JPEG antes de subirlo.
 */
internal data class PreparedImage(
    val file: File,
    val mime: String,
    val extension: String,
    val width: Int,
    val height: Int,
)

private val ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")

/** Lado máximo al convertir: se mantiene la calidad de pantalla alta. */
private const val MAX_DIMENSION = 4320

internal fun prepareUploadImage(
    context: Context,
    uri: Uri,
    maxBytes: Long,
): PreparedImage {
    val resolver = context.contentResolver
    val declared = normalizeMime(resolver.getType(uri))
    val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
    val knownSize = runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull()?.takeIf { it > 0 }

    if (declared in ALLOWED_MIME && (knownSize == null || knownSize <= maxBytes)) {
        val file = File(dir, "upload-${System.nanoTime()}.${extensionFor(declared)}")
        if (copyToFile(context, uri, file, maxBytes) != null) {
            val (width, height) = readBounds(context, uri)
            return PreparedImage(file, declared, extensionFor(declared), width, height)
        }
        file.delete()
    }
    return convertToJpeg(context, uri, dir, maxBytes)
}

private fun normalizeMime(raw: String?): String {
    val mime = raw?.trim()?.lowercase().orEmpty()
    return when (mime) {
        "image/jpg", "image/pjpeg" -> "image/jpeg"
        "image/x-png" -> "image/png"
        "" -> "image/jpeg"
        else -> mime
    }
}

private fun extensionFor(mime: String): String = when (mime) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    else -> "jpg"
}

/**
 * Copia el archivo elegido a la caché para que la subida tenga un tamaño
 * exacto. Devuelve null si supera el límite (así se convierte y comprime).
 */
private fun copyToFile(
    context: Context,
    uri: Uri,
    file: File,
    maxBytes: Long,
): Long? {
    val input = context.contentResolver.openInputStream(uri) ?: return null
    var total = 0L
    input.use { stream ->
        file.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return null
                out.write(buffer, 0, read)
            }
        }
    }
    return total.takeIf { it > 0 }
}

private fun convertToJpeg(
    context: Context,
    uri: Uri,
    dir: File,
    maxBytes: Long,
): PreparedImage {
    val (sourceWidth, sourceHeight) = readBounds(context, uri)
    val longest = max(sourceWidth, sourceHeight)
    val initialScale = if (longest > MAX_DIMENSION) {
        MAX_DIMENSION.toFloat() / longest
    } else {
        1f
    }
    var current: Bitmap = decodeScaled(context, uri, initialScale)
        ?: throw DannXError.Unexpected(
            "No se pudo leer la imagen. Probá con un JPG, PNG o WebP.",
        )
    var quality = 92
    var attempts = 0
    try {
        while (attempts < 10) {
            attempts++
            val file = File(dir, "upload-${System.nanoTime()}.jpg")
            file.outputStream().use { out ->
                current.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            if (file.length() > 0 && file.length() <= maxBytes) {
                return PreparedImage(file, "image/jpeg", "jpg", current.width, current.height)
            }
            file.delete()
            if (quality > 60) {
                quality -= 12
            } else {
                val next = Bitmap.createScaledBitmap(
                    current,
                    (current.width * 0.8f).toInt().coerceAtLeast(1),
                    (current.height * 0.8f).toInt().coerceAtLeast(1),
                    true,
                )
                if (next !== current) {
                    current.recycle()
                    current = next
                }
            }
        }
        throw DannXError.Unexpected(
            "La imagen sigue siendo muy pesada después de optimizarla. Probá con otra.",
        )
    } finally {
        if (!current.isRecycled) current.recycle()
    }
}

private fun decodeScaled(context: Context, uri: Uri, scale: Float): Bitmap? {
    return runCatching {
        if (scale <= 0f) return@runCatching null
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = (info.size.width * scale).toInt().coerceAtLeast(1)
            val height = (info.size.height * scale).toInt().coerceAtLeast(1)
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }.getOrNull() ?: runCatching {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(scale) }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()
}

private fun sampleFor(scale: Float): Int {
    if (scale >= 1f) return 1
    var sample = 1
    while (sample * 2 <= 1f / scale) sample *= 2
    return sample
}

private fun readBounds(context: Context, uri: Uri): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }
    return options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
}
