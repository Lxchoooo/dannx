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

/** Filtros y ventana que pide el feed paginado. */
data class WallpaperFilter(
    val query: String = "",
    val category: String? = null,
    val tag: String? = null,
    val offset: Int = 0,
    val limit: Int = FEED_PAGE_SIZE,
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || category != null || tag != null
}

data class WallpaperPage(
    val items: List<Wallpaper>,
    val hasMore: Boolean,
)

const val FEED_PAGE_SIZE = 30

/** Cantidad máxima de tags que acepta un fondo en el editor. */
const val TagsLimit = 12

/** Fondos por edición de tags en bloque: al tope se corta la cola. */
const val BulkTagLimit = 50

/**
 * Fallo de una edición con su mensaje listo para la pantalla. Permite
 * repasar errores puntuales (una categoría reservada, un título vacío) sin
 * acoplar la interfaz a los detalles internos.
 */
class StudioEditException(message: String) : Exception(message)

/**
 * Contrato de los datos remotos. Permite probar el ViewModel con un fake.
 */
interface WallpapersRepository {
    val isConfigured: Boolean

    suspend fun loadWallpapersPage(filter: WallpaperFilter): WallpaperPage
    suspend fun loadAdminWallpapers(): List<Wallpaper>
    suspend fun loadCategories(): List<WallpaperCategory>
    suspend fun loadCollections(): List<WallpaperCollection>

    /**
     * Carga fondos por id aunque no estén en la página actual del feed. Se usa
     * al abrir un detalle restaurado o cuando la colección apunta a imágenes
     * que todavía no están en memoria.
     */
    suspend fun loadWallpapersByIds(ids: List<String>): List<Wallpaper>

    suspend fun loadWallpaper(id: String): Wallpaper? =
        loadWallpapersByIds(listOf(id)).firstOrNull()

    suspend fun loadCollection(id: String): WallpaperCollection?
    suspend fun loadAppSettings(): AppSettings
    suspend fun saveAppSettings(settings: AppSettings): AppSettings
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
        author: String,
        tags: List<String>,
        featured: Boolean,
        featuredPosition: Int,
    ): Wallpaper

    suspend fun reorderFeatured(ids: List<String>)
    suspend fun bulkSetFeatured(ids: List<String>, featured: Boolean)
    suspend fun bulkSetArchived(ids: List<String>, archived: Boolean)
    suspend fun bulkSetCategory(ids: List<String>, categoryId: String)
    suspend fun softDeleteWallpapers(ids: List<String>)
    suspend fun restoreWallpapers(ids: List<String>)
    suspend fun replaceWallpaperImage(wallpaper: Wallpaper, uri: Uri): Wallpaper
    suspend fun archiveWallpaper(wallpaperId: String, archived: Boolean)

    /**
     * Borra el registro primero y después la imagen. Devuelve false cuando el
     * registro ya no existe pero el archivo de imagen quedó en Storage, para
     * que la interfaz avise en lugar de dar la baja por terminada en silencio.
     */
    suspend fun deleteWallpaper(wallpaper: Wallpaper): Boolean

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

    /**
     * Fondo del día: cuenta los vivos (sin papelera ni archivados, aunque haya
     * sesión de administración) con conteo exacto y devuelve el wallpaper de
     * la posición pedida del listado por id ascendente. Devuelve null cuando
     * conviene ocultar la tarjeta en silencio: falló el conteo, no hay vivos o
     * cambió el total y no se pudo reajustar la posición.
     */
    suspend fun loadDailyWallpaper(positionSeed: Long): Wallpaper?
}

/**
 * Contrato del almacenamiento local (favoritos, sesión, descargas).
 */
interface LocalStore {
    fun favorites(): Set<String>
    fun toggleFavorite(id: String): Set<String>

    /**
     * Quita ids del listado de favoritos. Se usa cuando un fondo se borra de
     * verdad: los favoritos no deben recordar fondos inexistentes.
     */
    fun removeFavorites(ids: Set<String>)
    fun recentSearches(): List<String>
    fun rememberSearch(query: String)
    fun clearRecentSearches()
    fun appSettings(): AppSettings
    fun saveAppSettings(settings: AppSettings)
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

    /**
     * Fondo del día elegido en este teléfono: solo responde si la fecha
     * guardada coincide con la pedida, para que la tarjeta no cambie durante
     * el día aunque el total de vivos cambie.
     */
    fun dailyWallpaper(date: String): Wallpaper?
    fun rememberDailyWallpaper(date: String, wallpaper: Wallpaper)
}

/**
 * Contrato de la cache en disco del feed.
 */
interface FeedCacheStore {
    suspend fun readWallpapers(): List<Wallpaper>
    suspend fun writeWallpapers(items: List<Wallpaper>)
    suspend fun readCollections(): List<WallpaperCollection>
    suspend fun writeCollections(items: List<WallpaperCollection>)
    suspend fun sizeBytes(): Long
    suspend fun clear()
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

    suspend fun prepareShareMany(wallpapers: List<Wallpaper>): List<SharedImage> =
        wallpapers.map { prepareShare(it) }

    fun shareMany(images: List<SharedImage>, title: String) {
        images.firstOrNull()?.let { share(it, title) }
    }
}
