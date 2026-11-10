package com.dannx.walls

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dannx.walls.data.AdminSession
import com.dannx.walls.data.AppSettings
import com.dannx.walls.data.AppearancePrefs
import com.dannx.walls.data.AppearanceStore
import com.dannx.walls.data.BulkTagLimit
import com.dannx.walls.data.CategoryAll
import com.dannx.walls.data.CategoryFeatured
import com.dannx.walls.data.CoilWallpaperColorResolver
import com.dannx.walls.data.Connectivity
import com.dannx.walls.data.DannXError
import com.dannx.walls.data.FeedCache
import com.dannx.walls.data.FeedCacheStore
import com.dannx.walls.data.InMemoryAppearanceStore
import com.dannx.walls.data.LOCAL_COLLECTION_OWNER
import com.dannx.walls.data.LOCAL_COLLECTION_PREFIX
import com.dannx.walls.data.LocalStore
import com.dannx.walls.data.Prefs
import com.dannx.walls.data.StudioEditException
import com.dannx.walls.data.TagsLimit
import com.dannx.walls.data.ThemeMode
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperActions
import com.dannx.walls.data.WallpaperCategory
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.WallpaperColor
import com.dannx.walls.data.WallpaperColorResolver
import com.dannx.walls.data.WallpaperFilter
import com.dannx.walls.data.WallpapersRepository
import com.dannx.walls.data.WallsApi
import com.dannx.walls.data.collectTags
import com.dannx.walls.data.filterWallpapers
import com.dannx.walls.data.isLocal
import com.dannx.walls.util.WallpaperFiles
import com.dannx.walls.util.WallpaperTarget
import com.dannx.walls.util.StringResolver
import com.dannx.walls.util.get
import com.dannx.walls.util.stringResolver
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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

/**
 * El estado se expone por StateFlow, asi que la instancia es estable: marcarla
 * evita que Compose recomponga la app entera cuando cambia un dato.
 */
