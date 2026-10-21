package com.dannx.walls

import android.net.Uri
import com.dannx.walls.data.AdminSession
import com.dannx.walls.data.DannXError
import com.dannx.walls.data.FeedCacheStore
import com.dannx.walls.data.LocalStore
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperActions
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.WallpapersRepository
import com.dannx.walls.util.SharedImage
import com.dannx.walls.util.WallpaperTarget

/**
 * Fakes usados por los tests del ViewModel.
 */
class FakeRepository : WallpapersRepository {
    var wallpapers: List<Wallpaper> = emptyList()
    var collections: List<WallpaperCollection> = emptyList()
    var failWith: Throwable? = null
    var failTimes: Int = 0
    var loadCalls: Int = 0
    var session: AdminSession? = null

    override val isConfigured: Boolean = true

    override suspend fun loadWallpapers(): List<Wallpaper> {
        loadCalls += 1
        val error = failWith
        if (error != null && failTimes > 0) {
            failTimes -= 1
            throw error
        }
        return wallpapers
    }

    override suspend fun loadCollections(): List<WallpaperCollection> {
        val error = failWith
        if (error != null && failTimes > 0) {
            failTimes -= 1
            throw error
        }
        return collections
    }

    override suspend fun signIn(email: String, password: String): AdminSession =
        session ?: AdminSession(email, "token")

    override suspend fun refreshSession(): AdminSession? = session

    override suspend fun uploadWallpaper(
        uri: Uri,
        title: String,
        category: String,
        tags: List<String>,
        collectionId: String?,
    ): Wallpaper = Wallpaper(id = "new", title = title, category = category, imageUrl = "url")

    override suspend fun createCollection(
        name: String,
        description: String,
        isPublic: Boolean,
    ): WallpaperCollection = WallpaperCollection(id = "c1", name = name)

    override suspend fun addToCollection(collectionId: String, wallpaperId: String) = Unit

    override suspend fun removeFromCollection(collectionId: String, wallpaperId: String) = Unit

    override suspend fun deleteCollection(id: String) = Unit

    override suspend fun incrementDownloads(id: String) = Unit
}

class FakeStore : LocalStore {
    private val favoriteIds = mutableSetOf<String>()
    private val downloads = mutableMapOf<String, String>()
    private var collections: List<WallpaperCollection> = emptyList()
    private var storedSession: AdminSession? = null

    override fun favorites(): Set<String> = favoriteIds.toSet()

    override fun toggleFavorite(id: String): Set<String> {
        if (!favoriteIds.add(id)) favoriteIds.remove(id)
        return favoriteIds.toSet()
    }

    override fun session(): AdminSession? = storedSession

    override fun saveSession(email: String, access: String?, refresh: String?, id: String?) {
        storedSession = AdminSession(email, access.orEmpty(), id.orEmpty())
    }

    override fun clearSession() {
        storedSession = null
    }

    override fun localCollections(): List<WallpaperCollection> = collections

    override fun saveLocalCollections(items: List<WallpaperCollection>) {
        collections = items
    }

    override fun downloadedIds(): Set<String> = downloads.keys.toSet()

    override fun downloadedUri(wallpaperId: String): String? = downloads[wallpaperId]

    override fun rememberDownload(wallpaperId: String, uri: String) {
        downloads[wallpaperId] = uri
    }
}

class FakeCache : FeedCacheStore {
    private var wallpapers: List<Wallpaper> = emptyList()
    private var collections: List<WallpaperCollection> = emptyList()

    override fun readWallpapers(): List<Wallpaper> = wallpapers

    override fun writeWallpapers(items: List<Wallpaper>) {
        wallpapers = items
    }

    override fun readCollections(): List<WallpaperCollection> = collections

    override fun writeCollections(items: List<WallpaperCollection>) {
        collections = items
    }
}

class FakeActions : WallpaperActions {
    var savedUri: Uri? = null
    var appliedTarget: WallpaperTarget? = null
    var shared: Boolean = false
    var failWith: Throwable? = null

    override suspend fun saveToGallery(
        wallpaper: Wallpaper,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Uri {
        failWith?.let { throw it }
        onProgress(50, 100)
        onProgress(100, 100)
        return savedUri ?: Uri.parse("content://media/test")
    }

    override suspend fun setAsWallpaper(
        wallpaper: Wallpaper,
        target: WallpaperTarget,
    ): WallpaperTarget {
        failWith?.let { throw it }
        appliedTarget = target
        return target
    }

    override suspend fun prepareShare(wallpaper: Wallpaper): SharedImage =
        SharedImage(Uri.parse("content://test/share"), "image/jpeg")

    override fun share(image: SharedImage, title: String) {
        shared = true
    }

    override fun exists(uri: Uri): Boolean = true
}
