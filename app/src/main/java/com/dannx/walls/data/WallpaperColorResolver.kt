package com.dannx.walls.data

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Obtiene el color de un fondo a partir de sus tags o de su miniatura. */
fun interface WallpaperColorResolver {
    suspend fun resolve(wallpaper: Wallpaper): WallpaperColor?
}

/**
 * Resuelve el color con la miniatura que ya usa el feed. Coil se encarga de la
 * caché de disco y de red; acá solo se muestrea a 64 px y se cuenta el color
 * dominante, sin guardar bitmaps grandes en memoria.
 */
class CoilWallpaperColorResolver(
    private val context: Context,
) : WallpaperColorResolver {
    override suspend fun resolve(wallpaper: Wallpaper): WallpaperColor? {
        colorFromTags(wallpaper.tags)?.let { return it }
        val url = wallpaper.thumbnailUrl
        if (url.isBlank()) return null

        val request = ImageRequest.Builder(context)
            .data(url)
            .size(SAMPLE_SIZE)
            .allowHardware(false)
            .build()
        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            ?: return null
        val bitmap = drawable.toBitmap()
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        return withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            dominantColorBucket(pixels)
        }
    }

    private companion object {
        const val SAMPLE_SIZE = 64
    }
}
