package com.dannx.walls.util

import android.app.Activity
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import com.dannx.walls.data.Wallpaper
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class WallpaperTarget {
    SYSTEM,
    LOCK,
    BOTH,
}

object WallpaperFiles {
    private const val DEFAULT_BUFFER_SIZE = 64 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        wallpaper: Wallpaper,
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(wallpaper.imageUrl)
            .header("User-Agent", "DannX/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw IOException("No se pudo descargar la imagen (${response.code}).")
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.isNotBlank() && !contentType.startsWith("image/")) {
                throw IOException("La respuesta no es una imagen.")
            }
            body.bytes()
        }
    }

    /**
     * Descarga la imagen a disco escribiendo de a chunks, para no cargar el
     * archivo entero en memoria. Es cancelable: al cancelar el coroutine se
     * corta la escritura.
     */
    suspend fun downloadToFile(
        wallpaper: Wallpaper,
        destination: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(wallpaper.imageUrl)
            .header("User-Agent", "DannX/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw IOException("No se pudo descargar la imagen (${response.code}).")
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.isNotBlank() && !contentType.startsWith("image/")) {
                throw IOException("La respuesta no es una imagen.")
            }
            destination.parentFile?.mkdirs()
            val total = body.contentLength()
            var downloaded = 0L
            try {
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
            destination
        }
    }

    suspend fun save(context: Context, wallpaper: Wallpaper): Uri {
        val bytes = download(wallpaper)
        return withContext(Dispatchers.IO) {
            val mime = mimeOf(bytes)
            val extension = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val safeTitle = wallpaper.title
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "dannx" }
                .take(40)
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "dannx-$safeTitle-${System.currentTimeMillis()}.$extension",
                )
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/DannX",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("No se pudo crear el archivo en Galería.")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                } ?: throw IOException("No se pudo escribir el archivo.")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
    }

    /**
     * Guarda la imagen en la galería escribiendo el archivo directo sobre la
     * fila de MediaStore, sin cargar el archivo entero en memoria.
     */
    suspend fun saveToGallery(
        context: Context,
        wallpaper: Wallpaper,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Uri = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(wallpaper.imageUrl)
            .header("User-Agent", "DannX/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw IOException("No se pudo descargar la imagen (${response.code}).")
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.isNotBlank() && !contentType.startsWith("image/")) {
                throw IOException("La respuesta no es una imagen.")
            }
            val mime = mimeFromContentType(contentType)
            val extension = extensionFor(mime)
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    galleryFileName(wallpaper, extension),
                )
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/DannX",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("No se pudo crear el archivo en Galería.")
            try {
                val total = body.contentLength()
                var downloaded = 0L
                resolver.openOutputStream(uri)?.use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                } ?: throw IOException("No se pudo escribir el archivo.")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
    }

    /**
     * Abre un archivo guardado en la galería con la app de imágenes del sistema.
     */
    fun open(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Indica si el archivo guardado sigue existiendo en el dispositivo.
     */
    fun exists(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun galleryFileName(wallpaper: Wallpaper, extension: String): String {
        val slug = wallpaper.id
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "dannx" }
        return "dannx-$slug-${System.currentTimeMillis()}.$extension"
    }

    private fun mimeFromContentType(contentType: String): String = when {
        contentType.contains("png", ignoreCase = true) -> "image/png"
        contentType.contains("webp", ignoreCase = true) -> "image/webp"
        contentType.contains("gif", ignoreCase = true) -> "image/gif"
        contentType.contains("heic", ignoreCase = true) -> "image/heic"
        contentType.contains("heif", ignoreCase = true) -> "image/heif"
        contentType.contains("jpeg", ignoreCase = true) ||
            contentType.contains("jpg", ignoreCase = true) -> "image/jpeg"
        else -> "image/jpeg"
    }

    private fun extensionFor(mime: String): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> "jpg"
    }

    /**
     * Aplica el fondo de pantalla en la pantalla de inicio, en el bloqueo o en
     * ambas. Devuelve qué destino quedó aplicado realmente (algunos teléfonos no
     * permiten modificar el bloqueo).
     */
    suspend fun setAsWallpaper(
        context: Context,
        wallpaper: Wallpaper,
        target: WallpaperTarget,
    ): WallpaperTarget = withContext(Dispatchers.IO) {
        val size = wallpaperTargetSize(context)
        val folder = File(context.cacheDir, "wallpaper-tmp").apply { mkdirs() }
        val temp = File(folder, "${wallpaper.id}-${System.currentTimeMillis()}.img")
        try {
            downloadToFile(wallpaper, temp)
            val bitmap = decodeCroppedBitmap(temp, size.x, size.y)
            try {
                applyBitmap(context, bitmap, target)
            } finally {
                bitmap.recycle()
            }
        } finally {
            temp.delete()
        }
    }

    private fun applyBitmap(
        context: Context,
        bitmap: Bitmap,
        target: WallpaperTarget,
    ): WallpaperTarget {
        val manager = WallpaperManager.getInstance(context)
        return when (target) {
            WallpaperTarget.SYSTEM -> {
                manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                WallpaperTarget.SYSTEM
            }

            WallpaperTarget.LOCK -> {
                try {
                    manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    WallpaperTarget.LOCK
                } catch (error: Exception) {
                    manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    WallpaperTarget.SYSTEM
                }
            }

            WallpaperTarget.BOTH -> {
                try {
                    manager.setBitmap(
                        bitmap,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                    )
                    WallpaperTarget.BOTH
                } catch (error: Exception) {
                    manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    try {
                        manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        WallpaperTarget.BOTH
                    } catch (lockError: Exception) {
                        WallpaperTarget.SYSTEM
                    }
                }
            }
        }
    }

    fun share(context: Context, uri: Uri, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Compartir fondo").apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }

    private fun mimeOf(bytes: ByteArray): String = when {
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() -> "image/png"

        bytes.size >= 12 &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"

        else -> "image/jpeg"
    }
}

