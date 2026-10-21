package com.dannx.walls.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Guarda en disco el último feed y las colecciones, para poder mostrar
 * contenido cuando no hay conexión.
 */
class FeedCache(context: Context) {
    private val folder = context.cacheDir.resolve("feed-cache").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun readWallpapers(): List<Wallpaper> {
        val file = folder.resolve(WALLPAPERS_FILE)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<CachedWallpaper>>(file.readText())
                .map { it.toWallpaper() }
        }.getOrDefault(emptyList())
    }

    fun writeWallpapers(items: List<Wallpaper>) {
        runCatching {
            val payload = items.map { CachedWallpaper.from(it) }
            folder.resolve(WALLPAPERS_FILE).writeText(json.encodeToString(payload))
        }
    }

    fun readCollections(): List<WallpaperCollection> {
        val file = folder.resolve(COLLECTIONS_FILE)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<WallpaperCollection>>(file.readText())
        }.getOrDefault(emptyList())
    }

    fun writeCollections(items: List<WallpaperCollection>) {
        runCatching {
            folder.resolve(COLLECTIONS_FILE).writeText(json.encodeToString(items))
        }
    }

    private companion object {
        const val WALLPAPERS_FILE = "wallpapers.json"
        const val COLLECTIONS_FILE = "collections.json"
    }
}

@Serializable
private data class CachedWallpaper(
    val id: String,
    val title: String,
    val category: String,
    val imageUrl: String,
    val thumbUrl: String = "",
    val author: String = "DannX",
    val width: Int = 0,
    val height: Int = 0,
    val featured: Boolean = false,
    val createdAt: String = "",
    val tags: List<String> = emptyList(),
) {
    fun toWallpaper(): Wallpaper = Wallpaper(
        id = id,
        title = title,
        category = category,
        imageUrl = imageUrl,
        thumbUrl = thumbUrl,
        author = author,
        width = width,
        height = height,
        featured = featured,
        createdAt = createdAt,
        tags = tags,
    )

    companion object {
        fun from(wallpaper: Wallpaper): CachedWallpaper = CachedWallpaper(
            id = wallpaper.id,
            title = wallpaper.title,
            category = wallpaper.category,
            imageUrl = wallpaper.imageUrl,
            thumbUrl = wallpaper.thumbUrl,
            author = wallpaper.author,
            width = wallpaper.width,
            height = wallpaper.height,
            featured = wallpaper.featured,
            createdAt = wallpaper.createdAt,
            tags = wallpaper.tags,
        )
    }
}
