package com.dannx.walls.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dannx.walls.R
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.util.StaticWallpaperLayout
import com.dannx.walls.util.WallpaperFiles
import com.dannx.walls.util.WallpaperTarget
import com.dannx.walls.util.staticWallpaperLayout
import com.dannx.walls.util.wallpaperTargetSize
import com.dannx.walls.wallpaper.LiveWallpaperStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
internal data class EditorUiState(
    val loading: Boolean = true,
    val loadProgress: Int? = null,
    val loadError: String? = null,
    val preview: Bitmap? = null,
    val blurLevels: List<Bitmap> = emptyList(),
    val grain: Bitmap? = null,
    val thumbs: List<EditorFilterThumb> = emptyList(),
    val settings: EditorSettings = EditorSettings(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showOriginal: Boolean = false,
    val applying: Boolean = false,
    val applyProgress: Int = 0,
    val applyError: String? = null,
    val appliedTarget: WallpaperTarget? = null,
    val liveReady: Boolean = false,
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
    private var blurLevelsJob: Job? = null
    private var applyJob: Job? = null
    private var sourceFile: File? = null

    // Historial liviano: solo valores de EditorSettings, nunca bitmaps.
    private val undoStack = ArrayDeque<EditorSettings>()
    private val redoStack = ArrayDeque<EditorSettings>()
    private var lastHistoryKey: String? = null
    private var lastHistoryAt = 0L

    init {
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null, loadProgress = null) }
            var preview: Bitmap? = null
            var grain: Bitmap? = null
            var thumbs: List<EditorFilterThumb> = emptyList()
            try {
                val file = ensureSourceFile()
                val decoded = withContext(Dispatchers.Default) {
                    runCatching { EditorImage.decodePreview(file) }
                        .getOrElse {
                            file.delete()
                            throw it
                        }
                }
                preview = decoded
                grain = withContext(Dispatchers.Default) { EditorImage.createGrainTile() }
                thumbs = withContext(Dispatchers.Default) {
                    EditorImage.createFilterThumbs(decoded)
                }
                sourceFile = file
                _state.update {
                    it.copy(
                        loading = false,
                        loadProgress = null,
                        preview = decoded,
                        blurLevels = emptyList(),
                        grain = grain,
                        thumbs = thumbs,
                    )
                }
                // El estado ya es dueño de los bitmaps; si se cancela después,
                // no hay que reciclarlos desde este bloque.
                preview = null
                grain = null
                thumbs = emptyList()
                ensureBlurLevels()
            } catch (error: CancellationException) {
                recycle(preview, grain, thumbs)
                throw error
            } catch (error: OutOfMemoryError) {
                recycle(preview, grain, thumbs)
                _state.update {
                    it.copy(
                        loading = false,
                        loadProgress = null,
                        loadError = appContext.getString(R.string.editor_error_memory),
                    )
                }
            } catch (error: Exception) {
                recycle(preview, grain, thumbs)
                _state.update {
                    it.copy(
                        loading = false,
                        loadProgress = null,
                        loadError = appContext.getString(R.string.editor_error_open),
                    )
                }
            }
        }
    }

    private fun recycle(
        preview: Bitmap?,
        grain: Bitmap?,
        thumbs: List<EditorFilterThumb>,
    ) {
        preview?.recycle()
        grain?.recycle()
        thumbs.forEach { it.bitmap.recycle() }
    }

    fun updateCrop(crop: EditorCrop) {
        applySettings("crop") { it.copy(crop = crop) }
    }

    fun updateAdjustments(adjustments: EditorAdjustments) {
        applySettings("adjust") { it.copy(adjustments = adjustments) }
    }

    fun selectFilter(preset: FilterPreset) {
        applySettings("filter:${preset.id}") { settings ->
            val intensity = when {
                preset.id == "original" -> 0f
                settings.filterIntensity > 0f -> settings.filterIntensity
                else -> 0.65f
            }
            settings.copy(
                filterId = preset.id,
                filterIntensity = intensity,
            )
        }
    }

    fun updateFilterIntensity(value: Float) {
        applySettings("intensity") { it.copy(filterIntensity = value.coerceIn(0f, 1f)) }
    }

    fun updateBlur(value: Float) {
        applySettings("blur") { it.copy(blur = value.coerceIn(0f, 1f)) }
        // La pila se calcula una sola vez: mover el slider ya no hace trabajo.
        ensureBlurLevels()
    }

    fun setShowOriginal(show: Boolean) {
        _state.update { it.copy(showOriginal = show) }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        val current = _state.value
        redoStack.addLast(current.settings)
        resetHistoryMerge()
        _state.value = current.copy(
            settings = previous,
            canUndo = undoStack.isNotEmpty(),
            canRedo = true,
            applyError = null,
        )
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        val current = _state.value
        if (undoStack.size >= MAX_HISTORY) undoStack.removeFirst()
        undoStack.addLast(current.settings)
        resetHistoryMerge()
        _state.value = current.copy(
            settings = next,
            canUndo = true,
            canRedo = redoStack.isNotEmpty(),
            applyError = null,
        )
    }

    fun reset() {
        applySettings("reset") { EditorSettings() }
        _state.update {
            it.copy(
                showOriginal = false,
                applyError = null,
            )
        }
    }

    /**
     * Aplica un cambio de ajustes y guarda el estado anterior para deshacer.
     * Los cambios seguidos del mismo tipo (arrastrar un slider, hacer zoom)
     * se agrupan en una sola entrada para no llenar el historial.
     */
    private fun applySettings(
        historyKey: String,
        transform: (EditorSettings) -> EditorSettings,
    ) {
        val current = _state.value
        val next = transform(current.settings)
        if (next == current.settings) return
        val now = SystemClock.elapsedRealtime()
        val merge = historyKey == lastHistoryKey && now - lastHistoryAt < HISTORY_MERGE_MS
        if (!merge) {
            if (undoStack.size >= MAX_HISTORY) undoStack.removeFirst()
            undoStack.addLast(current.settings)
        }
        redoStack.clear()
        lastHistoryKey = historyKey
        lastHistoryAt = now
        _state.value = current.copy(
            settings = next,
            canUndo = true,
            canRedo = false,
        )
    }

    private fun resetHistoryMerge() {
        lastHistoryKey = null
        lastHistoryAt = 0L
    }

    fun apply(target: WallpaperTarget) {
        launchApply(live = false) { rendered, layout ->
            val applied = files.applyEditedWallpaper(rendered, target, layout?.cropHint())
            _state.update { it.copy(appliedTarget = applied) }
        }
    }

    /**
     * Deja la foto lista para [com.dannx.walls.wallpaper.DannXWallpaperService].
     * El selector del sistema se abre desde la pantalla, que es la única que
     * puede lanzar una actividad y comprobar después qué fondo quedó puesto.
     */
    fun applyLive() {
        launchApply(live = true) { rendered, _ ->
            withContext(Dispatchers.IO) {
                LiveWallpaperStore(appContext).save(rendered)
            }
            _state.update { it.copy(liveReady = true) }
        }
    }

    fun consumeLiveReady() {
        _state.update { it.copy(liveReady = false) }
    }

    fun reportLiveChooserError() {
        _state.update {
            it.copy(applyError = appContext.getString(R.string.editor_error_live))
        }
    }

    private fun launchApply(
        live: Boolean,
        finish: suspend (Bitmap, StaticWallpaperLayout?) -> Unit,
    ) {
        if (applyJob?.isActive == true) return
        val current = _state.value
        if (current.preview == null || current.loading) return
        val settings = current.settings
        applyJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    applying = true,
                    applyProgress = 0,
                    applyError = null,
                    liveReady = false,
                )
            }
            var finalBitmap: Bitmap? = null
            try {
                val file = sourceFile ?: ensureSourceFile()
                val screen = wallpaperTargetSize(appContext)
                // El fondo estático se guarda con margen lateral; el animado
                // ya se mueve solo y se queda con el tamaño justo de pantalla.
                val layout = if (live) null else staticWallpaperLayout(screen.x, screen.y)
                val rendered = withContext(Dispatchers.Default) {
                    EditorImage.renderFinal(
                        file = file,
                        targetWidth = layout?.width ?: screen.x,
                        targetHeight = layout?.height ?: screen.y,
                        settings = settings,
                    ) { progress ->
                        _state.update { it.copy(applyProgress = progress) }
                    }
                }
                finalBitmap = rendered
                _state.update { it.copy(applyProgress = 96) }
                finish(rendered, layout)
                rendered.recycle()
                finalBitmap = null
                _state.update {
                    it.copy(
                        applying = false,
                        applyProgress = 100,
                    )
                }
            } catch (error: CancellationException) {
                finalBitmap?.recycle()
                _state.update { it.copy(applying = false, applyProgress = 0) }
                throw error
            } catch (error: OutOfMemoryError) {
                finalBitmap?.recycle()
                _state.update {
                    it.copy(
                        applying = false,
                        applyProgress = 0,
                        applyError = appContext.getString(R.string.editor_error_memory),
                    )
                }
            } catch (error: Exception) {
                finalBitmap?.recycle()
                _state.update {
                    it.copy(
                        applying = false,
                        applyProgress = 0,
                        applyError = appContext.getString(R.string.editor_error_apply),
                    )
                }
            }
        }
    }

    fun cancelApply() {
        applyJob?.cancel()
        applyJob = null
        _state.update { it.copy(applying = false, applyProgress = 0, liveReady = false) }
    }

    private suspend fun ensureSourceFile(): File {
        val folder = File(appContext.cacheDir, "editor").apply { mkdirs() }
        // La URL entra en la clave: si se reemplaza la imagen, no se reutiliza
        // el archivo viejo del mismo fondo.
        val cacheKey = "${wallpaper.id}-${wallpaper.imageUrl.hashCode()}"
        val safeId = cacheKey
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(96)
        val file = File(folder, "$safeId.original")
        if (file.exists() && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            pruneSourceCache(folder, keep = file)
            return file
        }
        files.downloadToFile(wallpaper, file) { downloaded, total ->
            val progress = if (total > 0L) {
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            } else {
                null
            }
            _state.update { it.copy(loadProgress = progress) }
        }
        file.setLastModified(System.currentTimeMillis())
        pruneSourceCache(folder, keep = file)
        return file
    }

    /**
     * La caché de originales puede ocupar mucho: cada archivo es la imagen
     * completa. Se conservan solo los últimos usados.
     */
    private fun pruneSourceCache(folder: File, keep: File) {
        val files = folder.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".original") && it != keep }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_CACHED_SOURCES - 1).forEach { it.delete() }
    }

    /**
     * Construye la pila de desenfoques la primera vez que hace falta. La
     * intensidad solo elige niveles al dibujar, así que no se recalcula al
     * mover el slider.
     */
    private fun ensureBlurLevels() {
        if (_state.value.settings.blur <= 0f) return
        if (_state.value.blurLevels.isNotEmpty()) return
        if (blurLevelsJob?.isActive == true) return
        val preview = _state.value.preview ?: return
        blurLevelsJob = viewModelScope.launch {
            val levels = withContext(Dispatchers.Default) {
                runCatching { EditorImage.createBlurLevels(preview) }.getOrNull()
            }
            if (levels.isNullOrEmpty()) return@launch
            val current = _state.value
            if (current.preview !== preview ||
                current.settings.blur <= 0f ||
                current.blurLevels.isNotEmpty()
            ) {
                levels.forEach { it.recycle() }
                return@launch
            }
            _state.update { it.copy(blurLevels = levels) }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        blurLevelsJob?.cancel()
        applyJob?.cancel()
        // El estado es el único dueño de estos bitmaps: los jobs reciclan los
        // suyos si se cancelan antes de entregarlos.
        val current = _state.value
        current.preview?.recycle()
        current.blurLevels.forEach { it.recycle() }
        current.grain?.recycle()
        current.thumbs.forEach { it.bitmap.recycle() }
    }

    companion object {
        private const val MAX_CACHED_SOURCES = 3
        private const val MAX_HISTORY = 30
        private const val HISTORY_MERGE_MS = 700L

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
