package com.dannx.walls

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.dannx.walls.data.AppCacheStore
import com.dannx.walls.data.LocalStore
import com.dannx.walls.data.AppearanceStore
import com.dannx.walls.data.ThemeMode
import com.dannx.walls.R
import com.dannx.walls.util.StringResolver
import com.dannx.walls.util.get

/**
 * Ajustes de apariencia y mantenimiento de caché. Es el primer bloque extraído
 * de MainViewModel (docs/designs/main-viewmodel-split.md): solo toca el store
 * de apariencia, las preferencias y la caché, sin depender del feed.
 *
 * MainViewModel sigue exponiendo la misma API pública y delega acá.
 */
class AppearanceController(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<UiState>,
    private val prefs: LocalStore,
    private val appearance: AppearanceStore,
    private val appCache: AppCacheStore,
    private val strings: StringResolver,
) {
    /** Cambia entre tema del sistema, claro u oscuro. */
    fun setThemeMode(mode: ThemeMode) {
        appearance.saveThemeMode(mode)
        state.update { it.copy(themeMode = mode) }
    }

    /** Activa o desactiva las animaciones más pesadas. */
    fun setLiteMode(enabled: Boolean) {
        appearance.saveLiteMode(enabled)
        state.update { it.copy(liteMode = enabled) }
    }

    /** Define si el botón Aplicar abre primero el editor de fondos. */
    fun setOpenEditorOnApply(enabled: Boolean) {
        appearance.saveOpenEditorOnApply(enabled)
        state.update { it.copy(openEditorOnApply = enabled) }
    }

    /** Vuelve la apariencia a los valores de fábrica. */
    fun resetAppearance() {
        appearance.reset()
        state.update {
            it.copy(
                themeMode = ThemeMode.SYSTEM,
                liteMode = false,
                openEditorOnApply = true,
                message = strings.get(R.string.msg_settings_reset),
                messageAction = null,
            )
        }
    }

    /** Recalcula el espacio que ocupa la caché local. */
    fun refreshCacheSize() {
        scope.launch {
            val size = readCacheSize()
            state.update { it.copy(cacheSizeBytes = size) }
        }
    }

    /** Borra la copia local del feed y las imágenes descargadas. */
    fun clearCache() {
        state.update { it.copy(cacheSizeBytes = 0L) }
        scope.launch {
            try {
                appCache.clear()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Si algo no se pudo borrar, el tamaño real se muestra abajo.
            }
            val size = readCacheSize()
            state.update {
                it.copy(
                    cacheSizeBytes = size,
                    message = strings.get(R.string.msg_cache_cleared),
                    messageAction = null,
                )
            }
        }
    }

    private suspend fun readCacheSize(): Long = try {
        appCache.sizeBytes()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        0L
    }

    /**
     * Marca la bienvenida como vista; [UiState.onboardingDone] abre la app.
     */
    fun completeOnboarding() {
        prefs.completeOnboarding()
        state.update { it.copy(onboardingDone = true) }
    }
}
