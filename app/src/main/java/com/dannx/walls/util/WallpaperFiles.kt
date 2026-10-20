package com.dannx.walls.util

import android.app.Activity
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.dannx.walls.data.Wallpaper
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object WallpaperFiles {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun download(wallpaper: Wallpaper): ByteArray = withContext(Dispatchers.IO) {
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

    suspend fun setAsWallpaper(context: Context, wallpaper: Wallpaper) {
        val bytes = download(wallpaper)
        withContext(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("La imagen no se pudo decodificar.")
            val manager = WallpaperManager.getInstance(context)
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
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
