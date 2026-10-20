package com.dannx.walls

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dannx.walls.data.AdminSession
import com.dannx.walls.data.Prefs
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallsApi
import com.dannx.walls.data.filterWallpapers
import com.dannx.walls.util.WallpaperFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val wallpapers: List<Wallpaper> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val favorites: Set<String> = emptySet(),
    val session: AdminSession? = null,
    val isDemo: Boolean = true,
    val uploading: Boolean = false,
    val uploadSuccessCount: Int = 0,
    val authBusy: Boolean = false,
    val message: String? = null,
) {
    val filtered: List<Wallpaper>
        get() = filterWallpapers(wallpapers, query, category)

    val featured: List<Wallpaper>
        get() = wallpapers.filter { it.featured }

    val favoriteWallpapers: List<Wallpaper>
        get() = wallpapers.filter { it.id in favorites }
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
                _state.update {
                    it.copy(
                        loading = false,
                        wallpapers = items,
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
                _state.update {
                    it.copy(
                        authBusy = false,
                        session = session,
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
        _state.update { it.copy(session = null, message = "Sesión cerrada.") }
    }

    fun uploadWallpaper(uri: Uri, title: String, category: String) {
        viewModelScope.launch {
            _state.update { it.copy(uploading = true) }
            try {
                val created = api.uploadWallpaper(uri, title, category)
                _state.update {
                    it.copy(
                        uploading = false,
                        wallpapers = listOf(created) + it.wallpapers,
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
                _state.update { it.copy(session = refreshed) }
            } else {
                _state.update { it.copy(session = null) }
            }
        }
    }

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
