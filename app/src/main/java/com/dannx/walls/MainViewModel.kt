package com.dannx.walls

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dannx.walls.data.AdminSession
import com.dannx.walls.data.Connectivity
import com.dannx.walls.data.DannXError
import com.dannx.walls.data.FeedCache
import com.dannx.walls.data.Prefs
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.WallsApi
import com.dannx.walls.data.collectTags
import com.dannx.walls.data.filterWallpapers
import com.dannx.walls.util.WallpaperFiles
import com.dannx.walls.util.WallpaperTarget
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
    val uri: Uri,
)

data class DownloadPrompt(
    val wallpaperId: String,
    val uri: Uri,
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
    UNEXPECTED,
}

data class UiState(
    val feed: LoadState<List<Wallpaper>> = LoadState.Loading,
    val collections: LoadState<List<WallpaperCollection>> = LoadState.Loading,
    val wallpapers: List<Wallpaper> = emptyList(),
    val collectionItems: List<WallpaperCollection> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val tag: String? = null,
    val favorites: Set<String> = emptySet(),
    val session: AdminSession? = null,
    val isDemo: Boolean = true,
    val uploading: Boolean = false,
    val collectionBusy: Boolean = false,
    val uploadSuccessCount: Int = 0,
    val authBusy: Boolean = false,
    val message: String? = null,
    val messageAction: MessageAction? = null,
    val activeAction: UiAction? = null,
    val actionProgress: Int? = null,
    val downloadedIds: Set<String> = emptySet(),
    val downloadPrompt: DownloadPrompt? = null,
    val offline: Boolean = false,
    val selectedUploadUri: Uri? = null,
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

    fun canEdit(collection: WallpaperCollection): Boolean =
        isDemo || (session != null && session.userId == collection.ownerId)

    fun wallpapersIn(collection: WallpaperCollection): List<Wallpaper> =
        collection.wallpaperIds.mapNotNull { id -> wallpapers.firstOrNull { it.id == id } }
}

class MainViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
    private val prefs: Prefs,
    private val api: WallsApi,
    private val cache: FeedCache,
) : AndroidViewModel(application) {
    private var actionJob: Job? = null
    private var refreshJob: Job? = null
    private val _state = MutableStateFlow(
        UiState(
            favorites = prefs.favorites(),
            session = prefs.session(),
            isDemo = !api.isConfigured,
            downloadedIds = prefs.downloadedIds(),
            query = savedState.get<String>(KEY_QUERY) ?: "",
            category = savedState.get<String>(KEY_CATEGORY),
            tag = savedState.get<String>(KEY_TAG),
            selectedUploadUri = savedState.get<String>(KEY_UPLOAD_URI)?.let(Uri::parse),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        restoreSession()
        refresh()
    }

    /**
     * Recarga el feed y las colecciones. Si ya hay una recarga en curso, la
     * cancela para no piling estados ni requests.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(feed = LoadState.Loading, collections = LoadState.Loading) }
            try {
                val items = api.loadWallpapers()
                val collections = loadCollectionsOrEmpty(_state.value.collectionItems)
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
                        collectionItems = collections,
                        isDemo = !api.isConfigured,
                        offline = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val kind = error.kind()
                val cachedWallpapers = cache.readWallpapers()
                val cachedCollections = cache.readCollections()
                _state.update {
                    it.copy(
                        feed = if (cachedWallpapers.isEmpty()) {
                            LoadState.Error(error.userMessage(), kind)
                        } else {
                            LoadState.Content(cachedWallpapers)
                        },
                        collections = if (cachedCollections.isEmpty()) {
                            LoadState.Content(emptyList())
                        } else {
                            LoadState.Content(cachedCollections)
                        },
                        wallpapers = cachedWallpapers,
                        collectionItems = cachedCollections,
                        offline = cachedWallpapers.isNotEmpty() && kind == ErrorKind.OFFLINE,
                    )
                }
            }
        }
    }

    /**
     * Refresca solo si hay red y no se está refrescando ya. Se usa al volver
     * de segundo plano para no provocar parpadeos.
     */
    fun refreshIfOnline() {
        if (!Connectivity.isOnline(getApplication())) return
        if (refreshJob?.isActive == true) return
        refresh()
    }

    fun setQuery(value: String) {
        savedState[KEY_QUERY] = value
        _state.update { it.copy(query = value) }
    }

    fun setCategory(value: String?) {
        savedState[KEY_CATEGORY] = value
        _state.update { it.copy(category = value) }
    }

    fun setTag(value: String?) {
        savedState[KEY_TAG] = value
        _state.update { it.copy(tag = value) }
    }

    fun setSelectedUploadUri(uri: Uri?) {
        savedState[KEY_UPLOAD_URI] = uri?.toString()
        _state.update { it.copy(selectedUploadUri = uri) }
    }

    fun toggleFavorite(id: String) {
        _state.update { it.copy(favorites = prefs.toggleFavorite(id)) }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(message = "Escribe tu correo y contraseña.") }
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
                        message = "Sesión iniciada como ${session.email}.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        authBusy = false,
                        message = error.userMessage(),
                    )
                }
            }
        }
    }

    fun signOut() {
        prefs.clearSession()
        val fallback = _state.value.collectionItems.filter { it.isPublic }
        _state.update { it.copy(session = null, collectionItems = fallback, message = "Sesión cerrada.") }
        viewModelScope.launch {
            val collections = loadCollectionsOrEmpty(fallback)
            _state.update { it.copy(collectionItems = collections) }
        }
    }

    fun uploadWallpaper(
        uri: Uri,
        title: String,
        category: String,
        tags: List<String>,
        collectionId: String?,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(uploading = true) }
            try {
                val created = api.uploadWallpaper(uri, title, category, tags, collectionId)
                val collections = loadCollectionsOrEmpty(_state.value.collectionItems)
                _state.update {
                    it.copy(
                        uploading = false,
                        wallpapers = listOf(created) + it.wallpapers,
                        collectionItems = collections,
                        uploadSuccessCount = it.uploadSuccessCount + 1,
                        message = "Wallpaper publicado.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        uploading = false,
                        message = error.userMessage(),
                    )
                }
            }
        }
    }

    fun createCollection(name: String, description: String, isPublic: Boolean) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(message = "Escribe un nombre para la colección.") }
            return
        }
        if (_state.value.isDemo) {
            val created = WallpaperCollection(
                id = "local-${UUID.randomUUID()}",
                name = cleanName,
                description = description.trim(),
                ownerId = "local",
                ownerName = "Tú",
                isPublic = isPublic,
            )
            val updated = listOf(created) + _state.value.collectionItems
            prefs.saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collectionItems = updated,
                    message = "Colección \"$cleanName\" creada.",
                )
            }
            return
        }
        val session = _state.value.session
        if (session == null) {
            _state.update { it.copy(message = "Inicia sesión para crear colecciones.") }
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
                        message = "Colección \"$cleanName\" creada.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        message = error.userMessage(),
                    )
                }
            }
        }
    }

    fun addToCollection(collectionId: String, wallpaper: Wallpaper) {
        val target = _state.value.collectionItems.firstOrNull { it.id == collectionId } ?: return
        if (wallpaper.id in target.wallpaperIds) {
            _state.update { it.copy(message = "Ya está en ${target.name}.") }
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
                    message = "Guardado en ${target.name}.",
                )
            }
            return
        }
        if (_state.value.session == null) {
            _state.update { it.copy(message = "Inicia sesión para guardar en colecciones.") }
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
                        message = "Guardado en ${target.name}.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        message = error.userMessage(),
                    )
                }
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
                it.copy(collectionItems = updated, message = "Fondo quitado de la colección.")
            }
            return
        }
        if (_state.value.session == null) {
            _state.update { it.copy(message = "Inicia sesión para editar colecciones.") }
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
                        message = "Fondo quitado de la colección.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        message = error.userMessage(),
                    )
                }
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
                    message = "Colección \"${target.name}\" eliminada.",
                )
            }
            return
        }
        if (_state.value.session == null) {
            _state.update { it.copy(message = "Inicia sesión para borrar colecciones.") }
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
                        message = "Colección \"${target.name}\" eliminada.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        message = error.userMessage(),
                    )
                }
            }
        }
    }

    fun download(wallpaper: Wallpaper) {
        val known = prefs.downloadedUri(wallpaper.id)
        if (known != null) {
            val uri = Uri.parse(known)
            if (WallpaperFiles.exists(getApplication(), uri)) {
                _state.update { it.copy(downloadPrompt = DownloadPrompt(wallpaper.id, uri)) }
                return
            }
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
            _state.update { it.copy(activeAction = UiAction.DOWNLOAD, actionProgress = 0) }
            try {
                val uri = WallpaperFiles.saveToGallery(getApplication(), wallpaper) { done, total ->
                    val percent = if (total > 0) {
                        ((done * 100) / total).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    _state.update { it.copy(actionProgress = percent) }
                }
                prefs.rememberDownload(wallpaper.id, uri.toString())
                api.incrementDownloads(wallpaper.id)
                _state.update {
                    it.copy(
                        activeAction = null,
                        actionProgress = null,
                        downloadedIds = it.downloadedIds + wallpaper.id,
                        message = "Guardado en Galería, carpeta Pictures/DannX.",
                        messageAction = MessageAction(label = "Abrir", uri = uri),
                    )
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(activeAction = null, actionProgress = null) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        activeAction = null,
                        actionProgress = null,
                        message = error.userMessage(),
                    )
                }
            }
        }
    }

    fun setWallpaper(wallpaper: Wallpaper, target: WallpaperTarget) {
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _state.update { it.copy(activeAction = UiAction.APPLY, actionProgress = null) }
            try {
                val applied = WallpaperFiles.setAsWallpaper(getApplication(), wallpaper, target)
                api.incrementDownloads(wallpaper.id)
                _state.update {
                    it.copy(
                        activeAction = null,
                        actionProgress = null,
                        message = when (applied) {
                            WallpaperTarget.SYSTEM -> "Listo, tu fondo de pantalla cambió."
                            WallpaperTarget.LOCK -> "Listo, cambiaste el fondo del bloqueo."
                            WallpaperTarget.BOTH -> "Listo, cambiaste el fondo de inicio y del bloqueo."
                        },
                    )
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(activeAction = null, actionProgress = null) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        activeAction = null,
                        actionProgress = null,
                        message = "No se pudo aplicar el fondo. Probá de nuevo.",
                    )
                }
            }
        }
    }

    fun cancelAction() {
        actionJob?.cancel()
        actionJob = null
        _state.update { it.copy(activeAction = null, actionProgress = null) }
    }

    fun share(wallpaper: Wallpaper) {
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _state.update { it.copy(activeAction = UiAction.SHARE, actionProgress = 0) }
            try {
                val image = WallpaperFiles.prepareShare(getApplication(), wallpaper)
                WallpaperFiles.share(getApplication(), image, wallpaper.title)
                _state.update { it.copy(activeAction = null, actionProgress = null) }
            } catch (error: CancellationException) {
                _state.update { it.copy(activeAction = null, actionProgress = null) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        activeAction = null,
                        actionProgress = null,
                        message = "No se pudo preparar la imagen para compartir.",
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, messageAction = null) }
    }

    private fun restoreSession() {
        if (!api.isConfigured) return
        viewModelScope.launch {
            val session = api.refreshSession() ?: return@launch
            _state.update { it.copy(session = session) }
        }
    }

    private suspend fun loadCollectionsOrEmpty(
        fallback: List<WallpaperCollection>,
    ): List<WallpaperCollection> =
        runCatching { api.loadCollections() }.getOrElse { fallback }

    companion object {
        private const val KEY_QUERY = "query"
        private const val KEY_CATEGORY = "category"
        private const val KEY_TAG = "tag"
        private const val KEY_UPLOAD_URI = "upload_uri"

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val prefs = Prefs(application)
                MainViewModel(
                    application = application,
                    savedState = createSavedStateHandle(),
                    prefs = prefs,
                    api = WallsApi(application, prefs),
                    cache = FeedCache(application),
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
    else -> ErrorKind.UNEXPECTED
}

/**
 * Mensaje claro en español para el usuario, sin texto crudo del servidor.
 */
fun Throwable.userMessage(): String = when (kind()) {
    ErrorKind.OFFLINE -> "Sin conexión. Revisá tu internet y probá de nuevo."
    ErrorKind.SERVER -> "El servidor está having problemas. Probá en un momento."
    ErrorKind.SESSION -> "Tu sesión venció. Volvé a iniciar sesión."
    ErrorKind.UNEXPECTED -> "Algo salió mal. Intentá de nuevo."
}
