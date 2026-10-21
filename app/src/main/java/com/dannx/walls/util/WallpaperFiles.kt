package com.dannx.walls.util

import android.app.Activity
import android.app.WallpaperManager
import android.content.ClipData
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
import androidx.core.content.FileProvider
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperActions
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

class WallpaperFiles(private val context: Context) : WallpaperActions {
    private val defaultBufferSize = 64 * 1024
    private val maxSharedFiles = 8
    private val sharedMaxAgeMs = 6 * 60 * 60 * 1000L
    private val prefixSize = 16

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

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
                        val buffer = ByteArray(defaultBufferSize)
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

    /**
     * Guarda la imagen en la galería escribiendo el archivo directo sobre la
     * fila de MediaStore, sin cargar el archivo entero en memoria.
     */
    override suspend fun saveToGallery(
        wallpaper: Wallpaper,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
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
            val total = body.contentLength()
            body.byteStream().use { input ->
                val prefix = ByteArray(prefixSize)
                var read = 0
                while (read < prefixSize) {
                    val step = input.read(prefix, read, prefixSize - read)
                    if (step < 0) break
                    read += step
                }
                val format = detectImageFormat(prefix.copyOf(read))
                    ?: mimeFromContentType(contentType).let { ImageFormat(it, extensionFor(it)) }
                val values = ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        galleryFileName(wallpaper, format.extension),
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, format.mime)
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
                    var downloaded = 0L
                    resolver.openOutputStream(uri)?.use { output ->
                        output.write(prefix, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                        val buffer = ByteArray(defaultBufferSize)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val chunk = input.read(buffer)
                            if (chunk < 0) break
                            output.write(buffer, 0, chunk)
                            downloaded += chunk
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    } ?: throw IOException("No se pudo escribir el archivo.")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri.toString()
                } catch (error: Exception) {
                    resolver.delete(uri, null, null)
                    throw error
                }
            }
        }
    }

    /**
     * Abre un archivo guardado en la galería con la app de imágenes del sistema.
     */
    fun open(uri: String) {
        val parsed = Uri.parse(uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(parsed, context.contentResolver.getType(parsed) ?: "image/*")
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
    override fun exists(uri: String): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
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
    override suspend fun setAsWallpaper(
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
                applyBitmap(bitmap, target)
            } finally {
                bitmap.recycle()
            }
        } finally {
            temp.delete()
        }
    }

    private fun applyBitmap(
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

    /**
     * Prepara la imagen para compartirla: la copia en la carpeta de caché con la
     * extensión real, borra las copias viejas y devuelve la uri con permisos
     * temporales. No deja copias en la galería.
     */
    override suspend fun prepareShare(wallpaper: Wallpaper): SharedImage {
        val folder = File(context.cacheDir, "shared").apply { mkdirs() }
        pruneSharedCache(folder)
        val temp = File(folder, "dannx-${wallpaper.id}-${System.currentTimeMillis()}.part")
        try {
            downloadToFile(wallpaper, temp)
            val format = detectImageFormat(temp.readHeader(prefixSize))
                ?: ImageFormat("image/jpeg", "jpg")
            val file = File(folder, "dannx-${wallpaper.id}-${System.currentTimeMillis()}.${format.extension}")
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            return SharedImage(uri = uri.toString(), mime = format.mime)
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    private fun File.readHeader(size: Int): ByteArray {
        val header = ByteArray(size)
        var read = 0
        inputStream().use { input ->
            while (read < size) {
                val step = input.read(header, read, size - read)
                if (step < 0) break
                read += step
            }
        }
        return header.copyOf(read)
    }

    private fun pruneSharedCache(folder: File) {
        val files = folder.listFiles()?.sortedBy { it.lastModified() } ?: return
        files.dropLast(maxSharedFiles).forEach { it.delete() }
        val deadline = System.currentTimeMillis() - sharedMaxAgeMs
        files.filter { it.lastModified() < deadline }.forEach { it.delete() }
    }

    override fun share(image: SharedImage, title: String) {
        val uri = Uri.parse(image.uri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = image.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newRawUri("DannX", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Compartir fondo").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
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
