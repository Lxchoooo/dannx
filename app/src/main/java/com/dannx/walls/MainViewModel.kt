package com.dannx.walls

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dannx.walls.data.AdminSession
import com.dannx.walls.data.Prefs
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.WallsApi
import com.dannx.walls.data.collectTags
import com.dannx.walls.data.filterWallpapers
import com.dannx.walls.util.WallpaperFiles
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val wallpapers: List<Wallpaper> = emptyList(),
    val collections: List<WallpaperCollection> = emptyList(),
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
            collections
        } else {
            val id = session?.userId.orEmpty()
            if (id.isBlank()) emptyList() else collections.filter { it.ownerId == id }
        }

    fun canEdit(collection: WallpaperCollection): Boolean =
        isDemo || (session != null && session.userId == collection.ownerId)

    fun wallpapersIn(collection: WallpaperCollection): List<Wallpaper> =
        collection.wallpaperIds.mapNotNull { id -> wallpapers.firstOrNull { it.id == id } }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Prefs(application)
    private val api = WallsApi(application, prefs)
    private val _state = MutableStateFlow(
        UiState(
            favorites = prefs.favorites(),
            session = prefs.session(),
            isDemo = !api.isConfigured,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        restoreSession()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            try {
                val items = api.loadWallpapers()
                val collections = loadCollectionsOrEmpty(_state.value.collections)
                _state.update {
                    it.copy(
                        loading = false,
                        wallpapers = items,
                        collections = collections,
                        isDemo = !api.isConfigured,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadError = error.message ?: "No se pudo cargar el feed.",
                    )
                }
            }
        }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun setCategory(value: String?) {
        _state.update { it.copy(category = value) }
    }

    fun setTag(value: String?) {
        _state.update { it.copy(tag = value) }
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
                val collections = loadCollectionsOrEmpty(_state.value.collections)
                _state.update {
                    it.copy(
                        authBusy = false,
                        session = session,
                        collections = collections,
                        message = "Sesión iniciada como ${session.email}.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        authBusy = false,
                        message = error.message ?: "No se pudo iniciar sesión.",
                    )
                }
            }
        }
    }

    fun signOut() {
        prefs.clearSession()
        val fallback = _state.value.collections.filter { it.isPublic }
        _state.update { it.copy(session = null, collections = fallback, message = "Sesión cerrada.") }
        viewModelScope.launch {
            val collections = loadCollectionsOrEmpty(fallback)
            _state.update { it.copy(collections = collections) }
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
                val collections = loadCollectionsOrEmpty(_state.value.collections)
                _state.update {
                    it.copy(
                        uploading = false,
                        wallpapers = listOf(created) + it.wallpapers,
                        collections = collections,
                        uploadSuccessCount = it.uploadSuccessCount + 1,
                        message = "Wallpaper publicado.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        uploading = false,
                        message = error.message ?: "No se pudo subir el wallpaper.",
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
            val updated = listOf(created) + _state.value.collections
            prefs.saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collections = updated,
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
                        collections = listOf(created) + it.collections,
                        message = "Colección \"$cleanName\" creada.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        message = error.message ?: "No se pudo crear la colección.",
                    )
                }
            }
        }
    }

    fun addToCollection(collectionId: String, wallpaper: Wallpaper) {
        val target = _state.value.collections.firstOrNull { it.id == collectionId } ?: return
        if (wallpaper.id in target.wallpaperIds) {
            _state.update { it.copy(message = "Ya está en ${target.name}.") }
            return
        }
        if (_state.value.isDemo) {
            val updated = _state.value.collections.map { collection ->
                if (collection.id == collectionId) {
                    collection.copy(wallpaperIds = collection.wallpaperIds + wallpaper.id)
                } else {
                    collection
                }
            }
            prefs.saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collections = updated,
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
                val updated = _state.value.collections.map { collection ->
                    if (collection.id == collectionId) {
                        collection.copy(wallpaperIds = collection.wallpaperIds + wallpaper.id)
                    } else {
                        collection
                    }
                }
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        collections = updated,
                        message = "Guardado en ${target.name}.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        message = error.message ?: "No se pudo guardar en la colección.",
                    )
                }
            }
        }
    }

    fun removeFromCollection(collectionId: String, wallpaperId: String) {
        if (_state.value.isDemo) {
            val updated = _state.value.collections.map { collection ->
                if (collection.id == collectionId) {
                    collection.copy(wallpaperIds = collection.wallpaperIds - wallpaperId)
                } else {
                    collection
                }
            }
            prefs.saveLocalCollections(updated)
            _state.update {
                it.copy(collections = updated, message = "Fondo quitado de la colección.")
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
                val updated = _state.value.collections.map { collection ->
                    if (collection.id == collectionId) {
                        collection.copy(wallpaperIds = collection.wallpaperIds - wallpaperId)
                    } else {
                        collection
                    }
                }
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        collections = updated,
                        message = "Fondo quitado de la colección.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        message = error.message ?: "No se pudo quitar el fondo.",
                    )
                }
            }
        }
    }

    fun deleteCollection(collectionId: String) {
        val target = _state.value.collections.firstOrNull { it.id == collectionId } ?: return
        if (_state.value.isDemo) {
            val updated = _state.value.collections.filterNot { it.id == collectionId }
            prefs.saveLocalCollections(updated)
            _state.update {
                it.copy(
                    collections = updated,
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
                        collections = it.collections.filterNot { c -> c.id == collectionId },
                        message = "Colección \"${target.name}\" eliminada.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        collectionBusy = false,
                        message = error.message ?: "No se pudo eliminar la colección.",
                    )
                }
            }
        }
    }

    fun download(wallpaper: Wallpaper) {
        runAction("Guardado en Galería, carpeta Pictures/DannX.") {
            WallpaperFiles.save(getApplication(), wallpaper)
            api.incrementDownloads(wallpaper.id)
        }
    }

    fun setWallpaper(wallpaper: Wallpaper) {
        runAction("Listo, tu fondo de pantalla cambió.") {
            WallpaperFiles.setAsWallpaper(getApplication(), wallpaper)
            api.incrementDownloads(wallpaper.id)
        }
    }

    fun share(wallpaper: Wallpaper) {
        runAction("Elige dónde compartir el fondo.") {
            val uri = WallpaperFiles.save(getApplication(), wallpaper)
            WallpaperFiles.share(getApplication(), uri, wallpaper.title)
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun restoreSession() {
        if (prefs.session() == null) return
        viewModelScope.launch {
            val refreshed = api.refreshSession()
            if (refreshed != null) {
                val collections = loadCollectionsOrEmpty(_state.value.collections)
                _state.update { it.copy(session = refreshed, collections = collections) }
            } else {
                _state.update { it.copy(session = null) }
            }
        }
    }

    private suspend fun loadCollectionsOrEmpty(
        fallback: List<WallpaperCollection>,
    ): List<WallpaperCollection> =
        runCatching { api.loadCollections() }.getOrElse { fallback }

    private fun runAction(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(message = successMessage) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(message = error.message ?: "Algo salió mal. Inténtalo de nuevo.")
                }
            }
        }
    }
}
