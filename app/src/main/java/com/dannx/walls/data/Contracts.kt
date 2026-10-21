package com.dannx.walls.data

import android.net.Uri

import com.dannx.walls.util.SharedImage
import com.dannx.walls.util.WallpaperTarget

/**
 * Contrato de los datos remotos. Permite probar el ViewModel con un fake.
 */
interface WallpapersRepository {
    val isConfigured: Boolean

    suspend fun loadWallpapers(): List<Wallpaper>
    suspend fun loadCollections(): List<WallpaperCollection>
    suspend fun signIn(email: String, password: String): AdminSession
    suspend fun refreshSession(): AdminSession?
    suspend fun uploadWallpaper(
        uri: Uri,
        title: String,
        category: String,
        tags: List<String>,
        collectionId: String?,
    ): Wallpaper

    suspend fun createCollection(
        name: String,
        description: String,
        isPublic: Boolean,
    ): WallpaperCollection

    suspend fun addToCollection(collectionId: String, wallpaperId: String)
    suspend fun removeFromCollection(collectionId: String, wallpaperId: String)
    suspend fun deleteCollection(id: String)
    suspend fun incrementDownloads(id: String)
}

/**
 * Contrato del almacenamiento local (favoritos, sesión, descargas).
 */
interface LocalStore {
    fun favorites(): Set<String>
    fun toggleFavorite(id: String): Set<String>
    fun session(): AdminSession?
    fun saveSession(email: String, access: String?, refresh: String?, id: String?)
    fun clearSession()
    fun localCollections(): List<WallpaperCollection>
    fun saveLocalCollections(items: List<WallpaperCollection>)
    fun downloadedIds(): Set<String>
    fun downloadedUri(wallpaperId: String): String?
    fun rememberDownload(wallpaperId: String, uri: String)
}

/**
 * Contrato de la cache en disco del feed.
 */
interface FeedCacheStore {
    fun readWallpapers(): List<Wallpaper>
    fun writeWallpapers(items: List<Wallpaper>)
    fun readCollections(): List<WallpaperCollection>
    fun writeCollections(items: List<WallpaperCollection>)
}

/**
 * Operaciones de archivo que necesitan contexto de Android.
 */
interface WallpaperActions {
    suspend fun saveToGallery(
        wallpaper: Wallpaper,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String

    suspend fun setAsWallpaper(wallpaper: Wallpaper, target: WallpaperTarget): WallpaperTarget
    suspend fun prepareShare(wallpaper: Wallpaper): SharedImage
    fun share(image: SharedImage, title: String)
    fun exists(uri: String): Boolean
}
