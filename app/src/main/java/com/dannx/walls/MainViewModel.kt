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
import com.dannx.walls.data.AppearancePrefs
import com.dannx.walls.data.AppearanceStore
import com.dannx.walls.data.CategoryAll
import com.dannx.walls.data.CategoryFeatured
import com.dannx.walls.data.Connectivity
import com.dannx.walls.data.DannXError
import com.dannx.walls.data.FeedCache
import com.dannx.walls.data.FeedCacheStore
import com.dannx.walls.data.InMemoryAppearanceStore
import com.dannx.walls.data.LocalStore
import com.dannx.walls.data.Prefs
import com.dannx.walls.data.ThemeMode
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperActions
import com.dannx.walls.data.WallpaperCategory
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.WallpapersRepository
import com.dannx.walls.data.WallsApi
import com.dannx.walls.data.collectTags
import com.dannx.walls.data.filterWallpapers
import com.dannx.walls.util.WallpaperFiles
import com.dannx.walls.util.WallpaperTarget
import com.dannx.walls.util.StringResolver
import com.dannx.walls.util.get
import com.dannx.walls.util.stringResolver
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UiAction {
    DOWNLOAD,
    APPLY,
    SHARE,
}

data class MessageAction(
    val label: String,
    val uri: String,
)

data class DownloadPrompt(
    val wallpaperId: String,
    val uri: String,
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
    val wallpapers: List<Wallpaper> = emptyList(),
    val adminWallpapers: List<Wallpaper> = emptyList(),
    val categories: List<WallpaperCategory> = emptyList(),
    val collectionItems: List<WallpaperCollection> = emptyList(),
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val category: String? = null,
    val tag: String? = null,
    val favorites: Set<String> = emptySet(),
    val session: AdminSession? = null,
    val isDemo: Boolean = true,
    val uploading: Boolean = false,
    val onboardingDone: Boolean = false,
    val collectionBusy: Boolean = false,
    val studioBusy: Boolean = false,
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
) {
    val filtered: List<Wallpaper>
        get() = filterWallpapers(wallpapers, query, category, tag)

    val featured: List<Wallpaper>
        get() = wallpapers.filter { it.featured }

    val favoriteWallpapers: List<Wallpaper>
        get() = wallpapers.filter { it.id in favorites }

    val availableTags: List<String>
        get() = collectTags(wallpapers)

    val myCollections: List<WallpaperCollection>
        get() = if (isDemo) {
            collectionItems
        } else {
            val id = session?.userId.orEmpty()
            if (id.isBlank()) emptyList() else collectionItems.filter { it.ownerId == id }
        }

    val studioWallpapers: List<Wallpaper>
        get() = if (isDemo) wallpapers else adminWallpapers

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
        get() = wallpapers.map { categoryNameFor(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()

    fun categoryNameFor(wallpaper: Wallpaper): String =
        categories.firstOrNull { it.id == wallpaper.categoryId }?.name
            ?: wallpaper.category

    fun canEdit(collection: WallpaperCollection): Boolean =
        isDemo || (session != null && session.userId == collection.ownerId)

    fun wallpapersIn(collection: WallpaperCollection): List<Wallpaper> {
        val byId = wallpapers.associateBy { it.id }
        return collection.wallpaperIds.mapNotNull { byId[it] }
    }
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
) : ViewModel() {
    private var actionJob: Job? = null
    private var refreshJob: Job? = null
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
            selectedUploadUri = savedState.get<String>(KEY_UPLOAD_URI),
            themeMode = appearance.themeMode(),
            liteMode = appearance.liteMode(),
            openEditorOnApply = appearance.openEditorOnApply(),
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
        }
    }

    /**
     * Muestra lo guardado en disco cuando no hay red. Sin sesión nunca se
     * muestran colecciones privadas cacheadas.
     */
    private suspend fun loadFromCache() {
        val cachedWallpapers = cache.readWallpapers()
        val cachedCollections = cache.readCollections()
            .filter { _state.value.session != null || it.isPublic }
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
                collections = if (cachedCollections.isEmpty()) {
                    LoadState.Empty
                } else {
                    LoadState.Content(cachedCollections)
                },
                wallpapers = cachedWallpapers,
                collectionItems = cachedCollections,
                offline = true,
            )
        }
    }

    /**
     * Recarga el feed y las colecciones. Si ya hay una recarga en curso, la
     * cancela para no piling estados ni requests.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val state = _state.value
            val hasContent = state.wallpapers.isNotEmpty() || state.collectionItems.isNotEmpty()
            _state.update {
                if (hasContent) {
                    it.copy(
                        feed = if (it.wallpapers.isEmpty()) LoadState.Loading else it.feed,
                        collections = if (it.collectionItems.isEmpty()) LoadState.Loading else it.collections,
                        refreshing = true,
                    )
                } else {
                    it.copy(
                        feed = LoadState.Loading,
                        collections = LoadState.Loading,
                        refreshing = false,
                    )
                }
            }
            try {
                val items = api.loadWallpapers()
                val collections = try {
                    api.loadCollections()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: DannXError.SessionExpired) {
                    throw error
                } catch (error: Exception) {
                    _state.update { state -> state.copy(message = error.userMessage()) }
                    _state.value.collectionItems
                }
                val categories = loadCategoriesOrEmpty(_state.value.categories)
                cache.writeWallpapers(items)
                cache.writeCollections(collections)
                _state.update {
                    it.copy(
                        feed = if (items.isEmpty()) LoadState.Empty else LoadState.Content(items),
                        collections = if (collections.isEmpty()) {
                            LoadState.Empty
                        } else {
                            LoadState.Content(collections)
                        },
                        wallpapers = items,
                        categories = categories,
                        collectionItems = collections,
                        isDemo = !api.isConfigured,
                        offline = false,
                        refreshing = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val kind = error.kind()
                val expired = error is DannXError.SessionExpired
                val current = _state.value
                val cachedWallpapers = cache.readWallpapers()
                val cachedCollections = cache.readCollections()
                val currentCollections = if (expired) {
                    current.collectionItems.filter { it.isPublic }
                } else {
                    current.collectionItems
                }
                val safeCachedCollections = if (expired || current.session == null) {
                    cachedCollections.filter { it.isPublic }
                } else {
                    cachedCollections
                }
                val fallbackWallpapers = current.wallpapers.ifEmpty { cachedWallpapers }
                val fallbackCollections = currentCollections.ifEmpty { safeCachedCollections }
                _state.update {
                    it.copy(
                        feed = if (fallbackWallpapers.isEmpty()) {
                            LoadState.Error(error.userMessage(), kind)
                        } else {
                            LoadState.Content(fallbackWallpapers)
                        },
                        collections = if (fallbackCollections.isEmpty()) {
                            LoadState.Empty
                        } else {
                            LoadState.Content(fallbackCollections)
                        },
                        wallpapers = fallbackWallpapers,
                        collectionItems = fallbackCollections,
                        session = if (expired) null else it.session,
                        adminWallpapers = if (expired) emptyList() else it.adminWallpapers,
                        message = if (expired) error.userMessage() else it.message,
                        offline = fallbackWallpapers.isNotEmpty() && kind == ErrorKind.OFFLINE,
                        refreshing = false,
                    )
                }
                if (expired) {
                    cache.writeCollections(fallbackCollections.filter { it.isPublic })
                }
            }
        }
    }

    /**
     * Refresca solo si hay red y no se está refrescando ya. Se usa al volver
     * de segundo plano para no provocar parpadeos.
     */
    fun refreshIfOnline() {
        if (!isOnline()) return
        if (refreshJob?.isActive == true) return
        refresh()
    }

    fun setQuery(value: String) {
        savedState[KEY_QUERY] = value
        _state.update { it.copy(query = value) }
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
    }

    fun setTag(value: String?) {
        savedState[KEY_TAG] = value
        _state.update { it.copy(tag = value) }
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

    fun signOut() {
        studioJob?.cancel()
        refreshJob?.cancel()
        uploadJob?.cancel()
        prefs.clearSession()
        savedState[KEY_UPLOAD_URI] = null
        val fallback = _state.value.collectionItems.filter { it.isPublic }
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
            )
        }
        viewModelScope.launch {
            // La caché puede contener colecciones privadas de la sesión que se
            // acaba de cerrar: se limpia antes de volver a cargar las públicas.
            cache.writeCollections(emptyList())
            val collections = loadCollectionsOrEmpty(fallback)
            cache.writeCollections(collections.filter { it.isPublic })
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
                        adminWallpapers = walls,
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
        tags: List<String>,
        featured: Boolean,
    ) {
        val cleanTags = tags.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        if (_state.value.isDemo) {
            // En demo los archivados solo están en studioWallpapers. Antes se
            // buscaban en wallpapers y editarlos no hacía nada.
            val updated = _state.value.studioWallpapers.firstOrNull { it.id == wallpaperId }
                ?.copy(
                    title = title,
                    category = category,
                    categoryId = categoryId,
                    tags = cleanTags,
                    featured = featured,
                )
                ?: return
            _state.update {
                it.copy(
                    wallpapers = it.wallpapers.map { w -> if (w.id == wallpaperId) updated else w },
                    adminWallpapers = it.adminWallpapers.map { w ->
                        if (w.id == wallpaperId) updated else w
                    },
                    message = strings.get(R.string.msg_wallpaper_updated),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                val updated = api.updateWallpaper(
                    wallpaperId = wallpaperId,
                    title = title,
                    category = category,
                    categoryId = categoryId,
                    tags = cleanTags,
                    featured = featured,
                )
                _state.update {
                    it.copy(
                        studioBusy = false,
                        wallpapers = it.wallpapers.map { w ->
                            if (w.id == wallpaperId) updated else w
                        },
                        adminWallpapers = it.adminWallpapers.map { w ->
                            if (w.id == wallpaperId) updated else w
                        },
                        message = strings.get(R.string.msg_wallpaper_updated),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.afterError(error)
                }
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
                val adminUpdated = it.adminWallpapers.map { w ->
                    if (w.id == wallpaperId) {
                        w.copy(archivedAt = if (archived) "local" else "")
                    } else {
                        w
                    }
                }
                it.copy(
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
        val wallpaper = _state.value.studioWallpapers.firstOrNull { it.id == wallpaperId }
            ?: _state.value.wallpapers.firstOrNull { it.id == wallpaperId }
            ?: return
        if (_state.value.isDemo) {
            _state.update {
                it.copy(
                    wallpapers = it.wallpapers.filterNot { w -> w.id == wallpaperId },
                    adminWallpapers = it.adminWallpapers.filterNot { w -> w.id == wallpaperId },
                    collectionItems = it.collectionItems.map { c ->
                        c.copy(wallpaperIds = c.wallpaperIds - wallpaperId)
                    },
                    message = strings.get(R.string.msg_wallpaper_deleted),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(studioBusy = true) }
            try {
                api.deleteWallpaper(wallpaper)
                _state.update {
                    it.copy(
                        studioBusy = false,
                        wallpapers = it.wallpapers.filterNot { w -> w.id == wallpaperId },
                        adminWallpapers = it.adminWallpapers.filterNot { w -> w.id == wallpaperId },
                        collectionItems = it.collectionItems.map { c ->
                            c.copy(wallpaperIds = c.wallpaperIds - wallpaperId)
                        },
                        message = strings.get(R.string.msg_wallpaper_deleted),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.afterError(error)
                }
            }
        }
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
        if (_state.value.isDemo) {
            _state.update {
                val updated = it.collectionItems.map { c ->
                    if (c.id == collectionId) {
                        c.copy(name = clean, description = description.trim(), isPublic = isPublic)
                    } else {
                        c
                    }
                }
                prefs.saveLocalCollections(updated)
                it.copy(collectionItems = updated, message = strings.get(R.string.msg_collection_updated))
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
                    isPublic = isPublic,
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
                    )
                }
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
                reloadCollectionsQuietly()
            }
        }
    }

    fun reorderCollection(collectionId: String, wallpaperIds: List<String>) {
        if (_state.value.isDemo) {
            _state.update {
                val updated = it.collectionItems.map { c ->
                    if (c.id == collectionId) c.copy(wallpaperIds = wallpaperIds) else c
                }
                prefs.saveLocalCollections(updated)
                it.copy(collectionItems = updated, message = strings.get(R.string.msg_order_updated))
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
                    )
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

    fun createCollection(name: String, description: String, isPublic: Boolean) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(message = strings.get(R.string.msg_collection_name_required)) }
            return
        }
        if (_state.value.isDemo) {
            val created = WallpaperCollection(
                id = "local-${UUID.randomUUID()}",
                name = cleanName,
                description = description.trim(),
                ownerId = "local",
                ownerName = strings.get(R.string.owner_you),
                isPublic = isPublic,
            )
            val updated = listOf(created) + _state.value.collectionItems
            prefs.saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collectionItems = updated,
                    message = strings.get(R.string.msg_collection_created, cleanName),
                )
            }
            return
        }
        val session = _state.value.session
        if (session == null) {
            _state.update { it.copy(message = strings.get(R.string.msg_login_to_create_collections)) }
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
        if (_state.value.isDemo) {
            val updated = _state.value.collectionItems.map { collection ->
                if (collection.id == collectionId) {
                    collection.copy(wallpaperIds = collection.wallpaperIds + wallpaper.id)
                } else {
                    collection
                }
            }
            prefs.saveLocalCollections(updated)
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
        if (_state.value.isDemo) {
            val updated = _state.value.collectionItems.map { collection ->
                if (collection.id == collectionId) {
                    collection.copy(wallpaperIds = collection.wallpaperIds - wallpaperId)
                } else {
                    collection
                }
            }
            prefs.saveLocalCollections(updated)
            _state.update {
                it.copy(collectionItems = updated, message = strings.get(R.string.msg_removed_from_collection))
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
                    )
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
        if (_state.value.isDemo) {
            val updated = _state.value.collectionItems.map { collection ->
                if (collection.id == collectionId) {
                    collection.copy(wallpaperIds = selected)
                } else {
                    collection
                }
            }
            prefs.saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collectionItems = updated,
                    message = strings.get(R.string.msg_collection_renamed, target.name),
                )
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
                    )
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
        if (_state.value.isDemo) {
            val updated = _state.value.collectionItems.filterNot { it.id == collectionId }
            prefs.saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collectionItems = updated,
                    message = strings.get(R.string.msg_collection_deleted, target.name),
                )
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
                    )
                }
            } catch (error: Exception) {
                _state.update { it.afterError(error) }
                reloadCollectionsQuietly()
            }
        }
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
        val wallpaper = _state.value.wallpapers.firstOrNull { it.id == wallpaperId }
        _state.update { it.copy(downloadPrompt = null) }
        if (wallpaper != null) {
            startDownload(wallpaper)
        }
    }

    private fun startDownload(wallpaper: Wallpaper) {
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _actionState.value = ActionState(active = UiAction.DOWNLOAD, progress = 0)
            try {
                val uri = actions.saveToGallery(wallpaper) { done, total ->
                    val percent = if (total > 0) {
                        ((done * 100) / total).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    _actionState.update { it.copy(progress = percent) }
                }
                prefs.rememberDownload(wallpaper.id, uri)
                _state.update {
                    it.copy(
                        downloadedIds = it.downloadedIds + wallpaper.id,
                        message = strings.get(R.string.msg_saved_gallery),
                        messageAction = MessageAction(
                            label = strings.get(R.string.action_open),
                            uri = uri,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                _actionState.value = ActionState()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        message = error.userMessage(),
                        messageAction = null,
                    )
                }
            }
        }
    }

    fun setWallpaper(wallpaper: Wallpaper, target: WallpaperTarget) {
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _actionState.value = ActionState(active = UiAction.APPLY)
            try {
                val applied = actions.setAsWallpaper(wallpaper, target)
                _state.update {
                    it.copy(message = appliedMessage(applied), messageAction = null)
                }
            } catch (error: CancellationException) {
                _actionState.value = ActionState()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        message = strings.get(R.string.msg_apply_failed),
                    )
                }
            }
        }
    }

    /**
     * El editor aplica la imagen por su cuenta y avisa acá para mostrar el
     * mismo mensaje que el flujo anterior.
     */
    fun onWallpaperApplied(target: WallpaperTarget) {
        _state.update { it.copy(message = appliedMessage(target), messageAction = null) }
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
        actionJob?.cancel()
        actionJob = null
        _actionState.value = ActionState()
    }

    fun share(wallpaper: Wallpaper) {
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _actionState.value = ActionState(active = UiAction.SHARE, progress = 0)
            try {
                val image = actions.prepareShare(wallpaper)
                actions.share(image, wallpaper.title)
                _actionState.value = ActionState()
            } catch (error: CancellationException) {
                _actionState.value = ActionState()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        message = strings.get(R.string.msg_share_failed),
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, messageAction = null) }
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
                        val publicCollections = it.collectionItems.filter { c -> c.isPublic }
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
            val collections = api.loadCollections()
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
        try {
            api.loadCollections()
        } catch (error: CancellationException) {
            throw error
        } catch (error: DannXError.SessionExpired) {
            throw error
        } catch (error: Exception) {
            fallback
        }

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

    private fun isReservedCategoryName(name: String): Boolean =
        name.equals("Todos", ignoreCase = true) ||
            name.equals("Destacados", ignoreCase = true) ||
            name.equals("All", ignoreCase = true) ||
            name.equals("Featured", ignoreCase = true) ||
            name == CategoryAll ||
            name == CategoryFeatured

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
            collectionItems.filter { it.isPublic }
        } else {
            collectionItems
        }
        return copy(
            message = message,
            messageAction = null,
            session = if (expired) null else session,
            adminWallpapers = if (expired) emptyList() else adminWallpapers,
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
        private const val KEY_UPLOAD_URI = "upload_uri"

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
