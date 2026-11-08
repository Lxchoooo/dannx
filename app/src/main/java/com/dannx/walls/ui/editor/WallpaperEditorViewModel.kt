package com.dannx.walls.ui.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.util.WallpaperFiles
import com.dannx.walls.util.WallpaperTarget
import com.dannx.walls.util.wallpaperTargetSize
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class EditorUiState(
    val loading: Boolean = true,
    val loadProgress: Int? = null,
    val loadError: String? = null,
    val preview: Bitmap? = null,
    val blurredPreview: Bitmap? = null,
    val grain: Bitmap? = null,
    val thumbs: List<EditorFilterThumb> = emptyList(),
    val settings: EditorSettings = EditorSettings(),
    val showOriginal: Boolean = false,
    val applying: Boolean = false,
    val applyProgress: Int = 0,
    val applyError: String? = null,
    val appliedTarget: WallpaperTarget? = null,
)

/**
 * Mantiene los bitmaps fuera de la composición y hace el trabajo pesado en
 * segundo plano. La preview usa una copia reducida; el resultado final se
 * genera una sola vez al tocar Aplicar.
 */
internal class WallpaperEditorViewModel(
    application: Application,
    private val wallpaper: Wallpaper,
) : ViewModel() {
    private val appContext = application.applicationContext
    private val files = WallpaperFiles(appContext)
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var blurJob: Job? = null
    private var applyJob: Job? = null
    private var sourceFile: File? = null

    init {
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null, loadProgress = null) }
            try {
                val file = ensureSourceFile()
                val preview = withContext(Dispatchers.Default) {
                    runCatching { EditorImage.decodePreview(file) }
                        .getOrElse {
                            file.delete()
                            throw it
                        }
                }
                val grain = withContext(Dispatchers.Default) { EditorImage.createGrainTile() }
                val thumbs = withContext(Dispatchers.Default) {
                    EditorImage.createFilterThumbs(preview)
                }
                sourceFile = file
                _state.update {
                    it.copy(
                        loading = false,
                        loadProgress = null,
                        preview = preview,
                        grain = grain,
                        thumbs = thumbs,
                    )
                }
                scheduleBlurPreview()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadProgress = null,
                        loadError = "No se pudo abrir la imagen. Revisá tu conexión y probá de nuevo.",
                    )
                }
            }
        }
    }

    fun updateCrop(crop: EditorCrop) {
        _state.update { current ->
            current.copy(settings = current.settings.copy(crop = crop))
        }
    }

    fun updateAdjustments(adjustments: EditorAdjustments) {
        _state.update { current ->
            current.copy(settings = current.settings.copy(adjustments = adjustments))
        }
    }

    fun selectFilter(preset: FilterPreset) {
        _state.update { current ->
            val intensity = when {
                preset.id == "original" -> 0f
                current.settings.filterIntensity > 0f -> current.settings.filterIntensity
                else -> 0.65f
            }
            current.copy(
                settings = current.settings.copy(
                    filterId = preset.id,
                    filterIntensity = intensity,
                ),
            )
        }
    }

    fun updateFilterIntensity(value: Float) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    filterIntensity = value.coerceIn(0f, 1f),
                ),
            )
        }
    }

    fun updateBlur(value: Float) {
        _state.update { current ->
            current.copy(settings = current.settings.copy(blur = value.coerceIn(0f, 1f)))
        }
        scheduleBlurPreview()
    }

    fun setShowOriginal(show: Boolean) {
        _state.update { it.copy(showOriginal = show) }
    }

    fun reset() {
        _state.update {
            it.copy(
                settings = EditorSettings(),
                showOriginal = false,
                applyError = null,
            )
        }
        scheduleBlurPreview()
    }

    fun apply(target: WallpaperTarget) {
        if (applyJob?.isActive == true) return
        val current = _state.value
        if (current.preview == null || current.loading) return
        applyJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    applying = true,
                    applyProgress = 0,
                    applyError = null,
                )
            }
            var finalBitmap: Bitmap? = null
            try {
                val file = sourceFile ?: ensureSourceFile()
                val size = wallpaperTargetSize(appContext)
                val rendered = withContext(Dispatchers.Default) {
                    EditorImage.renderFinal(
                        file = file,
                        targetWidth = size.x,
                        targetHeight = size.y,
                        settings = _state.value.settings,
                    ) { progress ->
                        _state.update { it.copy(applyProgress = progress) }
                    }
                }
                finalBitmap = rendered
                _state.update { it.copy(applyProgress = 96) }
                val applied = files.applyEditedWallpaper(rendered, target)
                rendered.recycle()
                finalBitmap = null
                _state.update {
                    it.copy(
                        applying = false,
                        applyProgress = 100,
                        appliedTarget = applied,
                    )
                }
            } catch (error: CancellationException) {
                finalBitmap?.recycle()
                _state.update { it.copy(applying = false, applyProgress = 0) }
                throw error
            } catch (error: Exception) {
                finalBitmap?.recycle()
                _state.update {
                    it.copy(
                        applying = false,
                        applyProgress = 0,
                        applyError = "No se pudo aplicar el fondo. Probá de nuevo.",
                    )
                }
            }
        }
    }

    fun cancelApply() {
        applyJob?.cancel()
        applyJob = null
        _state.update { it.copy(applying = false, applyProgress = 0) }
    }

    private suspend fun ensureSourceFile(): File {
        val folder = File(appContext.cacheDir, "editor").apply { mkdirs() }
        val safeId = wallpaper.id
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
        val file = File(folder, "$safeId.original")
        if (file.exists() && file.length() > 0L) return file
        files.downloadToFile(wallpaper, file) { downloaded, total ->
            val progress = if (total > 0L) {
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            } else {
                null
            }
            _state.update { it.copy(loadProgress = progress) }
        }
        return file
    }

    private fun scheduleBlurPreview() {
        blurJob?.cancel()
        val preview = _state.value.preview
        val blur = _state.value.settings.blur
        if (preview == null || blur <= 0f) {
            _state.update { it.copy(blurredPreview = null) }
            return
        }
        blurJob = viewModelScope.launch {
            delay(120)
            val blurred = withContext(Dispatchers.Default) {
                runCatching { EditorImage.createBlurred(preview) }.getOrNull()
            } ?: return@launch
            if (_state.value.settings.blur > 0f) {
                _state.update { it.copy(blurredPreview = blurred) }
            } else {
                blurred.recycle()
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        blurJob?.cancel()
        applyJob?.cancel()
        val hasActiveJobs = loadJob?.isActive == true ||
            blurJob?.isActive == true ||
            applyJob?.isActive == true
        if (!hasActiveJobs) {
            val current = _state.value
            current.preview?.recycle()
            current.blurredPreview?.recycle()
            current.grain?.recycle()
            current.thumbs.forEach { it.bitmap.recycle() }
        }
        super.onCleared()
    }

    companion object {
        fun factory(
            application: Application,
            wallpaper: Wallpaper,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (!modelClass.isAssignableFrom(WallpaperEditorViewModel::class.java)) {
                    throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
                }
                @Suppress("UNCHECKED_CAST")
                return WallpaperEditorViewModel(application, wallpaper) as T
            }
        }
    }
}