@Stable
class MainViewModel(
    private val savedState: SavedStateHandle,
    private val prefs: LocalStore,
    private val api: WallpapersRepository,
    private val cache: FeedCacheStore,
    private val actions: WallpaperActions,
    private val isOnline: () -> Boolean = { true },
    private val appearance: AppearanceStore = InMemoryAppearanceStore(),
    private val strings: StringResolver = StringResolver { _, _ -> "" },
    private val now: () -> Long = System::currentTimeMillis,
    private val colorResolver: WallpaperColorResolver? = null,
) : ViewModel() {
    private var actionJob: Job? = null
    private var actionToken = 0
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null
    private var dailyJob: Job? = null
    private var filterJob: Job? = null
    private var colorJob: Job? = null
    /** null = se intentó y no se pudo resolver; evita reintentos infinitos. */
    private val colorCache = mutableMapOf<String, WallpaperColor?>()
    private var lastFeedRefreshAt: Long? = null
    private var detailJob: Job? = null
    private var detailToken = 0
    private var collectionDetailJob: Job? = null
    private var collectionDetailToken = 0
    private var studioJob: Job? = null
    private var uploadJob: Job? = null
    private val _state = MutableStateFlow(
        UiState(
            favorites = prefs.favorites(),
            session = prefs.session(),
            isDemo = !api.isConfigured,
            onboardingDone = prefs.onboardingDone(),
            downloadedIds = prefs.downloadedIds(),
            query = savedState.get<String>(KEY_QUERY) ?: "",
            recentSearches = prefs.recentSearches(),
            category = savedState.get<String>(KEY_CATEGORY),
            tag = savedState.get<String>(KEY_TAG),
            color = WallpaperColor.fromKey(savedState.get<String>(KEY_COLOR)),
            selectedUploadUri = savedState.get<String>(KEY_UPLOAD_URI),
            themeMode = appearance.themeMode(),
            liteMode = appearance.liteMode(),
            openEditorOnApply = appearance.openEditorOnApply(),
            appSettings = prefs.appSettings(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val _actionState = MutableStateFlow(ActionState())
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    init {
        restoreSession()
        if (isOnline()) {
            refresh()
        } else {
            viewModelScope.launch { loadFromCache() }
            loadDailyWallpaper()
        }
    }

    /**
     * Muestra lo guardado en disco cuando no hay red. Sin sesión nunca se
     * muestran colecciones privadas cacheadas.
     */
    private suspend fun loadFromCache() {
        val hasSession = _state.value.session != null
        val cachedWallpapers = cache.readWallpapers()
        val cachedCollections = mergedWithLocal(cache.readCollections())
            .filter { it.isLocal || hasSession || it.isPublic }
        _state.update {
            it.copy(
                feed = if (cachedWallpapers.isEmpty()) {
                    LoadState.Error(
                        message = strings.get(R.string.error_offline),
                        kind = ErrorKind.OFFLINE,
                    )
                } else {
                    LoadState.Content(cachedWallpapers)
                },
                collections = when {
                    cachedCollections.isNotEmpty() -> LoadState.Content(cachedCollections)
                    // Sin nada guardado no mostramos una pantalla vacía: el
                    // primer uso sin internet ofrece reintentar.
                    api.isConfigured -> LoadState.Error(
                        message = strings.get(R.string.error_offline),
                        kind = ErrorKind.OFFLINE,
                    )
                    else -> LoadState.Empty
                },
                wallpapers = cachedWallpapers,
                collectionItems = cachedCollections,
                offline = true,
                loadingMore = false,
                hasMore = false,
                loadMoreError = null,
            )
        }
        if (_state.value.color != null) classifyPendingColors()
    }

    /** Une las colecciones guardadas en el teléfono con las del servidor. */
    private fun mergedWithLocal(remote: List<WallpaperCollection>): List<WallpaperCollection> {
        val locals = prefs.localCollections()
            .filter { !api.isConfigured || it.isLocal }
        return (locals + remote).distinctBy { it.id }
    }

    private fun isLocalCollection(collectionId: String): Boolean =
        collectionId.startsWith(LOCAL_COLLECTION_PREFIX) ||
            _state.value.collectionItems.firstOrNull { it.id == collectionId }?.isLocal == true

    /** En instalaciones reales solo las colecciones locales van al teléfono. */
    private fun saveLocalCollections(items: List<WallpaperCollection>) {
        prefs.saveLocalCollections(
            if (api.isConfigured) items.filter { it.isLocal } else items,
        )
    }

    /**
     * Recarga el feed y las colecciones. Si ya hay una recarga en curso, la
     * cancela para no apilar estados ni requests.
     */
    fun refresh() {
        loadDailyWallpaper()
        loadFeed(reloadMeta = true, preserveFeed = true)
    }

    /**
     * Recarga solo la primera página cuando cambia un filtro. No vuelve a
     * pedir colecciones, categorías ni ajustes.
     */
    private fun reloadFeed() {
        if (!isOnline()) return
        loadFeed(reloadMeta = false, preserveFeed = false)
    }

    private fun loadFeed(
        reloadMeta: Boolean,
        preserveFeed: Boolean,
        silent: Boolean = false,
    ) {
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        refreshJob = viewModelScope.launch {
            val snapshot = _state.value
            val filter = snapshot.feedFilter(offset = 0)
            val hasContent = snapshot.wallpapers.isNotEmpty() ||
                (reloadMeta && snapshot.collectionItems.isNotEmpty())
            _state.update {
                if (hasContent) {
                    it.copy(
                        feed = if (it.wallpapers.isEmpty()) LoadState.Loading else it.feed,
                        collections = if (reloadMeta && it.collectionItems.isEmpty()) {
                            LoadState.Loading
                        } else {
                            it.collections
                        },
                        refreshing = if (silent) it.refreshing else true,
                        loadingMore = false,
                        loadMoreError = null,
                    )
                } else {
                    it.copy(
                        feed = LoadState.Loading,
                        collections = if (reloadMeta) LoadState.Loading else it.collections,
                        refreshing = false,
                        loadingMore = false,
                        loadMoreError = null,
                    )
                }
            }
            try {
                val page = api.loadWallpapersPage(filter)
                val current = _state.value
                val trashedIds = current.trashWallpapers.map { it.id }.toSet()
                val fresh = page.items.filterNot { it.isDeleted || it.id in trashedIds }
                val remoteCollections = if (reloadMeta) {
                    try {
                        api.loadCollections()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: DannXError.SessionExpired) {
                        throw error
                    } catch (error: Exception) {
                        _state.update { state -> state.copy(message = error.userMessage()) }
                        _state.value.collectionItems
                    }
                } else {
                    current.collectionItems
                }
                val collections = mergedWithLocal(remoteCollections)
                val categories = if (reloadMeta) {
                    loadCategoriesOrEmpty(current.categories)
                } else {
                    current.categories
                }
                val appSettings = if (reloadMeta) {
                    loadAppSettingsOrFallback(current.appSettings)
                } else {
                    current.appSettings
                }
                val items = if (preserveFeed) {
                    mergedFirstPage(fresh, current.wallpapers)
                } else {
                    fresh
                }
                if (!filter.hasActiveFilters) {
                    cacheFeed(items)
                }
                if (reloadMeta) {
                    cache.writeCollections(collections.filterNot { it.isLocal })
                }
                val hasMore = if (preserveFeed && current.wallpapers.size > fresh.size) {
                    current.hasMore
                } else {
                    page.hasMore
                }
                _state.update {
                    it.copy(
                        feed = if (items.isEmpty()) LoadState.Empty else LoadState.Content(items),
                        collections = if (reloadMeta) {
                            if (collections.isEmpty()) {
                                LoadState.Empty
                            } else {
                                LoadState.Content(collections)
                            }
                        } else {
                            it.collections
                        },
                        wallpapers = items,
                        categories = categories,
                        collectionItems = collections,
                        appSettings = appSettings,
                        isDemo = !api.isConfigured,
                        offline = false,
                        refreshing = false,
                        loadingMore = false,
                        hasMore = hasMore,
                        loadMoreError = null,
                    )
                }
                lastFeedRefreshAt = now()
                if (_state.value.color != null) classifyPendingColors()
                if (preserveFeed) {
                    pruneLocalCollections()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val kind = error.kind()
                val expired = error is DannXError.SessionExpired
                val current = _state.value
                val cachedWallpapers = cache.readWallpapers()
                val cachedCollections = if (reloadMeta) cache.readCollections() else emptyList()
                val currentCollections = if (expired) {
                    current.collectionItems.filter { it.isLocal || it.isPublic }
                } else {
                    current.collectionItems
                }
                val safeCachedCollections = if (expired || current.session == null) {
                    cachedCollections.filter { it.isPublic }
                } else {
                    cachedCollections
                }
                val fallbackWallpapers = current.wallpapers.ifEmpty { cachedWallpapers }
                val fallbackCollections = mergedWithLocal(
                    if (reloadMeta) {
                        currentCollections.ifEmpty { safeCachedCollections }
                    } else {
                        currentCollections
                    },
                )
                _state.update {
                    it.copy(
                        feed = if (fallbackWallpapers.isEmpty()) {
                            LoadState.Error(error.userMessage(), kind)
                        } else {
                            LoadState.Content(fallbackWallpapers)
                        },
                        collections = if (reloadMeta) {
                            if (fallbackCollections.isEmpty()) {
                                LoadState.Empty
                            } else {
                                LoadState.Content(fallbackCollections)
                            }
                        } else {
                            it.collections
                        },
                        wallpapers = fallbackWallpapers,
                        collectionItems = fallbackCollections,
                        session = if (expired) null else it.session,
                        adminWallpapers = if (expired) emptyList() else it.adminWallpapers,
                        trashWallpapers = if (expired) emptyList() else it.trashWallpapers,
                        message = if (expired) error.userMessage() else it.message,
                        offline = fallbackWallpapers.isNotEmpty() && kind == ErrorKind.OFFLINE,
                        refreshing = false,
                        loadingMore = false,
                        hasMore = fallbackWallpapers.isNotEmpty() && current.hasMore,
                        loadMoreError = null,
                    )
                }
                if (reloadMeta && expired) {
                    cache.writeCollections(
                        fallbackCollections.filter { it.isPublic && !it.isLocal },
                    )
                }
            }
        }
    }

    /**
     * Une la primera página nueva con lo que ya estaba cargado. Conserva el
     * orden de lo visible para que el scroll no salte al volver a la app.
     */
    private fun mergedFirstPage(
        fresh: List<Wallpaper>,
        current: List<Wallpaper>,
    ): List<Wallpaper> {
        if (current.isEmpty()) return fresh
        val freshById = fresh.associateBy { it.id }
        val currentIds = current.map { it.id }.toSet()
        val updated = current.map { old -> freshById[old.id] ?: old }
        val added = fresh.filterNot { it.id in currentIds }
        return added + updated
    }

    /**
     * Si un fondo local ya no existe en el servidor se quita sin avisar, para
     * que la colección y su contador sigan siendo confiables.
     */
    private fun pruneLocalCollections() {
        if (!api.isConfigured) return
        val current = _state.value
        val locals = current.collectionItems.filter { it.isLocal }
        if (locals.isEmpty()) return
        viewModelScope.launch {
            val known = (current.wallpapers + cache.readWallpapers()).map { it.id }.toSet()
            val missing = locals
                .flatMap { it.wallpaperIds }
                .filterNot { it in known }
                .distinct()
            if (missing.isEmpty()) return@launch
            val loaded = try {
                api.loadWallpapersByIds(missing)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Sin red no se borra nada: el próximo refresco lo reintenta.
                return@launch
            }
            if (loaded.isNotEmpty()) {
                cacheFeed(loaded)
            }
            val available = known + loaded.map { it.id }
            var changed = false
            val pruned = _state.value.collectionItems.map { collection ->
                if (!collection.isLocal) {
                    collection
                } else {
                    val ids = collection.wallpaperIds.filter { it in available }
                    if (ids.size == collection.wallpaperIds.size) {
                        collection
                    } else {
                        changed = true
                        collection.copy(wallpaperIds = ids)
                    }
                }
            }
            if (!changed) return@launch
            saveLocalCollections(pruned)
            _state.update {
                it.copy(
                    collectionItems = pruned,
                    collections = if (pruned.isEmpty()) {
                        LoadState.Empty
                    } else {
                        LoadState.Content(pruned)
                    },
                )
            }
        }
    }

    /**
     * Pide la página siguiente del feed actual. Si el usuario cambió de
     * filtro mientras viajaba la respuesta, el resultado se descarta.
     */
    fun loadMore() {
        val snapshot = _state.value
        // El color se resuelve sobre lo que ya está cargado; pedir más páginas
        // solo agregaría fondos sin clasificar.
        if (snapshot.color != null) return
        if (!snapshot.hasMore || snapshot.loadingMore || snapshot.refreshing) return
        if (refreshJob?.isActive == true) return
        val filter = snapshot.feedFilter(offset = snapshot.wallpapers.size)
        if (filter.offset <= 0) return
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, loadMoreError = null) }
            try {
                val page = api.loadWallpapersPage(filter)
                val current = _state.value
                if (current.query != filter.query ||
                    current.category != filter.category ||
                    current.tag != filter.tag
                ) {
                    _state.update { it.copy(loadingMore = false) }
                    return@launch
                }
                val trashedIds = current.trashWallpapers.map { it.id }.toSet()
                val fresh = page.items.filterNot { it.isDeleted || it.id in trashedIds }
                val merged = (current.wallpapers + fresh).distinctBy { it.id }
                _state.update {
                    it.copy(
                        feed = LoadState.Content(merged),
                        wallpapers = merged,
                        loadingMore = false,
                        hasMore = page.hasMore,
                        loadMoreError = null,
                    )
                }
                if (!filter.hasActiveFilters) {
                    cacheFeed(merged)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loadingMore = false,
                        loadMoreError = error.userMessage(),
                    )
                }
            }
        }
    }

    /**
     * Resuelve un fondo por id para la pantalla de detalle. Nunca navega ni
     * cierra la pantalla: primero busca en memoria, después en la caché y por
     * último en el servidor.
     */
    fun loadWallpaperDetail(id: String, force: Boolean = false) {
        val wallpaperId = id.trim()
        if (wallpaperId.isBlank()) {
            _state.update {
                it.copy(
                    detailWallpaperId = id,
                    detailWallpaper = LoadState.Error(
                        strings.get(R.string.detail_wallpaper_missing),
                        ErrorKind.UNEXPECTED,
                    ),
                )
            }
            return
        }
        val current = _state.value
        if (!force) {
            if (current.detailWallpaperId == wallpaperId &&
                current.detailWallpaper is LoadState.Content
            ) {
                return
            }
            if (current.detailWallpaperId == wallpaperId &&
                current.detailWallpaper is LoadState.Loading &&
                detailJob?.isActive == true
            ) {
                return
            }
            current.wallpaperById(wallpaperId)?.let { wallpaper ->
                detailToken += 1
                detailJob?.cancel()
                _state.update {
                    it.copy(
                        detailWallpaperId = wallpaperId,
                        detailWallpaper = LoadState.Content(wallpaper),
                    )
                }
                return
            }
        }

        val token = ++detailToken
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _state.update {
                if (detailToken != token) {
                    it
                } else {
                    it.copy(
                        detailWallpaperId = wallpaperId,
                        detailWallpaper = LoadState.Loading,
                    )
                }
            }
            try {
                val cached = cache.readWallpapers().firstOrNull { it.id == wallpaperId }
                if (detailToken != token) return@launch
                if (cached != null) {
                    _state.update {
                        it.copy(
                            detailWallpaperId = wallpaperId,
                            detailWallpaper = LoadState.Content(cached),
                        )
                    }
                    return@launch
                }
                val remote = api.loadWallpaper(wallpaperId)
                if (detailToken != token) return@launch
                if (remote == null) {
                    _state.update {
                        it.copy(
                            detailWallpaperId = wallpaperId,
                            detailWallpaper = LoadState.Error(
                                strings.get(R.string.detail_wallpaper_missing),
                                ErrorKind.UNEXPECTED,
                            ),
                        )
                    }
                    return@launch
                }
                cacheFeed(listOf(remote))
                if (detailToken != token) return@launch
                _state.update {
                    it.copy(
                        detailWallpaperId = wallpaperId,
                        detailWallpaper = LoadState.Content(remote),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (detailToken == token) {
                    _state.update {
                        it.copy(
                            detailWallpaperId = wallpaperId,
                            detailWallpaper = LoadState.Error(error.userMessage(), error.kind()),
                        )
                    }
                }
            }
        }
    }

    fun clearWallpaperDetail() {
        detailToken += 1
        detailJob?.cancel()
        _state.update { it.copy(detailWallpaperId = null, detailWallpaper = null) }
    }

    /**
     * Resuelve una colección y todos sus fondos. Si la colección o alguna de
     * sus imágenes no está en memoria, la busca en caché y luego en el
     * servidor, sin cerrar la pantalla si algo falla.
     */
    fun loadCollectionDetail(id: String, force: Boolean = false) {
        val collectionId = id.trim()
        if (collectionId.isBlank()) {
            _state.update {
                it.copy(
                    detailCollectionId = id,
                    detailCollection = LoadState.Error(
                        strings.get(R.string.detail_collection_missing),
                        ErrorKind.UNEXPECTED,
                    ),
                )
            }
            return
        }
        val current = _state.value
        if (!force && current.detailCollectionId == collectionId &&
            current.detailCollection is LoadState.Content
        ) {
            return
        }
        current.collectionItems.firstOrNull { it.id == collectionId }?.let { collection ->
            if (!force && current.collectionDetailIsComplete(collection)) {
                collectionDetailToken += 1
                collectionDetailJob?.cancel()
                _state.update {
                    it.copy(
                        detailCollectionId = collectionId,
                        detailCollection = LoadState.Content(
                            CollectionDetailData(collection, it.wallpapersFor(collection)),
                        ),
                    )
                }
                return
            }
        }
        if (!force && current.detailCollectionId == collectionId &&
            current.detailCollection is LoadState.Loading &&
            collectionDetailJob?.isActive == true
        ) {
            return
        }

        val token = ++collectionDetailToken
        collectionDetailJob?.cancel()
        collectionDetailJob = viewModelScope.launch {
            _state.update {
                if (collectionDetailToken != token) {
                    it
                } else {
                    it.copy(
                        detailCollectionId = collectionId,
                        detailCollection = LoadState.Loading,
                    )
                }
            }
            try {
                val hasSession = _state.value.session != null
                val collection = _state.value.collectionItems
                    .firstOrNull { it.id == collectionId }
                    ?: prefs.localCollections().firstOrNull { it.id == collectionId }
                    ?: cache.readCollections().firstOrNull {
                        it.id == collectionId && (it.isLocal || it.isPublic || hasSession)
                    }
                    ?: api.loadCollection(collectionId)
                    ?: throw CollectionNotFound()
                if (collectionDetailToken != token) return@launch
                if (_state.value.collectionItems.none { it.id == collection.id }) {
                    _state.update {
                        it.copy(collectionItems = listOf(collection) + it.collectionItems)
                    }
                }
                val known = _state.value.wallpapersFor(collection).associateBy { it.id }
                val cached = if (collection.wallpaperIds.any { it !in known }) {
                    val fromCache = cache.readWallpapers()
                    if (collectionDetailToken != token) return@launch
                    fromCache.associateBy { it.id }
                } else {
                    emptyMap()
                }
                val missing = collection.wallpaperIds.filter { it !in known && it !in cached }
                val fetched = if (missing.isNotEmpty()) {
                    val loaded = api.loadWallpapersByIds(missing)
                    if (collectionDetailToken != token) return@launch
                    loaded.also { items -> if (items.isNotEmpty()) cacheFeed(items) }
                } else {
                    emptyList()
                }
                if (collectionDetailToken != token) return@launch
                val byId = known + cached + fetched.associateBy { it.id }
                val items = collection.wallpaperIds.mapNotNull { byId[it] }
                _state.update {
                    it.copy(
                        detailCollectionId = collectionId,
                        detailCollection = LoadState.Content(
                            CollectionDetailData(collection, items),
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: CollectionNotFound) {
                if (collectionDetailToken == token) {
                    _state.update {
                        it.copy(
                            detailCollectionId = collectionId,
                            detailCollection = LoadState.Error(
                                strings.get(R.string.detail_collection_missing),
                                ErrorKind.UNEXPECTED,
                            ),
                        )
                    }
                }
            } catch (error: Exception) {
                if (collectionDetailToken == token) {
                    _state.update {
                        it.copy(
                            detailCollectionId = collectionId,
                            detailCollection = LoadState.Error(error.userMessage(), error.kind()),
                        )
                    }
                }
            }
        }
    }

    fun clearCollectionDetail() {
        collectionDetailToken += 1
        collectionDetailJob?.cancel()
        _state.update { it.copy(detailCollectionId = null, detailCollection = null) }
    }

    private class CollectionNotFound : Exception()

    private fun UiState.wallpaperById(id: String): Wallpaper? =
        (wallpapers + adminWallpapers).firstOrNull { it.id == id }
            ?: (detailWallpaper as? LoadState.Content<Wallpaper>)?.data?.takeIf { it.id == id }

    private fun UiState.wallpapersFor(collection: WallpaperCollection): List<Wallpaper> {
        val byId = (wallpapers + adminWallpapers).associateBy { it.id }
        return collection.wallpaperIds.mapNotNull { byId[it] }
    }

    private fun UiState.collectionDetailIsComplete(collection: WallpaperCollection): Boolean {
        val known = (wallpapers + adminWallpapers).map { it.id }.toSet()
        return collection.wallpaperIds.all { it in known }
    }

    private fun UiState.collectionDetailUpdated(
        collectionId: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ): UiState {
        val detail = detailCollection as? LoadState.Content<CollectionDetailData> ?: return this
        if (detail.data.collection.id != collectionId) return this
        return copy(
            detailCollection = LoadState.Content(
                detail.data.copy(
                    collection = detail.data.collection.copy(
                        name = name,
                        description = description,
                        isPublic = isPublic,
                    ),
                ),
            ),
        )
    }

    private fun UiState.collectionDetailWithout(
        collectionId: String,
        wallpaperId: String,
    ): UiState {
        val detail = detailCollection as? LoadState.Content<CollectionDetailData> ?: return this
        if (detail.data.collection.id != collectionId) return this
        val collection = detail.data.collection.copy(
            wallpaperIds = detail.data.collection.wallpaperIds.filterNot {
                it == wallpaperId
            },
        )
        return copy(
            detailCollection = LoadState.Content(
                detail.data.copy(
                    collection = collection,
                    wallpapers = detail.data.wallpapers.filterNot { it.id == wallpaperId },
                ),
            ),
        )
    }

    private fun UiState.collectionDetailWithWallpapers(
        collectionId: String,
        wallpaperIds: List<String>,
    ): UiState {
        val detail = detailCollection as? LoadState.Content<CollectionDetailData> ?: return this
        if (detail.data.collection.id != collectionId) return this
        val selected = wallpaperIds.distinct()
        val byId = detail.data.wallpapers.associateBy { it.id }
        return copy(
            detailCollection = LoadState.Content(
                detail.data.copy(
                    collection = detail.data.collection.copy(wallpaperIds = selected),
                    wallpapers = selected.mapNotNull { byId[it] },
                ),
            ),
        )
    }

    private fun UiState.collectionDetailDeleted(collectionId: String): UiState {
        val detail = detailCollection as? LoadState.Content<CollectionDetailData> ?: return this
        if (detail.data.collection.id != collectionId) return this
        return copy(
            detailCollectionId = null,
            detailCollection = null,
        )
    }

    /**
     * Conserva los fondos viejos que no vinieron en la página nueva para que
     * el modo offline siga teniendo contenido, sin pasar el límite de la caché.
     */
    private suspend fun cacheFeed(items: List<Wallpaper>) {
        val cached = cache.readWallpapers()
        cache.writeWallpapers(
            (items + cached).distinctBy { it.id }.take(FeedCache.MAX_WALLPAPERS),
        )
    }

    private fun UiState.feedFilter(offset: Int): WallpaperFilter =
        WallpaperFilter(
            query = query,
            category = category,
            tag = tag,
            offset = offset,
        )

    /**
     * Refresca al volver de segundo plano sin perder las páginas cargadas ni
     * mover el scroll. Si la persona volvió hace menos de cinco minutos no se
     * consulta de nuevo.
     */
    fun refreshIfOnline() {
        if (!isOnline()) return
        if (refreshJob?.isActive == true) return
        val last = lastFeedRefreshAt
        if (last != null && now() - last < REFRESH_WINDOW_MS) return
        loadFeed(reloadMeta = false, preserveFeed = true, silent = true)
    }

    fun setQuery(value: String) {
        savedState[KEY_QUERY] = value
        if (value == _state.value.query) return
        _state.update { it.copy(query = value) }
        filterJob?.cancel()
        // Borrar todo vuelve al feed principal al instante, sin esperar.
        if (value.isBlank()) {
            filterJob = null
            reloadFeed()
            return
        }
        filterJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (_state.value.query == value) reloadFeed()
        }
    }

    fun rememberCurrentSearch() {
        val query = _state.value.query
        if (query.isBlank()) return
        prefs.rememberSearch(query)
        _state.update { it.copy(recentSearches = prefs.recentSearches()) }
    }

    fun clearRecentSearches() {
        prefs.clearRecentSearches()
        _state.update { it.copy(recentSearches = emptyList()) }
    }

    fun setCategory(value: String?) {
        savedState[KEY_CATEGORY] = value
        _state.update { it.copy(category = value) }
        filterJob?.cancel()
        reloadFeed()
    }

    fun setTag(value: String?) {
        savedState[KEY_TAG] = value
        _state.update { it.copy(tag = value) }
        filterJob?.cancel()
        reloadFeed()
    }

    /**
     * Activa o quita el filtro por color. La clasificación es perezosa: recién
     * al elegir un color se analizan las miniaturas que falten.
     */
    fun setColor(value: WallpaperColor?) {
        savedState[KEY_COLOR] = value?.key
        if (value == _state.value.color) return
        _state.update { it.copy(color = value) }
        if (value == null) {
            colorJob?.cancel()
            colorJob = null
            _state.update { it.copy(classifyingColors = false) }
        } else {
            classifyPendingColors()
        }
    }

    private fun classifyPendingColors() {
        val resolver = colorResolver ?: return
        if (colorJob?.isActive == true) return
        val pending = _state.value.wallpapers.filter { it.id !in colorCache }
        if (pending.isEmpty()) return
        colorJob = viewModelScope.launch {
            _state.update { it.copy(classifyingColors = true) }
            try {
                pending.chunked(COLOR_BATCH_SIZE).forEach { chunk ->
                    val resolved = coroutineScope {
                        chunk.map { wallpaper ->
                            async {
                                val color = try {
                                    resolver.resolve(wallpaper)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    null
                                }
                                wallpaper.id to color
                            }
                        }.awaitAll()
                    }
                    var changed = false
                    resolved.forEach { (id, color) ->
                        colorCache[id] = color
                        if (color != null) changed = true
                    }
                    if (changed) {
                        _state.update {
                            it.copy(wallpaperColors = resolvedColors())
                        }
                    }
                }
            } finally {
                _state.update { it.copy(classifyingColors = false) }
            }
        }
    }

    private fun resolvedColors(): Map<String, WallpaperColor> = buildMap {
        colorCache.forEach { (id, color) ->
            if (color != null) put(id, color)
        }
    }

    fun setSelectedUploadUri(uri: String?) {
        savedState[KEY_UPLOAD_URI] = uri
        _state.update { it.copy(selectedUploadUri = uri) }
    }

    fun toggleFavorite(id: String) {
        _state.update { it.copy(favorites = prefs.toggleFavorite(id)) }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(message = strings.get(R.string.msg_enter_credentials)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true) }
            try {
                val session = api.signIn(email, password)
                val collections = loadCollectionsOrEmpty(_state.value.collectionItems)
                _state.update {
                    it.copy(
                        authBusy = false,
                        session = session,
                        collectionItems = collections,
                        collections = if (collections.isEmpty()) {
                            LoadState.Empty
                        } else {
                            LoadState.Content(collections)
                        },
                        message = strings.get(R.string.msg_signed_in, session.email),
                        messageAction = null,
                    )
                }
                // StudioScreen observa el cambio de sesión y dispara la carga
                // del Estudio una sola vez.
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
            }
        }
    }

    /**
     * Señal en la memoria del proceso de que la pantalla señuelo se abrió en
     * esta corrida de la app. Al girar el teléfono el ViewModel sobrevive y la
     * marca sigue en pie; si Android recreó el proceso, arranca en false y la
     * navegación vuelve al Inicio en lugar de restaurar pantallas privadas.
     */
    private var decoyOpenedInThisProcess = false

    val decoyVisited: Boolean
        get() = decoyOpenedInThisProcess

    fun rememberDecoyOpened() {
        decoyOpenedInThisProcess = true
    }

    fun signOut() {
        studioJob?.cancel()
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        filterJob?.cancel()
        detailJob?.cancel()
        collectionDetailJob?.cancel()
        uploadJob?.cancel()
        prefs.clearSession()
        savedState[KEY_UPLOAD_URI] = null
        val fallback = mergedWithLocal(
            _state.value.collectionItems.filter { it.isLocal || it.isPublic },
        )
        _state.update {
            it.copy(
                session = null,
                adminWallpapers = emptyList(),
                collectionItems = fallback,
                collections = if (fallback.isEmpty()) {
                    LoadState.Empty
                } else {
                    LoadState.Content(fallback)
                },
                selectedUploadUri = null,
                uploading = false,
                refreshing = false,
                authBusy = false,
                studioBusy = false,
                collectionBusy = false,
                message = strings.get(R.string.msg_signed_out),
                messageAction = null,
                downloadPrompt = null,
                detailWallpaperId = null,
                detailWallpaper = null,
                detailCollectionId = null,
                detailCollection = null,
            )
        }
        viewModelScope.launch {
            // La caché puede contener colecciones privadas de la sesión que se
            // acaba de cerrar: se limpia antes de volver a cargar las públicas.
            cache.writeCollections(emptyList())
            val collections = loadCollectionsOrEmpty(fallback)
            cache.writeCollections(collections.filter { it.isPublic && !it.isLocal })
            _state.update {
                it.copy(
                    collectionItems = collections,
                    collections = if (collections.isEmpty()) {
                        LoadState.Empty
                    } else {
                        LoadState.Content(collections)
                    },
                )
            }
        }
    }

    fun uploadWallpaper(
        uri: Uri,
        title: String,
        category: String,
        categoryId: String?,
        tags: List<String>,
        collectionId: String?,
    ) {
        uploadJob = viewModelScope.launch {
            _state.update { it.copy(uploading = true) }
            try {
                val outcome = api.uploadWallpaper(
                    uri = uri,
                    title = title,
                    category = category,
                    categoryId = categoryId,
                    tags = tags,
                    collectionId = collectionId,
                )
                val created = outcome.wallpaper
                val collections = loadCollectionsOrEmpty(_state.value.collectionItems)
                _state.update {
                    val updatedWallpapers = listOf(created) +
                        it.wallpapers.filterNot { w -> w.id == created.id }
                    it.copy(
                        uploading = false,
                        feed = LoadState.Content(updatedWallpapers),
                        wallpapers = updatedWallpapers,
                        adminWallpapers = listOf(created) +
                            it.adminWallpapers.filterNot { w -> w.id == created.id },
                        collectionItems = collections,
                        collections = if (collections.isEmpty()) {
                            LoadState.Empty
                        } else {
                            LoadState.Content(collections)
                        },
                        uploadSuccessCount = it.uploadSuccessCount + 1,
                        message = if (outcome.warning != null) {
                            strings.get(R.string.msg_uploaded_without_collection)
                        } else {
                            strings.get(R.string.msg_wallpaper_published)
                        },
                        messageAction = null,
                    )
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(uploading = false) }
                throw error
            } catch (error: Exception) {
                Log.w("DannX", "No se pudo subir el wallpaper", error)
                _state.update { it.afterError(error, error.uploadMessage()) }
            }
        }
    }

    /**
     * Carga la lista completa del Estudio, incluyendo fondos archivados.
     */
    fun loadStudio() {
        val current = _state.value
        if (current.isDemo) {
            _state.update { it.copy(adminWallpapers = it.wallpapers) }
            return
        }
        if (current.session == null) return
        studioJob?.cancel()
        studioJob = viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                val walls = api.loadAdminWallpapers()
                val categories = loadCategoriesOrEmpty(_state.value.categories)
                _state.update {
                    it.copy(
                        adminWallpapers = walls.filterNot { w -> w.isDeleted },
                        trashWallpapers = walls.filter { w -> w.isDeleted },
                        categories = categories,
                        studioBusy = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
            }
        }
    }

    fun saveAppSettings(tagline: String, bannerText: String, bannerEnabled: Boolean) {
        val clean = AppSettings(
            tagline = tagline,
            bannerText = bannerText,
            bannerEnabled = bannerEnabled,
        ).normalized()
        if (_state.value.isDemo) {
            prefs.saveAppSettings(clean)
            _state.update {
                it.copy(
                    appSettings = clean,
                    message = strings.get(R.string.msg_app_settings_saved),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                val saved = api.saveAppSettings(clean)
                prefs.saveAppSettings(saved)
                _state.update {
                    it.copy(
                        studioBusy = false,
                        appSettings = saved,
                        message = strings.get(R.string.msg_app_settings_saved),
                    )
                }
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
            }
        }
    }

    fun createCategory(name: String) {
        val clean = name.trim().take(40)
        if (clean.isBlank()) {
            _state.update { it.copy(message = strings.get(R.string.msg_category_name_required)) }
            return
        }
        if (isReservedCategoryName(clean)) {
            _state.update { it.copy(message = strings.get(R.string.msg_name_reserved)) }
            return
        }
        val position = (_state.value.categories.maxOfOrNull { it.position } ?: -1) + 1
        if (_state.value.isDemo) {
            val created = WallpaperCategory(
                id = "local-category-${UUID.randomUUID()}",
                name = clean,
                position = position,
            )
            _state.update {
                it.copy(
                    categories = it.categories + created,
                    message = strings.get(R.string.msg_category_created, clean),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                val created = api.createCategory(clean, position)
                _state.update {
                    it.copy(
                        studioBusy = false,
                        categories = (it.categories + created)
                            .distinctBy { c -> c.id }
                            .sortedWith(compareBy({ c -> c.position }, { c -> c.name })),
                        message = strings.get(R.string.msg_category_created, created.name),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.afterError(error)
                }
            }
        }
    }

    fun updateCategory(categoryId: String, name: String) {
        val category = _state.value.categories.firstOrNull { it.id == categoryId } ?: return
        val clean = name.trim().take(40)
        if (clean.isBlank()) {
            _state.update { it.copy(message = strings.get(R.string.msg_category_name_required)) }
            return
        }
        if (isReservedCategoryName(clean)) {
            _state.update { it.copy(message = strings.get(R.string.msg_name_reserved)) }
            return
        }
        if (_state.value.isDemo) {
            _state.update {
                it.copy(
                    categories = it.categories.map { c ->
                        if (c.id == categoryId) c.copy(name = clean) else c
                    },
                    wallpapers = it.wallpapers.map { w ->
                        if (w.categoryId == categoryId) w.copy(category = clean) else w
                    },
                    adminWallpapers = it.adminWallpapers.map { w ->
                        if (w.categoryId == categoryId) w.copy(category = clean) else w
                    },
                    message = strings.get(R.string.msg_category_updated),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                val updated = api.updateCategory(categoryId, clean, category.position)
                _state.update {
                    it.copy(
                        studioBusy = false,
                        categories = it.categories.map { c ->
                            if (c.id == categoryId) updated else c
                        },
                        wallpapers = it.wallpapers.map { w ->
                            if (w.categoryId == categoryId) w.copy(category = updated.name) else w
                        },
                        adminWallpapers = it.adminWallpapers.map { w ->
                            if (w.categoryId == categoryId) w.copy(category = updated.name) else w
                        },
                        message = strings.get(R.string.msg_category_renamed),
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun moveCategory(categoryId: String, direction: Int) {
        if (direction == 0) return
        val ordered = _state.value.categories.sortedBy { it.position }
        val index = ordered.indexOfFirst { it.id == categoryId }
        if (index < 0) return
        val targetIndex = index + direction
        if (targetIndex !in ordered.indices) return

        val reordered = ordered.toMutableList().apply {
            val item = removeAt(index)
            add(targetIndex, item)
        }.mapIndexed { position, category -> category.copy(position = position) }
        val oldPositions = ordered.associate { it.id to it.position }
        val changed = reordered.filter { oldPositions[it.id] != it.position }

        if (_state.value.isDemo) {
            _state.update {
                it.copy(
                    categories = reordered,
                    message = strings.get(R.string.msg_order_updated),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                changed.forEach { category ->
                    api.updateCategory(category.id, category.name, category.position)
                }
                _state.update {
                    it.copy(
                        studioBusy = false,
                        categories = reordered,
                        message = strings.get(R.string.msg_order_updated),
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun deleteCategory(categoryId: String, reassignToId: String? = null) {
        val category = _state.value.categories.firstOrNull { it.id == categoryId } ?: return
        val affected = _state.value.studioWallpapers.filter { it.categoryId == categoryId }
        val target = _state.value.categories.firstOrNull { it.id == reassignToId }
        if (affected.isNotEmpty() && target == null) {
            _state.update {
                it.copy(
                    message = strings.get(
                        R.string.msg_category_delete_blocked,
                        category.name,
                        affected.size,
                    ),
                )
            }
            return
        }
        if (_state.value.isDemo) {
            _state.update {
                it.copy(
                    categories = it.categories.filterNot { c -> c.id == categoryId },
                    wallpapers = it.wallpapers.map { w ->
                        if (w.categoryId == categoryId && target != null) {
                            w.copy(categoryId = target.id, category = target.name)
                        } else {
                            w
                        }
                    },
                    adminWallpapers = it.adminWallpapers.map { w ->
                        if (w.categoryId == categoryId && target != null) {
                            w.copy(categoryId = target.id, category = target.name)
                        } else {
                            w
                        }
                    },
                    message = strings.get(R.string.msg_category_deleted, category.name),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.deleteCategory(
                    id = categoryId,
                    targetId = target?.id,
                    targetName = target?.name,
                )
                _state.update {
                    it.copy(
                        studioBusy = false,
                        categories = it.categories.filterNot { c -> c.id == categoryId },
                        wallpapers = it.wallpapers.map { w ->
                            if (w.categoryId == categoryId && target != null) {
                                w.copy(categoryId = target.id, category = target.name)
                            } else {
                                w
                            }
                        },
                        adminWallpapers = it.adminWallpapers.map { w ->
                            if (w.categoryId == categoryId && target != null) {
                                w.copy(categoryId = target.id, category = target.name)
                            } else {
                                w
                            }
                        },
                        message = strings.get(R.string.msg_category_deleted, category.name),
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun updateWallpaper(
        wallpaperId: String,
        title: String,
        category: String,
        categoryId: String?,
        author: String,
        tags: List<String>,
        featured: Boolean,
    ) {
        viewModelScope.launch {
            try {
                updateWallpaperNow(
                    wallpaperId = wallpaperId,
                    title = title,
                    categoryInput = category,
                    categoryId = categoryId,
                    author = author,
                    tags = tags,
                    featured = featured,
                )
                _state.update { it.copy(message = strings.get(R.string.msg_wallpaper_updated)) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.afterError(error) }
            }
        }
    }

    /**
     * Guarda una edición de fondo y devuelve el wallpaper actualizado. La usa
     * la hoja de edición con la pantalla de espera y Reintentar; al confirmar
     * el servidor, esparce los nuevos datos por todas las pantallas y la caché
     * para que no quede ningún dato viejo a la vista.
     */
    suspend fun updateWallpaperNow(
        wallpaperId: String,
        title: String,
        categoryInput: String,
        categoryId: String?,
        author: String,
        tags: List<String>,
        featured: Boolean,
    ): Wallpaper {
        val cleanTags = tags.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val cleanAuthor = author.trim().ifBlank { "DannX" }.take(60)
        val cleanTitle = title.trim().take(120)
        val current = _state.value.studioWallpapers.firstOrNull { it.id == wallpaperId }
            ?: throw StudioEditException(strings.get(R.string.error_unexpected))
        val resolved = resolveCategoryForEdit(current, categoryInput, categoryId)
        val nextFeaturedPosition = when {
            featured && !current.featured ->
                (_state.value.studioWallpapers.filter { it.featured }
                    .maxOfOrNull { it.featuredPosition } ?: -1) + 1
            else -> current.featuredPosition
        }
        if (_state.value.isDemo) {
            val updated = current.copy(
                title = cleanTitle,
                category = resolved.first,
                categoryId = resolved.second,
                author = cleanAuthor,
                tags = cleanTags,
                featured = featured,
                featuredPosition = nextFeaturedPosition,
            )
            applyWallpaperUpdates(listOf(updated))
            return updated
        }
        val updated = api.updateWallpaper(
            wallpaperId = wallpaperId,
            title = cleanTitle,
            category = resolved.first,
            categoryId = resolved.second,
            author = cleanAuthor,
            tags = cleanTags,
            featured = featured,
            featuredPosition = nextFeaturedPosition,
        )
        applyWallpaperUpdates(listOf(updated))
        return updated
    }

    /**
     * Cierra la pareja (nombre, id) de la categoría elegida en el editor:
     * texto que coincide con una de la lista usa su id; texto igual al nombre
     * actual conserva el id previo; texto nuevo crea la categoría antes de
     * tocar el fondo. El texto vacío conserva la categoría que ya tenía.
     */
    private suspend fun resolveCategoryForEdit(
        current: Wallpaper,
        categoryInput: String,
        categoryId: String?,
    ): Pair<String, String?> {
        val typed = categoryInput.trim().take(40)
        if (typed.isBlank()) {
            return current.category to current.categoryId
        }
        val existing = _state.value.categories.firstOrNull {
            it.name.equals(typed, ignoreCase = true)
        }
        if (existing != null) return existing.name to existing.id
        if (categoryId != null && current.category.equals(typed, ignoreCase = true)) {
            return typed to categoryId
        }
        val created = createCategoryNow(typed)
        return created.name to created.id
    }

    /**
     * Crea una categoría y la devuelve sin avisar en pantalla: la llama el
     * editor cuando se guarda con una categoría que todavía no existe. Si
     * falla, la edición entera queda con su error y sin tocar el fondo.
     */
    suspend fun createCategoryNow(name: String): WallpaperCategory {
        val clean = name.trim().take(40)
        if (clean.isBlank()) {
            throw StudioEditException(strings.get(R.string.msg_category_name_required))
        }
        if (isReservedCategoryName(clean)) {
            throw StudioEditException(strings.get(R.string.msg_name_reserved))
        }
        _state.value.categories.firstOrNull { it.name.equals(clean, ignoreCase = true) }
            ?.let { return it }
        val position = (_state.value.categories.maxOfOrNull { it.position } ?: -1) + 1
        if (_state.value.isDemo) {
            val created = WallpaperCategory(
                id = "local-category-" + UUID.randomUUID().toString(),
                name = clean,
                position = position,
            )
            _state.update { it.copy(categories = it.categories + created) }
            return created
        }
        try {
            val created = api.createCategory(clean, position)
            _state.update {
                it.copy(
                    categories = (it.categories + created)
                        .distinctBy { c -> c.id }
                        .sortedWith(compareBy({ c -> c.position }, { c -> c.name })),
                )
            }
            return created
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw StudioEditException(error.userMessage())
        }
    }

    /**
     * Esparce wallpapers editados por todo el estado: listas del Estudio y del
     * feed, detalle de fondo, detalle de colección y caché del feed. Es el
     * punto único para que una edición se vea al instante en cada pantalla.
     */
    private suspend fun applyWallpaperUpdates(updates: List<Wallpaper>) {
        if (updates.isEmpty()) return
        val byId = updates.associateBy { it.id }
        _state.update { state ->
            val detailWallpaper = (state.detailWallpaper as? LoadState.Content<Wallpaper>)
                ?.takeIf { it.data.id in byId }
                ?.let { content -> LoadState.Content(byId.getValue(content.data.id)) }
            val detailCollection = (state.detailCollection as? LoadState.Content<CollectionDetailData>)
                ?.takeIf { content -> content.data.wallpapers.any { it.id in byId } }
                ?.let { content ->
                    LoadState.Content(
                        content.data.copy(
                            wallpapers = content.data.wallpapers.map { byId[it.id] ?: it },
                        ),
                    )
                }
            state.copy(
                wallpapers = state.wallpapers.map { byId[it.id] ?: it },
                adminWallpapers = state.adminWallpapers.map { byId[it.id] ?: it },
                detailWallpaper = detailWallpaper ?: state.detailWallpaper,
                detailCollection = detailCollection ?: state.detailCollection,
            )
        }
        runCatching {
            val cached = cache.readWallpapers()
            if (cached.any { it.id in byId }) {
                cache.writeWallpapers(cached.map { byId[it.id] ?: it })
            }
        }
    }

    /** Cierra el resumen de una corrida de edición de tags en bloque. */
    fun clearBulkResult() {
        _state.update { it.copy(bulkResult = null) }
    }

    /** Reemplaza el tag en las listas del fondo sin repetirlo. */
    private fun Wallpaper.withBulkTag(tag: String, add: Boolean): Wallpaper {
        val lowered = tag.lowercase(Locale.ROOT)
        val without = tags.filterNot { it.lowercase(Locale.ROOT) == lowered }
        return copy(tags = if (add) without + tag else without)
    }

    /**
     * Editar tags de varios fondos a la vez: agrega el tag al final de cada
     * uno (sin repetir) o lo quita. Hace una petición por fondo con a lo sumo
     * tres en paralelo y muestra el progreso en pantalla; ante un fallo, ese
     * fondo queda como está y queda en la lista para reintentarlo.
     */
    fun bulkEditTags(ids: List<String>, tag: String, add: Boolean) {
        val targets = ids.asSequence()
            .distinct()
            .take(BulkTagLimit)
            .mapNotNull { id -> _state.value.studioWallpapers.firstOrNull { it.id == id } }
            .toList()
        val cleanTag = tag.trim().trimStart('#').trim().take(24)
        if (targets.isEmpty() || cleanTag.isBlank()) return
        _state.update { it.copy(bulkResult = null, bulkProgress = null) }
        viewModelScope.launch {
            if (_state.value.isDemo) {
                val updated = targets.map { it.withBulkTag(cleanTag, add) }
                applyWallpaperUpdates(updated)
                _state.update {
                    it.copy(
                        bulkResult = BulkTagResult(
                            tag = cleanTag,
                            add = add,
                            saved = updated.size,
                            failed = emptyList(),
                        ),
                    )
                }
                return@launch
            }
            var done = 0
            _state.update { it.copy(bulkProgress = 0 to targets.size) }
            val saved = mutableListOf<Wallpaper>()
            val failed = mutableListOf<String>()
            val semaphore = Semaphore(3)
            val jobs = targets.map { wallpaper ->
                launch {
                    semaphore.withPermit {
                        val newTags = when {
                            add && wallpaper.tags.size >= TagsLimit -> null
                            else -> wallpaper.withBulkTag(cleanTag, add).tags
                        }
                        try {
                            val updated = when (newTags) {
                                null -> null
                                else -> api.updateWallpaper(
                                    wallpaperId = wallpaper.id,
                                    title = wallpaper.title,
                                    category = wallpaper.category,
                                    categoryId = wallpaper.categoryId,
                                    author = wallpaper.author,
                                    tags = newTags,
                                    featured = wallpaper.featured,
                                    featuredPosition = wallpaper.featuredPosition,
                                )
                            }
                            if (updated != null) saved += updated
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failed += wallpaper.id
                        } finally {
                            done += 1
                            _state.update {
                                it.copy(bulkProgress = done to targets.size)
                            }
                        }
                    }
                }
            }
            jobs.joinAll()
            applyWallpaperUpdates(saved)
            _state.update { state ->
                state.copy(
                    bulkProgress = null,
                    bulkResult = BulkTagResult(
                        tag = cleanTag,
                        add = add,
                        saved = saved.size,
                        failed = failed,
                    ),
                )
            }
        }
    }

    /**
     * Resuelve la tarjeta de fondo del día. Con elección del día ya guardada
     * en el teléfono se muestra esa sin tocar el servidor; si no, se pide al
     * catálogo la posición del día y se guarda acá. Todos los caminos que no
     * encuentran fondo dejan la tarjeta oculta sin avisos.
     */
    fun loadDailyWallpaper() {
        if (dailyJob?.isActive == true) return
        _state.value.dailyWallpaper?.let { return }
        dailyJob = viewModelScope.launch {
            try {
                val today = LocalDate.now().toString()
                prefs.dailyWallpaper(today)?.let { saved ->
                    _state.update { it.copy(dailyWallpaper = saved) }
                    return@launch
                }
                val picked = api.loadDailyWallpaper(LocalDate.now().toEpochDay()) ?: return@launch
                _state.update { it.copy(dailyWallpaper = picked) }
                prefs.rememberDailyWallpaper(today, picked)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
    }

    fun replaceWallpaperImage(wallpaperId: String, uri: Uri) {
        val wallpaper = _state.value.studioWallpapers.firstOrNull { it.id == wallpaperId } ?: return
        if (_state.value.isDemo) {
            _state.update {
                val updated = wallpaper.copy(imageUrl = uri.toString(), thumbUrl = "")
                it.copy(
                    wallpapers = it.wallpapers.map { w -> if (w.id == wallpaperId) updated else w },
                    adminWallpapers = it.adminWallpapers.map { w ->
                        if (w.id == wallpaperId) updated else w
                    },
                    message = strings.get(R.string.msg_image_updated),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                val updated = api.replaceWallpaperImage(wallpaper, uri)
                _state.update {
                    it.copy(
                        studioBusy = false,
                        wallpapers = it.wallpapers.map { w ->
                            if (w.id == wallpaperId) updated else w
                        },
                        adminWallpapers = it.adminWallpapers.map { w ->
                            if (w.id == wallpaperId) updated else w
                        },
                        message = strings.get(R.string.msg_image_replaced),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.afterError(error)
                }
            }
        }
    }

    fun archiveWallpaper(wallpaperId: String, archived: Boolean) {
        if (_state.value.isDemo) {
            _state.update {
                val target = it.studioWallpapers.firstOrNull { w -> w.id == wallpaperId }
                    ?: return@update it
                val updated = target.copy(archivedAt = if (archived) "local" else "")
                val adminUpdated = (it.adminWallpapers.filterNot { w -> w.id == wallpaperId } +
                    updated).distinctBy { w -> w.id }
                it.copy(
                    adminWallpapers = adminUpdated,
                    wallpapers = if (archived) {
                        it.wallpapers.filterNot { w -> w.id == wallpaperId }
                    } else {
                        (it.wallpapers.filterNot { w -> w.id == wallpaperId } + updated)
                            .distinctBy { w -> w.id }
                    },
                    message = if (archived) {
                        strings.get(R.string.msg_wallpaper_archived)
                    } else {
                        strings.get(R.string.msg_wallpaper_restored)
                    },
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.archiveWallpaper(wallpaperId, archived)
                _state.update {
                    val adminUpdated = it.adminWallpapers.map { w ->
                        if (w.id == wallpaperId) {
                            w.copy(archivedAt = if (archived) "archivado" else "")
                        } else {
                            w
                        }
                    }
                    it.copy(
                        studioBusy = false,
                        adminWallpapers = adminUpdated,
                        wallpapers = if (archived) {
                            it.wallpapers.filterNot { w -> w.id == wallpaperId }
                        } else {
                            it.wallpapers + adminUpdated.filter { w ->
                                w.id == wallpaperId && !w.isArchived
                            }
                        },
                        message = if (archived) {
                            strings.get(R.string.msg_wallpaper_archived)
                        } else {
                            strings.get(R.string.msg_wallpaper_restored)
                        },
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.afterError(error)
                }
            }
        }
    }

    fun deleteWallpaper(wallpaperId: String) {
        softDeleteWallpapers(setOf(wallpaperId))
    }

    fun softDeleteWallpapers(ids: Set<String>) {
        val targets = _state.value.studioWallpapers.filter { it.id in ids }
        if (targets.isEmpty()) return
        val message = if (targets.size == 1) {
            strings.get(R.string.msg_wallpaper_trashed)
        } else {
            strings.get(R.string.msg_wallpapers_trashed)
        }
        if (_state.value.isDemo) {
            _state.update {
                it.withTrash(targets, message)
            }
            viewModelScope.launch { removeFromWallpaperCache(ids) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.softDeleteWallpapers(targets.map { it.id })
                _state.update {
                    it.withTrash(targets, message).copy(studioBusy = false)
                }
                removeFromWallpaperCache(ids)
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun restoreWallpapers(ids: Set<String>) {
        val targets = _state.value.trashWallpapers.filter { it.id in ids }
        if (targets.isEmpty()) return
        val restored = targets.map { it.copy(deletedAt = "") }
        val message = strings.get(R.string.msg_wallpapers_restored)
        if (_state.value.isDemo) {
            _state.update { it.withRestored(restored, message) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.restoreWallpapers(targets.map { it.id })
                _state.update {
                    it.withRestored(restored, message).copy(studioBusy = false)
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun purgeWallpaper(wallpaperId: String) {
        val wallpaper = _state.value.trashWallpapers.firstOrNull { it.id == wallpaperId } ?: return
        purgeWallpapers(listOf(wallpaper))
    }

    fun emptyTrash() {
        purgeWallpapers(_state.value.trashWallpapers)
    }

    /**
     * Borrado definitivo desde la papelera. El registro se borra primero y la
     * imagen después: si el archivo no se pudo borrar, la lista queda
     * consistente y se avisa con un mensaje claro.
     */
    private fun purgeWallpapers(targets: List<Wallpaper>) {
        if (targets.isEmpty()) return
        val ids = targets.map { it.id }.toSet()
        // El registro se borra siempre; lo único que puede fallar es el
        // archivo de imagen, y por eso tiene un mensaje aparte.
        val partialMessage = if (targets.size == 1) {
            strings.get(R.string.msg_image_delete_failed)
        } else {
            strings.get(R.string.msg_image_delete_failed_many)
        }
        if (_state.value.isDemo) {
            viewModelScope.launch {
                cleanupAfterPurge(ids)
                _state.update {
                    it.withPurged(ids, strings.get(R.string.msg_trash_emptied))
                }
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                var imageFailed = false
                targets.forEach { wallpaper ->
                    if (!api.deleteWallpaper(wallpaper)) imageFailed = true
                }
                cleanupAfterPurge(ids)
                _state.update {
                    it.withPurged(
                        ids = ids,
                        message = if (imageFailed) partialMessage else {
                            strings.get(R.string.msg_trash_emptied)
                        },
                    ).copy(studioBusy = false)
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    /**
     * La caché offline del feed no debe volver a mostrar fondos que ya no
     * están en el catálogo ni en el Estudio.
     */
    private suspend fun removeFromWallpaperCache(ids: Set<String>) {
        runCatching {
            cache.writeWallpapers(
                cache.readWallpapers().filterNot { it.id in ids },
            )
        }
    }

    /**
     * Quita del teléfono los restos de los fondos borrados: favoritos,
     * colecciones locales y la caché del feed, para que ningún contador ni
     * lista queden apuntando a un fondo que ya no existe.
     */
    private suspend fun cleanupAfterPurge(ids: Set<String>) {
        prefs.removeFavorites(ids)
        saveLocalCollections(
            prefs.localCollections().map { collection ->
                if (collection.wallpaperIds.none { it in ids }) {
                    collection
                } else {
                    collection.copy(
                        wallpaperIds = collection.wallpaperIds.filterNot { it in ids },
                    )
                }
            },
        )
        removeFromWallpaperCache(ids)
        runCatching {
            cache.writeCollections(
                cache.readCollections().map { collection ->
                    if (collection.wallpaperIds.none { it in ids }) {
                        collection
                    } else {
                        collection.copy(
                            wallpaperIds = collection.wallpaperIds.filterNot { it in ids },
                        )
                    }
                },
            )
        }
    }

    private fun UiState.withPurged(ids: Set<String>, message: String): UiState = copy(
        wallpapers = wallpapers.filterNot { it.id in ids },
        adminWallpapers = adminWallpapers.filterNot { it.id in ids },
        trashWallpapers = trashWallpapers.filterNot { it.id in ids },
        favorites = favorites - ids,
        collectionItems = collectionItems.map { collection ->
            if (collection.wallpaperIds.none { it in ids }) {
                collection
            } else {
                collection.copy(wallpaperIds = collection.wallpaperIds.filterNot { it in ids })
            }
        },
        message = message,
    )

    fun bulkSetFeatured(ids: Set<String>, featured: Boolean) {
        val targets = _state.value.studioWallpapers.filter { it.id in ids }
        if (targets.isEmpty()) return
        var next = (_state.value.studioWallpapers
            .filter { it.featured && !it.isDeleted }
            .maxOfOrNull { it.featuredPosition } ?: -1) + 1
        val updated = targets.associate { wallpaper ->
            wallpaper.id to wallpaper.copy(
                featured = featured,
                featuredPosition = if (featured && !wallpaper.featured) {
                    next++
                } else {
                    wallpaper.featuredPosition
                },
            )
        }
        val message = strings.get(R.string.msg_wallpapers_updated)
        if (_state.value.isDemo) {
            _state.update {
                it.copy(
                    wallpapers = it.wallpapers.map { w -> updated[w.id] ?: w },
                    adminWallpapers = it.adminWallpapers.map { w -> updated[w.id] ?: w },
                    message = message,
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.bulkSetFeatured(targets.map { it.id }, featured)
                _state.update {
                    it.copy(
                        studioBusy = false,
                        wallpapers = it.wallpapers.map { w -> updated[w.id] ?: w },
                        adminWallpapers = it.adminWallpapers.map { w -> updated[w.id] ?: w },
                        message = message,
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun bulkSetArchived(ids: Set<String>, archived: Boolean) {
        val targets = _state.value.studioWallpapers.filter { it.id in ids }
        if (targets.isEmpty()) return
        if (_state.value.isDemo) {
            _state.update {
                it.withBulkArchived(targets, archived).copy(
                    message = if (archived) {
                        strings.get(R.string.msg_wallpapers_archived)
                    } else {
                        strings.get(R.string.msg_wallpapers_restored)
                    },
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.bulkSetArchived(targets.map { it.id }, archived)
                _state.update {
                    it.withBulkArchived(targets, archived).copy(
                        studioBusy = false,
                        message = if (archived) {
                            strings.get(R.string.msg_wallpapers_archived)
                        } else {
                            strings.get(R.string.msg_wallpapers_restored)
                        },
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun bulkDeleteWallpapers(ids: Set<String>) {
        softDeleteWallpapers(ids)
    }

    fun bulkSetCategory(ids: Set<String>, categoryId: String) {
        val category = _state.value.categories.firstOrNull { it.id == categoryId } ?: return
        val targets = _state.value.studioWallpapers.filter { it.id in ids }
        if (targets.isEmpty()) return
        if (_state.value.isDemo) {
            val updated = targets.associate { wallpaper ->
                wallpaper.id to wallpaper.copy(
                    category = category.name,
                    categoryId = category.id,
                )
            }
            _state.update {
                it.copy(
                    wallpapers = it.wallpapers.map { w -> updated[w.id] ?: w },
                    adminWallpapers = it.adminWallpapers.map { w -> updated[w.id] ?: w },
                    message = strings.get(
                        R.string.msg_wallpapers_category_updated,
                        category.name,
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.bulkSetCategory(targets.map { it.id }, category.id)
                val updated = targets.associate { wallpaper ->
                    wallpaper.id to wallpaper.copy(
                        category = category.name,
                        categoryId = category.id,
                    )
                }
                _state.update {
                    it.copy(
                        studioBusy = false,
                        wallpapers = it.wallpapers.map { w -> updated[w.id] ?: w },
                        adminWallpapers = it.adminWallpapers.map { w -> updated[w.id] ?: w },
                        message = strings.get(
                            R.string.msg_wallpapers_category_updated,
                            category.name,
                        ),
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun reorderFeatured(orderedIds: List<String>) {
        val ids = orderedIds.distinct()
        if (ids.isEmpty()) return
        if (_state.value.isDemo) {
            _state.update {
                it.withFeaturedOrder(ids).copy(message = strings.get(R.string.msg_order_updated))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.reorderFeatured(ids)
                _state.update {
                    it.withFeaturedOrder(ids).copy(
                        studioBusy = false,
                        message = strings.get(R.string.msg_order_updated),
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun moveFeatured(wallpaperId: String, direction: Int) {
        if (direction == 0) return
        val ordered = _state.value.studioWallpapers
            .filter { it.featured && !it.isArchived }
            .sortedBy { it.featuredPosition }
        val index = ordered.indexOfFirst { it.id == wallpaperId }
        if (index < 0) return
        val targetIndex = index + direction
        if (targetIndex !in ordered.indices) return
        val ids = ordered.toMutableList().apply {
            val item = removeAt(index)
            add(targetIndex, item)
        }.map { it.id }
        reorderFeatured(ids)
    }

    fun updateCollection(
        collectionId: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ) {
        val clean = name.trim()
        if (clean.isBlank()) {
            _state.update { it.copy(message = strings.get(R.string.msg_collection_name_required)) }
            return
        }
        val localOnly =
            _state.value.collectionItems.firstOrNull { it.id == collectionId }?.isLocal == true
        val visible = if (localOnly) false else isPublic
        if (_state.value.isDemo || localOnly) {
            _state.update {
                val updated = it.collectionItems.map { c ->
                    if (c.id == collectionId) {
                        c.copy(
                            name = clean,
                            description = description.trim(),
                            isPublic = if (c.isLocal) false else visible,
                        )
                    } else {
                        c
                    }
                }
                saveLocalCollections(updated)
                it.copy(
                    collectionItems = updated,
                    message = strings.get(R.string.msg_collection_updated),
                ).collectionDetailUpdated(
                    collectionId = collectionId,
                    name = clean,
                    description = description.trim(),
                    isPublic = if (localOnly) false else visible,
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(collectionBusy = true) }
            try {
                val updated = api.updateCollection(
                    id = collectionId,
                    name = clean,
                    description = description,
                    isPublic = visible,
                )
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        collectionItems = it.collectionItems.map { c ->
                            if (c.id == collectionId) {
                                c.copy(
                                    name = updated.name,
                                    description = updated.description,
                                    isPublic = updated.isPublic,
                                )
                            } else {
                                c
                            }
                        },
                        message = strings.get(R.string.msg_collection_updated),
                    ).collectionDetailUpdated(
                        collectionId = collectionId,
                        name = updated.name,
                        description = updated.description,
                        isPublic = updated.isPublic,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
                reloadCollectionsQuietly()
            }
        }
    }

    fun reorderCollection(collectionId: String, wallpaperIds: List<String>) {
        if (_state.value.isDemo || isLocalCollection(collectionId)) {
            _state.update {
                val updated = it.collectionItems.map { c ->
                    if (c.id == collectionId) c.copy(wallpaperIds = wallpaperIds) else c
                }
                saveLocalCollections(updated)
                it.copy(
                    collectionItems = updated,
                    message = strings.get(R.string.msg_order_updated),
                ).collectionDetailWithWallpapers(collectionId, wallpaperIds)
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(collectionBusy = true) }
            try {
                api.reorderCollection(collectionId, wallpaperIds)
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        collectionItems = it.collectionItems.map { c ->
                            if (c.id == collectionId) c.copy(wallpaperIds = wallpaperIds) else c
                        },
                        message = strings.get(R.string.msg_order_updated),
                    ).collectionDetailWithWallpapers(collectionId, wallpaperIds)
                }
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
                reloadCollectionsQuietly()
            }
        }
    }

    fun addTag(tag: String, wallpaperIds: List<String>) {
        val clean = tag.trim().take(40)
        if (clean.isBlank() || wallpaperIds.isEmpty()) {
            _state.update {
                it.copy(message = strings.get(R.string.msg_pick_name_and_wallpaper))
            }
            return
        }
        val selected = wallpaperIds.toSet()
        if (_state.value.isDemo) {
            _state.update {
                val updated = it.wallpapers.map { w ->
                    if (w.id in selected) {
                        w.copy(tags = (w.tags + clean).distinctBy { t -> t.lowercase() })
                    } else {
                        w
                    }
                }
                it.copy(
                    wallpapers = updated,
                    adminWallpapers = it.adminWallpapers.map { w ->
                        if (w.id in selected) {
                            w.copy(tags = (w.tags + clean).distinctBy { t -> t.lowercase() })
                        } else {
                            w
                        }
                    },
                    message = strings.get(R.string.msg_tag_applied, clean),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.addTag(clean, wallpaperIds)
                _state.update {
                    it.copy(
                        studioBusy = false,
                        wallpapers = it.wallpapers.map { w ->
                            if (w.id in selected) {
                                w.copy(tags = (w.tags + clean).distinctBy { t -> t.lowercase() })
                            } else {
                                w
                            }
                        },
                        adminWallpapers = it.adminWallpapers.map { w ->
                            if (w.id in selected) {
                                w.copy(tags = (w.tags + clean).distinctBy { t -> t.lowercase() })
                            } else {
                                w
                            }
                        },
                        message = strings.get(R.string.msg_tag_applied, clean),
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun renameTag(oldTag: String, newTag: String) {
        val old = oldTag.trim()
        val new = newTag.trim().take(40)
        if (old.isBlank() || new.isBlank()) {
            _state.update { it.copy(message = strings.get(R.string.msg_tag_replace_required)) }
            return
        }
        val rename: (List<String>) -> List<String> = { tags ->
            tags.map { if (it.equals(old, ignoreCase = true)) new else it }
                .distinctBy { it.lowercase() }
        }
        if (_state.value.isDemo) {
            _state.update {
                it.copy(
                    wallpapers = it.wallpapers.map { w ->
                        if (w.tags.any { t -> t.equals(old, ignoreCase = true) }) {
                            w.copy(tags = rename(w.tags))
                        } else {
                            w
                        }
                    },
                    adminWallpapers = it.adminWallpapers.map { w ->
                        if (w.tags.any { t -> t.equals(old, ignoreCase = true) }) {
                            w.copy(tags = rename(w.tags))
                        } else {
                            w
                        }
                    },
                    message = strings.get(R.string.msg_tag_renamed, old, new),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.renameTag(old, new)
                _state.update {
                    it.copy(
                        studioBusy = false,
                        wallpapers = it.wallpapers.map { w ->
                            if (w.tags.any { t -> t.equals(old, ignoreCase = true) }) {
                                w.copy(tags = rename(w.tags))
                            } else {
                                w
                            }
                        },
                        adminWallpapers = it.adminWallpapers.map { w ->
                            if (w.tags.any { t -> t.equals(old, ignoreCase = true) }) {
                                w.copy(tags = rename(w.tags))
                            } else {
                                w
                            }
                        },
                        message = strings.get(R.string.msg_tag_renamed, old, new),
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun deleteTag(tag: String) {
        val clean = tag.trim()
        if (clean.isBlank()) return
        val remove: (List<String>) -> List<String> = { tags ->
            tags.filterNot { it.equals(clean, ignoreCase = true) }
        }
        if (_state.value.isDemo) {
            _state.update {
                it.copy(
                    wallpapers = it.wallpapers.map { w ->
                        if (w.tags.any { t -> t.equals(clean, ignoreCase = true) }) {
                            w.copy(tags = remove(w.tags))
                        } else {
                            w
                        }
                    },
                    adminWallpapers = it.adminWallpapers.map { w ->
                        if (w.tags.any { t -> t.equals(clean, ignoreCase = true) }) {
                            w.copy(tags = remove(w.tags))
                        } else {
                            w
                        }
                    },
                    message = strings.get(R.string.msg_tag_deleted, clean),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.deleteTag(clean)
                _state.update {
                    it.copy(
                        studioBusy = false,
                        wallpapers = it.wallpapers.map { w ->
                            if (w.tags.any { t -> t.equals(clean, ignoreCase = true) }) {
                                w.copy(tags = remove(w.tags))
                            } else {
                                w
                            }
                        },
                        adminWallpapers = it.adminWallpapers.map { w ->
                            if (w.tags.any { t -> t.equals(clean, ignoreCase = true) }) {
                                w.copy(tags = remove(w.tags))
                            } else {
                                w
                            }
                        },
                        message = strings.get(R.string.msg_tag_deleted, clean),
                    )
                }
            } catch (error: Exception) {
                handleBulkFailure(error)
            }
        }
    }

    fun createCollection(
        name: String,
        description: String,
        isPublic: Boolean,
        onDevice: Boolean = false,
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(message = strings.get(R.string.msg_collection_name_required)) }
            return
        }
        val session = _state.value.session
        // Sin sesión no hay cuenta: la colección vive solo en el teléfono.
        if (_state.value.isDemo || session == null || onDevice) {
            val created = WallpaperCollection(
                id = "$LOCAL_COLLECTION_PREFIX${UUID.randomUUID()}",
                name = cleanName,
                description = description.trim(),
                ownerId = LOCAL_COLLECTION_OWNER,
                ownerName = strings.get(R.string.owner_you),
                isPublic = false,
            )
            val updated = listOf(created) + _state.value.collectionItems
            saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collectionItems = updated,
                    collections = LoadState.Content(updated),
                    message = strings.get(R.string.msg_collection_created, cleanName),
                    collectionCreatedTick = it.collectionCreatedTick + 1,
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(collectionBusy = true) }
            try {
                val created = api.createCollection(cleanName, description, isPublic)
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        collectionItems = listOf(created) + it.collectionItems,
                        message = strings.get(R.string.msg_collection_created, cleanName),
                        collectionCreatedTick = it.collectionCreatedTick + 1,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
            }
        }
    }

    fun addToCollection(collectionId: String, wallpaper: Wallpaper) {
        val target = _state.value.collectionItems.firstOrNull { it.id == collectionId } ?: return
        if (wallpaper.id in target.wallpaperIds) {
            _state.update { it.copy(message = strings.get(R.string.msg_already_in_collection, target.name)) }
            return
        }
        if (_state.value.isDemo || target.isLocal) {
            val updated = _state.value.collectionItems.map { collection ->
                if (collection.id == collectionId) {
                    collection.copy(wallpaperIds = collection.wallpaperIds + wallpaper.id)
                } else {
                    collection
                }
            }
            saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collectionItems = updated,
                    message = strings.get(R.string.msg_saved_in_collection, target.name),
                )
            }
            return
        }
        if (_state.value.session == null) {
            _state.update { it.copy(message = strings.get(R.string.msg_login_to_save)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(collectionBusy = true) }
            try {
                api.addToCollection(collectionId, wallpaper.id)
                val updated = _state.value.collectionItems.map { collection ->
                    if (collection.id == collectionId) {
                        collection.copy(wallpaperIds = collection.wallpaperIds + wallpaper.id)
                    } else {
                        collection
                    }
                }
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        collectionItems = updated,
                        message = strings.get(R.string.msg_saved_in_collection, target.name),
                    )
                }
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
                reloadCollectionsQuietly()
            }
        }
    }

    fun removeFromCollection(collectionId: String, wallpaperId: String) {
        if (_state.value.isDemo || isLocalCollection(collectionId)) {
            val updated = _state.value.collectionItems.map { collection ->
                if (collection.id == collectionId) {
                    collection.copy(wallpaperIds = collection.wallpaperIds - wallpaperId)
                } else {
                    collection
                }
            }
            saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collectionItems = updated,
                    message = strings.get(R.string.msg_removed_from_collection),
                ).collectionDetailWithout(collectionId, wallpaperId)
            }
            return
        }
        if (_state.value.session == null) {
            _state.update { it.copy(message = strings.get(R.string.msg_login_to_edit_collections)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(collectionBusy = true) }
            try {
                api.removeFromCollection(collectionId, wallpaperId)
                val updated = _state.value.collectionItems.map { collection ->
                    if (collection.id == collectionId) {
                        collection.copy(wallpaperIds = collection.wallpaperIds - wallpaperId)
                    } else {
                        collection
                    }
                }
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        collectionItems = updated,
                        message = strings.get(R.string.msg_removed_from_collection),
                    ).collectionDetailWithout(collectionId, wallpaperId)
                }
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
                reloadCollectionsQuietly()
            }
        }
    }

    /**
     * Reemplaza la selección completa de fondos de una colección en dos
     * operaciones, en vez de una llamada por cada alta y baja.
     */
    fun setCollectionWallpapers(collectionId: String, wallpaperIds: List<String>) {
        val target = _state.value.collectionItems.firstOrNull { it.id == collectionId } ?: return
        val selected = wallpaperIds.distinct()
        if (_state.value.isDemo || target.isLocal) {
            val updated = _state.value.collectionItems.map { collection ->
                if (collection.id == collectionId) {
                    collection.copy(wallpaperIds = selected)
                } else {
                    collection
                }
            }
            saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collectionItems = updated,
                    message = strings.get(R.string.msg_collection_renamed, target.name),
                ).collectionDetailWithWallpapers(collectionId, selected)
            }
            return
        }
        if (_state.value.session == null) {
            _state.update { it.copy(message = strings.get(R.string.msg_login_to_edit_collections)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(collectionBusy = true) }
            try {
                api.setCollectionWallpapers(collectionId, selected)
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        collectionItems = it.collectionItems.map { collection ->
                            if (collection.id == collectionId) {
                                collection.copy(wallpaperIds = selected)
                            } else {
                                collection
                            }
                        },
                        message = strings.get(R.string.msg_collection_renamed, target.name),
                    ).collectionDetailWithWallpapers(collectionId, selected)
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(collectionBusy = false) }
                throw error
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
                reloadCollectionsQuietly()
            }
        }
    }

    fun deleteCollection(collectionId: String) {
        val target = _state.value.collectionItems.firstOrNull { it.id == collectionId } ?: return
        if (_state.value.isDemo || target.isLocal) {
            val updated = _state.value.collectionItems.filterNot { it.id == collectionId }
            saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collectionItems = updated,
                    message = strings.get(R.string.msg_collection_deleted, target.name),
                ).collectionDetailDeleted(collectionId)
            }
            return
        }
        if (_state.value.session == null) {
            _state.update { it.copy(message = strings.get(R.string.msg_login_to_delete_collections)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(collectionBusy = true) }
            try {
                api.deleteCollection(collectionId)
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        collectionItems = it.collectionItems.filterNot { c -> c.id == collectionId },
                        message = strings.get(R.string.msg_collection_deleted, target.name),
                    ).collectionDetailDeleted(collectionId)
                }
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
                reloadCollectionsQuietly()
            }
        }
    }

    /**
     * Marca el inicio de una acción y descarta cualquier acción anterior. El
     * token evita que el cierre tardío de una descarga cancelada borre el
     * progreso de la acción que la reemplazó.
     */
    private fun beginAction(action: UiAction, progress: Int? = null): Int {
        actionToken += 1
        actionJob?.cancel()
        actionJob = null
        _actionState.value = ActionState(active = action, progress = progress)
        _state.update { it.copy(message = null, messageAction = null) }
        return actionToken
    }

    private fun finishAction(token: Int) {
        if (token != actionToken) return
        actionJob = null
        _actionState.value = ActionState()
    }

    fun download(wallpaper: Wallpaper) {
        val known = prefs.downloadedUri(wallpaper.id)
        if (known != null && actions.exists(known)) {
            _state.update { it.copy(downloadPrompt = DownloadPrompt(wallpaper.id, known)) }
            return
        }
        startDownload(wallpaper)
    }

    fun dismissDownloadPrompt() {
        _state.update { it.copy(downloadPrompt = null) }
    }

    fun downloadAgain(wallpaperId: String) {
        val wallpaper = _state.value.wallpaperById(wallpaperId)
        _state.update { it.copy(downloadPrompt = null) }
        if (wallpaper != null) {
            startDownload(wallpaper)
        }
    }

    private fun startDownload(wallpaper: Wallpaper) {
        val token = beginAction(UiAction.DOWNLOAD, progress = 0)
        actionJob = viewModelScope.launch {
            try {
                val uri = actions.saveToGallery(wallpaper) { done, total ->
                    val percent = if (total > 0) {
                        ((done * 100) / total).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    if (token == actionToken) {
                        _actionState.update { it.copy(progress = percent) }
                    }
                }
                if (token != actionToken) return@launch
                prefs.rememberDownload(wallpaper.id, uri)
                finishAction(token)
                _state.update {
                    it.copy(
                        downloadedIds = it.downloadedIds + wallpaper.id,
                        message = strings.get(R.string.msg_saved_gallery),
                        messageAction = MessageAction.OpenFile(
                            label = strings.get(R.string.action_open),
                            uri = uri,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                finishAction(token)
                throw error
            } catch (error: Exception) {
                finishAction(token)
                if (token == actionToken) {
                    _state.update {
                        it.copy(
                            message = error.userMessage(),
                            messageAction = MessageAction.RetryDownload(
                                label = strings.get(R.string.action_retry),
                                wallpaperId = wallpaper.id,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun setWallpaper(wallpaper: Wallpaper, target: WallpaperTarget) {
        val token = beginAction(UiAction.APPLY)
        actionJob = viewModelScope.launch {
            try {
                val applied = actions.setAsWallpaper(wallpaper, target)
                if (token != actionToken) return@launch
                finishAction(token)
                _state.update {
                    it.copy(
                        message = appliedMessage(applied),
                        messageAction = null,
                        applyTick = it.applyTick + 1,
                    )
                }
            } catch (error: CancellationException) {
                finishAction(token)
                throw error
            } catch (error: Exception) {
                finishAction(token)
                if (token == actionToken) {
                    _state.update {
                        it.copy(
                            message = strings.get(R.string.msg_apply_failed),
                            messageAction = null,
                        )
                    }
                }
            }
        }
    }

    /**
     * El editor aplica la imagen por su cuenta y avisa acá para mostrar el
     * mismo mensaje que el flujo anterior.
     */
    fun onWallpaperApplied(target: WallpaperTarget) {
        _state.update {
            it.copy(
                message = appliedMessage(target),
                messageAction = null,
                applyTick = it.applyTick + 1,
            )
        }
    }

    private fun appliedMessage(target: WallpaperTarget): String = when (target) {
        WallpaperTarget.SYSTEM -> strings.get(R.string.msg_applied_system)
        WallpaperTarget.LOCK -> strings.get(R.string.msg_applied_lock)
        WallpaperTarget.BOTH -> strings.get(R.string.msg_applied_both)
    }

    /** Mensaje localizado para el usuario, sin texto crudo del servidor. */
    private fun Throwable.userMessage(): String = when (kind()) {
        ErrorKind.OFFLINE -> strings.get(R.string.error_offline)
        ErrorKind.SERVER -> strings.get(R.string.error_server)
        ErrorKind.SESSION -> strings.get(R.string.error_session)
        ErrorKind.PERMISSION -> strings.get(R.string.error_permission)
        ErrorKind.UNEXPECTED -> strings.get(R.string.error_unexpected)
    }

    /** Los fallos de carga conservan un mensaje específico para el Estudio. */
    private fun Throwable.uploadMessage(): String = when (kind()) {
        ErrorKind.UNEXPECTED -> strings.get(R.string.msg_upload_failed)
        else -> userMessage()
    }

    fun cancelAction() {
        val active = _actionState.value.active ?: return
        actionToken += 1
        actionJob?.cancel()
        actionJob = null
        _actionState.value = ActionState()
        val message = when (active) {
            UiAction.DOWNLOAD -> strings.get(R.string.msg_download_cancelled)
            UiAction.APPLY -> strings.get(R.string.msg_apply_cancelled)
            UiAction.SHARE -> strings.get(R.string.msg_action_cancelled)
        }
        _state.update { it.copy(message = message, messageAction = null) }
    }

    fun share(wallpaper: Wallpaper) {
        val token = beginAction(UiAction.SHARE)
        actionJob = viewModelScope.launch {
            try {
                val image = actions.prepareShare(wallpaper)
                actions.share(image, wallpaper.title)
                finishAction(token)
            } catch (error: CancellationException) {
                finishAction(token)
                throw error
            } catch (error: Exception) {
                finishAction(token)
                if (token == actionToken) {
                    _state.update {
                        it.copy(
                            message = strings.get(R.string.msg_share_failed),
                            messageAction = null,
                        )
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, messageAction = null) }
    }

    fun notifyFileOpenFailed() {
        _state.update {
            it.copy(
                message = strings.get(R.string.msg_open_file_failed),
                messageAction = null,
            )
        }
    }

    /** Cambia entre tema del sistema, claro u oscuro. */
    fun setThemeMode(mode: ThemeMode) {
        appearance.saveThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    /** Activa o desactiva las animaciones más pesadas. */
    fun setLiteMode(enabled: Boolean) {
        appearance.saveLiteMode(enabled)
        _state.update { it.copy(liteMode = enabled) }
    }

    /** Define si el botón Aplicar abre primero el editor de fondos. */
    fun setOpenEditorOnApply(enabled: Boolean) {
        appearance.saveOpenEditorOnApply(enabled)
        _state.update { it.copy(openEditorOnApply = enabled) }
    }

    /** Vuelve la apariencia a los valores de fábrica. */
    fun resetAppearance() {
        appearance.reset()
        _state.update {
            it.copy(
                themeMode = ThemeMode.SYSTEM,
                liteMode = false,
                openEditorOnApply = true,
                message = strings.get(R.string.msg_settings_reset),
                messageAction = null,
            )
        }
    }

    /** Borra la copia local del feed; se vuelve a bajar al actualizar. */
    fun clearCache() {
        viewModelScope.launch {
            cache.writeWallpapers(emptyList())
            cache.writeCollections(emptyList())
            _state.update {
                it.copy(message = strings.get(R.string.msg_cache_cleared), messageAction = null)
            }
        }
    }

    /**
     * Marca la bienvenida como vista; [UiState.onboardingDone] abre la app.
     */
    fun completeOnboarding() {
        prefs.completeOnboarding()
        _state.update { it.copy(onboardingDone = true) }
    }

    private fun restoreSession() {
        if (!api.isConfigured) return
        viewModelScope.launch {
            val session = api.refreshSession()
            if (session == null) {
                // Si el refresh token fue rechazado, la implementación borra
                // las preferencias; en ese caso tampoco dejamos la sesión
                // vieja en pantalla ni colecciones privadas cacheadas.
                if (prefs.session() == null) {
                    _state.update {
                        val publicCollections = it.collectionItems.filter {
                            c -> c.isLocal || c.isPublic
                        }
                        it.copy(
                            session = null,
                            adminWallpapers = emptyList(),
                            collectionItems = publicCollections,
                            collections = if (publicCollections.isEmpty()) {
                                LoadState.Empty
                            } else {
                                LoadState.Content(publicCollections)
                            },
                        )
                    }
                }
                return@launch
            }
            _state.update { it.copy(session = session) }
        }
    }

    /**
     * Una operación masiva puede quedar a medias si falla en el servidor. Se
     * muestra el error y se vuelve a leer el Estudio para no dejar en pantalla
     * un estado que ya no coincide con la base.
     */
    private fun handleBulkFailure(error: Exception) {
        _state.update { it.afterError(error) }
        if (_state.value.session != null) {
            loadStudio()
        }
    }

    /**
     * Relee las colecciones sin pisar el mensaje de error. Si la sesión venció,
     * afterError ya la ocultó y no se pide nada más.
     */
    private suspend fun reloadCollectionsQuietly() {
        if (_state.value.session == null) return
        try {
            val collections = mergedWithLocal(api.loadCollections())
            _state.update {
                it.copy(
                    collectionItems = collections,
                    collections = if (collections.isEmpty()) {
                        LoadState.Empty
                    } else {
                        LoadState.Content(collections)
                    },
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // El error original ya está visible. Si tampoco se puede releer, la
            // próxima acción o el refresco general volverán a intentarlo.
        }
    }

    private suspend fun loadCollectionsOrEmpty(
        fallback: List<WallpaperCollection>,
    ): List<WallpaperCollection> =
        mergedWithLocal(
            try {
                api.loadCollections()
            } catch (error: CancellationException) {
                throw error
            } catch (error: DannXError.SessionExpired) {
                throw error
            } catch (error: Exception) {
                fallback
            },
        )

    private suspend fun loadCategoriesOrEmpty(
        fallback: List<WallpaperCategory>,
    ): List<WallpaperCategory> =
        try {
            api.loadCategories().sortedWith(compareBy({ c -> c.position }, { c -> c.name }))
        } catch (error: CancellationException) {
            throw error
        } catch (error: DannXError.SessionExpired) {
            throw error
        } catch (error: Exception) {
            fallback
        }

    private suspend fun loadAppSettingsOrFallback(fallback: AppSettings): AppSettings =
        try {
            val settings = api.loadAppSettings().normalized()
            prefs.saveAppSettings(settings)
            settings
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            fallback
        }

    private fun isReservedCategoryName(name: String): Boolean =
        name.equals("Todos", ignoreCase = true) ||
            name.equals("Destacados", ignoreCase = true) ||
            name.equals("All", ignoreCase = true) ||
            name.equals("Featured", ignoreCase = true) ||
            name == CategoryAll ||
            name == CategoryFeatured

    private fun UiState.withBulkArchived(
        targets: List<Wallpaper>,
        archived: Boolean,
    ): UiState {
        val updated = targets.associate { wallpaper ->
            wallpaper.id to wallpaper.copy(
                archivedAt = if (archived) "archivado" else "",
            )
        }
        val admin = (adminWallpapers.map { updated[it.id] ?: it } +
            updated.values.filter { it.isArchived }).distinctBy { it.id }
        val feed = if (archived) {
            wallpapers.filterNot { it.id in updated.keys }
        } else {
            (wallpapers.filterNot { it.id in updated.keys } +
                updated.values.filter { !it.isArchived }).distinctBy { it.id }
        }
        return copy(adminWallpapers = admin, wallpapers = feed)
    }

    private fun UiState.withTrash(
        targets: List<Wallpaper>,
        message: String,
    ): UiState {
        val ids = targets.map { it.id }.toSet()
        val trashed = targets.map { it.copy(deletedAt = "local") }
        return copy(
            wallpapers = wallpapers.filterNot { it.id in ids },
            adminWallpapers = adminWallpapers.filterNot { it.id in ids },
            trashWallpapers = (trashWallpapers.filterNot { it.id in ids } + trashed)
                .distinctBy { it.id },
            message = message,
        )
    }

    private fun UiState.withRestored(
        restored: List<Wallpaper>,
        message: String,
    ): UiState {
        val ids = restored.map { it.id }.toSet()
        return copy(
            adminWallpapers = (adminWallpapers.filterNot { it.id in ids } + restored)
                .distinctBy { it.id },
            wallpapers = (wallpapers.filterNot { it.id in ids } +
                restored.filter { !it.isArchived && !it.isDeleted }).distinctBy { it.id },
            trashWallpapers = trashWallpapers.filterNot { it.id in ids },
            message = message,
        )
    }

    private fun UiState.withFeaturedOrder(ids: List<String>): UiState {
        val positions = ids.withIndex().associate { (index, id) -> id to index }
        val reorder: (List<Wallpaper>) -> List<Wallpaper> = { list ->
            list.map { wallpaper ->
                positions[wallpaper.id]?.let { wallpaper.copy(featuredPosition = it) } ?: wallpaper
            }
        }
        return copy(
            wallpapers = reorder(wallpapers),
            adminWallpapers = reorder(adminWallpapers),
        )
    }

    /**
     * Deja la app en un estado coherente después de un error. Si la sesión
     * venció, se oculta el Estudio y se quitan las colecciones privadas.
     */
    private fun UiState.afterError(error: Throwable): UiState =
        afterError(error, error.userMessage())

    private fun UiState.afterError(
        error: Throwable,
        message: String,
    ): UiState {
        val expired = error is DannXError.SessionExpired
        val publicCollections = if (expired) {
            collectionItems.filter { it.isLocal || it.isPublic }
        } else {
            collectionItems
        }
        return copy(
            message = message,
            messageAction = null,
            session = if (expired) null else session,
            adminWallpapers = if (expired) emptyList() else adminWallpapers,
            trashWallpapers = if (expired) emptyList() else trashWallpapers,
            collectionItems = publicCollections,
            collections = if (expired) {
                if (publicCollections.isEmpty()) {
                    LoadState.Empty
                } else {
                    LoadState.Content(publicCollections)
                }
            } else {
                collections
            },
            uploading = false,
            authBusy = false,
            collectionBusy = false,
            studioBusy = false,
        )
    }

    companion object {
        private const val KEY_QUERY = "query"
        private const val KEY_CATEGORY = "category"
        private const val KEY_TAG = "tag"
        private const val KEY_COLOR = "color"
        private const val KEY_UPLOAD_URI = "upload_uri"
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val REFRESH_WINDOW_MS = 5 * 60 * 1000L
        private const val COLOR_BATCH_SIZE = 4

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val prefs = Prefs(application)
                MainViewModel(
                    savedState = createSavedStateHandle(),
                    prefs = prefs,
                    api = WallsApi(application, prefs),
                    cache = FeedCache(application),
                    actions = WallpaperFiles(application),
                    isOnline = { Connectivity.isOnline(application) },
                    appearance = AppearancePrefs(application),
                    strings = application.stringResolver(),
                    colorResolver = CoilWallpaperColorResolver(application),
                )
            }
        }
    }
}

/**
 * Traduce una excepción a un tipo de error de interfaz.
 */
fun Throwable.kind(): ErrorKind = when (this) {
    is DannXError.Offline -> ErrorKind.OFFLINE
    is DannXError.Server -> ErrorKind.SERVER
    is DannXError.SessionExpired -> ErrorKind.SESSION
    is DannXError.Forbidden -> ErrorKind.PERMISSION
    else -> ErrorKind.UNEXPECTED
}
