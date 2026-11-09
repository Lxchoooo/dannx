package com.dannx.walls.data

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Guarda en disco el último feed y las colecciones, para poder mostrar
 * contenido cuando no hay conexión.
 */
class FeedCache internal constructor(private val folder: File) : FeedCacheStore {
    constructor(context: Context) : this(context.cacheDir.resolve("feed-cache"))

    init {
        folder.mkdirs()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun readWallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        val file = folder.resolve(WALLPAPERS_FILE)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<CachedWallpaper>>(file.readText())
                .filter { it.id.isNotBlank() && it.imageUrl.isNotBlank() }
                .map { it.toWallpaper() }
        }.getOrDefault(emptyList())
    }

    override suspend fun writeWallpapers(items: List<Wallpaper>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = items.take(MAX_WALLPAPERS).map { CachedWallpaper.from(it) }
                writeAtomically(folder.resolve(WALLPAPERS_FILE), json.encodeToString(payload))
            }
        }
    }

    override suspend fun readCollections(): List<WallpaperCollection> = withContext(Dispatchers.IO) {
        val file = folder.resolve(COLLECTIONS_FILE)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<WallpaperCollection>>(file.readText())
        }.getOrDefault(emptyList())
    }

    override suspend fun writeCollections(items: List<WallpaperCollection>) {
        withContext(Dispatchers.IO) {
            runCatching {
                writeAtomically(
                    folder.resolve(COLLECTIONS_FILE),
                    json.encodeToString(items.take(MAX_COLLECTIONS)),
                )
            }
        }
    }

    /**
     * Escribe primero en un archivo temporal y lo reemplaza de una vez, para
     * que un cierre a mitad de escritura no deje el caché corrupto. El nombre
     * temporal es único para que dos escrituras simultáneas no se pisen.
     */
    private fun writeAtomically(file: File, content: String) {
        val temp = File.createTempFile("${file.name}.", ".tmp", file.parentFile)
        try {
            temp.writeText(content)
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    internal companion object {
        const val MAX_WALLPAPERS = 400
        const val MAX_COLLECTIONS = 100
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
    val featuredPosition: Int = 0,
    val createdAt: String = "",
    val tags: List<String> = emptyList(),
    val categoryId: String? = null,
    val storagePath: String = "",
    val createdBy: String = "",
    val updatedAt: String = "",
    val archivedAt: String = "",
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
        featuredPosition = featuredPosition,
        createdAt = createdAt,
        tags = tags,
        categoryId = categoryId,
        storagePath = storagePath,
        createdBy = createdBy,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
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
            featuredPosition = wallpaper.featuredPosition,
            createdAt = wallpaper.createdAt,
            tags = wallpaper.tags,
            categoryId = wallpaper.categoryId,
            storagePath = wallpaper.storagePath,
            createdBy = wallpaper.createdBy,
            updatedAt = wallpaper.updatedAt,
            archivedAt = wallpaper.archivedAt,
        )
    }
}
