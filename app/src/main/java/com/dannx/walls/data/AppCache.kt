package com.dannx.walls.data

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Espacio que ocupan la caché del feed y la de las imágenes. */
interface AppCacheStore {
    suspend fun sizeBytes(): Long

    suspend fun clear()
}

/** Suma y borra las dos cachés que administra la app. */
class AppCache(
    private val feedCache: FeedCacheStore,
    private val imageCache: ImageCacheStore,
) : AppCacheStore {
    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        feedCache.sizeBytes() + imageCache.sizeBytes()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        feedCache.clear()
        imageCache.clear()
    }
}

/** Tamaño y borrado de la caché en disco de imágenes. */
interface ImageCacheStore {
    suspend fun sizeBytes(): Long

    suspend fun clear()
}

@OptIn(ExperimentalCoilApi::class)
class CoilImageCache(private val context: Context) : ImageCacheStore {
    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        runCatching { context.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching { context.imageLoader.diskCache?.clear() }
        }
    }
}

/** Implementación vacía para tests. */
object EmptyAppCache : AppCacheStore {
    override suspend fun sizeBytes(): Long = 0L

    override suspend fun clear() = Unit
}
