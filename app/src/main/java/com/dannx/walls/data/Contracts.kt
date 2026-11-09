package com.dannx.walls.data

import android.net.Uri

import com.dannx.walls.util.SharedImage
import com.dannx.walls.util.WallpaperTarget

/**
 * Resultado de publicar un fondo. El aviso permite contar cuando la ficha se
 * creó pero una operación secundaria, como sumarlo a una colección, falló.
 */
data class UploadOutcome(
    val wallpaper: Wallpaper,
    val warning: String? = null,
)

/**
 * Contrato de los datos remotos. Permite probar el ViewModel con un fake.
 */
interface WallpapersRepository {
    val isConfigured: Boolean

    suspend fun loadWallpapers(): List<Wallpaper>
    suspend fun loadAdminWallpapers(): List<Wallpaper>
    suspend fun loadCategories(): List<WallpaperCategory>
    suspend fun loadCollections(): List<WallpaperCollection>
    suspend fun signIn(email: String, password: String): AdminSession
    suspend fun refreshSession(): AdminSession?
    suspend fun uploadWallpaper(
        uri: Uri,
        title: String,
        category: String,
        categoryId: String?,
        tags: List<String>,
        collectionId: String?,
    ): UploadOutcome

    suspend fun updateWallpaper(
        wallpaperId: String,
        title: String,
        category: String,
        categoryId: String?,
        tags: List<String>,
        featured: Boolean,
    ): Wallpaper

    suspend fun replaceWallpaperImage(wallpaper: Wallpaper, uri: Uri): Wallpaper
    suspend fun archiveWallpaper(wallpaperId: String, archived: Boolean)
    suspend fun deleteWallpaper(wallpaper: Wallpaper)

    suspend fun createCategory(name: String, position: Int): WallpaperCategory
    suspend fun updateCategory(id: String, name: String, position: Int): WallpaperCategory
    suspend fun deleteCategory(id: String, targetId: String? = null, targetName: String? = null)

    suspend fun createCollection(
        name: String,
        description: String,
        isPublic: Boolean,
    ): WallpaperCollection

    suspend fun updateCollection(
        id: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ): WallpaperCollection

    suspend fun addToCollection(collectionId: String, wallpaperId: String)
    suspend fun removeFromCollection(collectionId: String, wallpaperId: String)
    suspend fun setCollectionWallpapers(collectionId: String, wallpaperIds: List<String>)
    suspend fun reorderCollection(collectionId: String, wallpaperIds: List<String>)
    suspend fun deleteCollection(id: String)

    suspend fun addTag(tag: String, wallpaperIds: List<String>)
    suspend fun renameTag(oldTag: String, newTag: String)
    suspend fun deleteTag(tag: String)
}

/**
 * Contrato del almacenamiento local (favoritos, sesión, descargas).
 */
interface LocalStore {
    fun favorites(): Set<String>
    fun toggleFavorite(id: String): Set<String>
    fun recentSearches(): List<String>
    fun rememberSearch(query: String)
    fun clearRecentSearches()
    fun session(): AdminSession?
    fun saveSession(email: String, access: String?, refresh: String?, id: String?)
    fun clearSession()
    fun localCollections(): List<WallpaperCollection>
    fun saveLocalCollections(items: List<WallpaperCollection>)
    fun downloadedIds(): Set<String>
    fun downloadedUri(wallpaperId: String): String?
    fun rememberDownload(wallpaperId: String, uri: String)
    fun onboardingDone(): Boolean
    fun completeOnboarding()
}

/**
 * Contrato de la cache en disco del feed.
 */
interface FeedCacheStore {
    suspend fun readWallpapers(): List<Wallpaper>
    suspend fun writeWallpapers(items: List<Wallpaper>)
    suspend fun readCollections(): List<WallpaperCollection>
    suspend fun writeCollections(items: List<WallpaperCollection>)
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
