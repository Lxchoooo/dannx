package com.dannx.walls

import android.net.Uri
import com.dannx.walls.data.AdminSession
import com.dannx.walls.data.DannXError
import com.dannx.walls.data.FeedCacheStore
import com.dannx.walls.data.LocalStore
import com.dannx.walls.data.UploadOutcome
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperActions
import com.dannx.walls.data.WallpaperCategory
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.WallpapersRepository
import com.dannx.walls.util.SharedImage
import com.dannx.walls.util.WallpaperTarget

/**
 * Fakes usados por los tests del ViewModel.
 */
class FakeRepository : WallpapersRepository {
    var wallpapers: List<Wallpaper> = emptyList()
    var adminWallpapers: List<Wallpaper>? = null
    var categories: List<WallpaperCategory> = emptyList()
    var collections: List<WallpaperCollection> = emptyList()
    var failWith: Throwable? = null
    var failTimes: Int = 0
    var loadCalls: Int = 0
    var session: AdminSession? = null
    var uploadWarning: String? = null
    var reassignedCategory: Triple<String, String, String>? = null
    var lastCollectionWallpapers: Pair<String, List<String>>? = null

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

    override suspend fun loadAdminWallpapers(): List<Wallpaper> = adminWallpapers ?: wallpapers

    override suspend fun loadCategories(): List<WallpaperCategory> = categories

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
        categoryId: String?,
        tags: List<String>,
        collectionId: String?,
    ): UploadOutcome = UploadOutcome(
        wallpaper = Wallpaper(
            id = "new",
            title = title,
            category = category,
            categoryId = categoryId,
            imageUrl = "url",
            tags = tags,
        ),
        warning = uploadWarning,
    )

    override suspend fun updateWallpaper(
        wallpaperId: String,
        title: String,
        category: String,
        categoryId: String?,
        tags: List<String>,
        featured: Boolean,
    ): Wallpaper = wallpapers.firstOrNull { it.id == wallpaperId }
        ?.copy(
            title = title,
            category = category,
            categoryId = categoryId,
            tags = tags,
            featured = featured,
        )
        ?: Wallpaper(
            id = wallpaperId,
            title = title,
            category = category,
            categoryId = categoryId,
            imageUrl = "url",
            tags = tags,
            featured = featured,
        )

    override suspend fun replaceWallpaperImage(wallpaper: Wallpaper, uri: Uri): Wallpaper =
        wallpaper.copy(imageUrl = uri.toString(), thumbUrl = "")

    override suspend fun archiveWallpaper(wallpaperId: String, archived: Boolean) {
        wallpapers = wallpapers.map {
            if (it.id == wallpaperId) {
                it.copy(archivedAt = if (archived) "archived" else "")
            } else {
                it
            }
        }
    }

    override suspend fun deleteWallpaper(wallpaper: Wallpaper) {
        wallpapers = wallpapers.filterNot { it.id == wallpaper.id }
    }

    override suspend fun createCategory(name: String, position: Int): WallpaperCategory =
        WallpaperCategory(id = "cat-${categories.size + 1}", name = name, position = position)

    override suspend fun updateCategory(
        id: String,
        name: String,
        position: Int,
    ): WallpaperCategory = WallpaperCategory(id = id, name = name, position = position)

    override suspend fun reassignCategory(
        categoryId: String,
        targetId: String,
        targetName: String,
    ) {
        reassignedCategory = Triple(categoryId, targetId, targetName)
    }

    override suspend fun deleteCategory(id: String) = Unit

    override suspend fun createCollection(
        name: String,
        description: String,
        isPublic: Boolean,
    ): WallpaperCollection = WallpaperCollection(id = "c1", name = name)

    override suspend fun updateCollection(
        id: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ): WallpaperCollection = collections.firstOrNull { it.id == id }?.copy(
        name = name,
        description = description,
        isPublic = isPublic,
    ) ?: WallpaperCollection(id = id, name = name)

    override suspend fun addToCollection(collectionId: String, wallpaperId: String) = Unit

    override suspend fun removeFromCollection(collectionId: String, wallpaperId: String) = Unit

    override suspend fun setCollectionWallpapers(
        collectionId: String,
        wallpaperIds: List<String>,
    ) {
        lastCollectionWallpapers = collectionId to wallpaperIds
    }

    override suspend fun reorderCollection(collectionId: String, wallpaperIds: List<String>) = Unit

    override suspend fun deleteCollection(id: String) = Unit

    override suspend fun addTag(tag: String, wallpaperIds: List<String>) = Unit

    override suspend fun renameTag(oldTag: String, newTag: String) = Unit

    override suspend fun deleteTag(tag: String) = Unit
}

class FakeStore : LocalStore {
    private val favoriteIds = mutableSetOf<String>()
    private val downloads = mutableMapOf<String, String>()
    private var collections: List<WallpaperCollection> = emptyList()
    private var storedSession: AdminSession? = null
    private var onboardingDone = false

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

    override fun onboardingDone(): Boolean = onboardingDone

    override fun completeOnboarding() {
        onboardingDone = true
    }
}

class FakeCache : FeedCacheStore {
    private var wallpapers: List<Wallpaper> = emptyList()
    private var collections: List<WallpaperCollection> = emptyList()

    override suspend fun readWallpapers(): List<Wallpaper> = wallpapers

    override suspend fun writeWallpapers(items: List<Wallpaper>) {
        wallpapers = items
    }

    override suspend fun readCollections(): List<WallpaperCollection> = collections

    override suspend fun writeCollections(items: List<WallpaperCollection>) {
        collections = items
    }
}

class FakeActions : WallpaperActions {
    var savedUri: String? = null
    var appliedTarget: WallpaperTarget? = null
    var shared: Boolean = false
    var failWith: Throwable? = null

    override suspend fun saveToGallery(
        wallpaper: Wallpaper,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String {
        failWith?.let { throw it }
        onProgress(50, 100)
        onProgress(100, 100)
        return savedUri ?: "content://media/test"
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
        SharedImage("content://test/share", "image/jpeg")

    override fun share(image: SharedImage, title: String) {
        shared = true
    }

    override fun exists(uri: String): Boolean = true
}
