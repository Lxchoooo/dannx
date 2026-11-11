package com.dannx.walls

import androidx.compose.runtime.Immutable
import com.dannx.walls.data.AdminSession
import com.dannx.walls.data.AppSettings
import com.dannx.walls.data.CategoryFeatured
import com.dannx.walls.data.ThemeMode
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCategory
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.WallpaperColor
import com.dannx.walls.data.collectTags
import com.dannx.walls.data.filterWallpapers
import com.dannx.walls.data.isLocal
import com.dannx.walls.data.normalizeSearch
import java.util.Locale

enum class UiAction {
    DOWNLOAD,
    APPLY,
    SHARE,
}

sealed interface MessageAction {
    val label: String

    data class OpenFile(override val label: String, val uri: String) : MessageAction

    data class RetryDownload(
        override val label: String,
        val wallpaperId: String,
    ) : MessageAction
}

data class DownloadPrompt(
    val wallpaperId: String,
    val uri: String,
)

/**
 * Colección resuelta para su pantalla de detalle, con sus fondos completos.
 */
@Immutable
data class CollectionDetailData(
    val collection: WallpaperCollection,
    val wallpapers: List<Wallpaper>,
)

/**
 * Cierre de una edición de tags en bloque: el tag tocado, si fue alta o baja,
 * cuántos fondos quedaron bien y lista de los que fallaron para reintentos.
 */
data class BulkTagResult(
    val tag: String,
    val add: Boolean,
    val saved: Int,
    val failed: List<String>,
)

/**
 * Estado de una fuente de datos remota: cargando, con contenido, vacía o con
 * error. Reemplaza los booleanos sueltos de carga y error.
 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Content<T>(val data: T) : LoadState<T>
    data object Empty : LoadState<Nothing>
    data class Error(val message: String, val kind: ErrorKind) : LoadState<Nothing>
}

enum class ErrorKind {
    OFFLINE,
    SERVER,
    SESSION,
    PERMISSION,
    UNEXPECTED,
}

/**
 * Estado de las acciones en curso (descargar, aplicar, compartir). Vive
 * aparte del estado general para que el progreso no recomponga toda la app.
 */
@Immutable
data class ActionState(
    val active: UiAction? = null,
    val progress: Int? = null,
)

@Immutable
data class UiState(
    val feed: LoadState<List<Wallpaper>> = LoadState.Loading,
    val collections: LoadState<List<WallpaperCollection>> = LoadState.Loading,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val loadMoreError: String? = null,
    val wallpapers: List<Wallpaper> = emptyList(),
    val adminWallpapers: List<Wallpaper> = emptyList(),
    val trashWallpapers: List<Wallpaper> = emptyList(),
    val categories: List<WallpaperCategory> = emptyList(),
    val collectionItems: List<WallpaperCollection> = emptyList(),
    val detailWallpaperId: String? = null,
    val detailWallpaper: LoadState<Wallpaper>? = null,
    val detailCollectionId: String? = null,
    val detailCollection: LoadState<CollectionDetailData>? = null,
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val category: String? = null,
    val tag: String? = null,
    /** Color elegido en el feed; null = sin filtro de color. */
    val color: WallpaperColor? = null,
    /** Color resuelto por fondo. Se completa al usar el filtro. */
    val wallpaperColors: Map<String, WallpaperColor> = emptyMap(),
    val classifyingColors: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val session: AdminSession? = null,
    val isDemo: Boolean = true,
    val uploading: Boolean = false,
    val onboardingDone: Boolean = false,
    val collectionBusy: Boolean = false,
    val studioBusy: Boolean = false,
    /** Fondo elegido para hoy en este teléfono; null = tarjeta oculta. */
    val dailyWallpaper: Wallpaper? = null,
    /**
     * Contadores que avisan una sola vez que una acción terminó bien, para el
     * tacto de confirmación (aplicar fondo, crear colección).
     */
    val applyTick: Int = 0,
    val collectionCreatedTick: Int = 0,
    /** Edición en bloque de tags: (fondos editados, total). */
    val bulkProgress: Pair<Int, Int>? = null,
    val bulkResult: BulkTagResult? = null,
    val uploadSuccessCount: Int = 0,
    val authBusy: Boolean = false,
    val message: String? = null,
    val messageAction: MessageAction? = null,
    val downloadedIds: Set<String> = emptySet(),
    val downloadPrompt: DownloadPrompt? = null,
    val offline: Boolean = false,
    val selectedUploadUri: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val liteMode: Boolean = false,
    val openEditorOnApply: Boolean = true,
    /** Espacio que ocupa la caché local, en bytes. */
    val cacheSizeBytes: Long = 0L,
    val appSettings: AppSettings = AppSettings(),
) {
    val filtered: List<Wallpaper>
        get() = filterWallpapers(wallpapers, query, category, tag)
            .filterNot { it.isDeleted }
            .filter { color == null || wallpaperColors[it.id] == color }
            .let { items ->
                if (category == CategoryFeatured) {
                    items.sortedBy { it.featuredPosition }
                } else {
                    items
                }
            }

    val featured: List<Wallpaper>
        get() = wallpapers
            .filter { it.featured && !it.isDeleted }
            .sortedBy { it.featuredPosition }

    val favoriteWallpapers: List<Wallpaper>
        get() = wallpapers.filter { it.id in favorites && !it.isDeleted }

    val availableTags: List<String>
        get() = collectTags(wallpapers)

    /** Etiquetas y autores que coinciden con lo que se está escribiendo. */
    val searchSuggestions: List<String>
        get() {
            val term = normalizeSearch(query)
            if (term.length < 2) return emptyList()
            val tags = availableTags.filter { normalizeSearch(it).contains(term) }
            val authors = wallpapers
                .asSequence()
                .map { it.author.trim() }
                .filter { it.isNotBlank() && normalizeSearch(it).contains(term) }
                .toList()
            return (tags + authors)
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(6)
        }

    val myCollections: List<WallpaperCollection>
        get() = if (isDemo) {
            collectionItems
        } else {
            val id = session?.userId.orEmpty()
            collectionItems.filter { it.isLocal || (id.isNotBlank() && it.ownerId == id) }
        }

    val studioWallpapers: List<Wallpaper>
        get() = if (isDemo) {
            (wallpapers + adminWallpapers).distinctBy { it.id }
        } else {
            adminWallpapers
        }

    val categoryOptions: List<WallpaperCategory>
        get() = categories.ifEmpty {
            wallpapers.map { it.category }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .mapIndexed { index, name ->
                    WallpaperCategory(id = "", name = name, position = index)
                }
        }

    val categoryNames: List<String>
        get() = wallpapers.filterNot { it.isDeleted }
            .map { categoryNameFor(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()

    fun categoryNameFor(wallpaper: Wallpaper): String =
        categories.firstOrNull { it.id == wallpaper.categoryId }?.name
            ?: wallpaper.category

    fun canEdit(collection: WallpaperCollection): Boolean =
        isDemo || collection.isLocal ||
            (session != null && session.userId == collection.ownerId)

}