/**
 * Decodifica solo la región central necesaria y con muestreo, para que una
 * imagen enorme no se cargue completa en memoria.
 */
internal fun decodeCroppedBitmap(
    file: File,
    targetWidth: Int,
    targetHeight: Int,
): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sourceWidth = bounds.outWidth
    val sourceHeight = bounds.outHeight
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        throw IOException("La imagen no se pudo decodificar.")
    }
    val crop = centerCropRect(sourceWidth, sourceHeight, targetWidth, targetHeight)
    val sampleSize = calculateInSampleSize(crop.width, crop.height, targetWidth, targetHeight)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val region = runCatching {
        val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false)
        try {
            decoder.decodeRegion(Rect(crop.left, crop.top, crop.right, crop.bottom), options)
        } finally {
            @Suppress("DEPRECATION")
            runCatching { decoder.recycle() }
        }
    }.getOrNull()
    val cropped = region ?: decodeAndCropWholeFile(file, crop, sampleSize, options)
    if (cropped.width == targetWidth && cropped.height == targetHeight) {
        return cropped
    }
    val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
    if (scaled != cropped) {
        cropped.recycle()
    }
    return scaled
}

private fun decodeAndCropWholeFile(
    file: File,
    crop: CropRect,
    sampleSize: Int,
    options: BitmapFactory.Options,
): Bitmap {
    val full = BitmapFactory.decodeFile(file.absolutePath, options)
        ?: throw IOException("La imagen no se pudo decodificar.")
    val x = (crop.left / sampleSize).coerceIn(0, (full.width - 1).coerceAtLeast(0))
    val y = (crop.top / sampleSize).coerceIn(0, (full.height - 1).coerceAtLeast(0))
    val width = (crop.width / sampleSize).coerceIn(1, full.width - x)
    val height = (crop.height / sampleSize).coerceIn(1, full.height - y)
    val cropped = Bitmap.createBitmap(full, x, y, width, height)
    if (cropped != full) {
        full.recycle()
    }
    return cropped
}

/**
 * Tamaño real de la pantalla en píxeles, con los valores de WallpaperManager
 * como respaldo.
 */
internal fun wallpaperTargetSize(context: Context): android.graphics.Point {
    val metrics = DisplayMetrics()
    runCatching {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
    }
    val manager = WallpaperManager.getInstance(context)
    val width = if (metrics.widthPixels > 0) metrics.widthPixels else manager.desiredMinimumWidth
    val height = if (metrics.heightPixels > 0) metrics.heightPixels else manager.desiredMinimumHeight
    return android.graphics.Point(width.coerceAtLeast(1), height.coerceAtLeast(1))
}
