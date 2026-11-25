package com.dannx.walls.data

import android.content.Context
import androidx.core.content.edit

/** Forma de elegir entre tema claro y oscuro. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Cuánto se mueve el fondo animado al deslizar entre pantallas. */
enum class LiveMotion { SUBTLE, NORMAL, STILL }

/**
 * Preferencias visuales de la app. Se guardan aparte del resto para que los
 * ajustes no dependan de la sesión ni del feed.
 */
interface AppearanceStore {
    fun themeMode(): ThemeMode
    fun liteMode(): Boolean
    fun liveMotion(): LiveMotion
    fun openEditorOnApply(): Boolean
    fun colorSeed(): String
    fun paletteStyle(): ColorPaletteStyle
    fun contrastLevel(): ColorContrast
    fun amoled(): Boolean
    fun saveThemeMode(mode: ThemeMode)
    fun saveLiteMode(enabled: Boolean)
    fun saveLiveMotion(motion: LiveMotion)
    fun saveOpenEditorOnApply(enabled: Boolean)
    fun saveColorSeed(seed: String)
    fun savePaletteStyle(style: ColorPaletteStyle)
    fun saveContrastLevel(level: ColorContrast)
    fun saveAmoled(enabled: Boolean)
    fun reset()
}

/** Guarda las preferencias en el teléfono. */
class AppearancePrefs(context: Context) : AppearanceStore {
    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun themeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(
            prefs.getString(KEY_THEME, null) ?: ThemeMode.SYSTEM.name,
        )
    }.getOrDefault(ThemeMode.SYSTEM)

    override fun liteMode(): Boolean = prefs.getBoolean(KEY_LITE, false)

    override fun liveMotion(): LiveMotion = runCatching {
        LiveMotion.valueOf(
            prefs.getString(KEY_LIVE_MOTION, null) ?: LiveMotion.NORMAL.name,
        )
    }.getOrDefault(LiveMotion.NORMAL)

    override fun openEditorOnApply(): Boolean = prefs.getBoolean(KEY_OPEN_EDITOR, true)

    override fun colorSeed(): String = prefs.getString(KEY_COLOR_SEED, null).orEmpty()

    override fun paletteStyle(): ColorPaletteStyle =
        ColorPaletteStyle.fromName(prefs.getString(KEY_PALETTE_STYLE, null))

    override fun contrastLevel(): ColorContrast =
        ColorContrast.fromName(prefs.getString(KEY_CONTRAST, null))

    override fun amoled(): Boolean = prefs.getBoolean(KEY_AMOLED, false)

    override fun saveThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
    }

    override fun saveLiteMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LITE, enabled) }
    }

    override fun saveLiveMotion(motion: LiveMotion) {
        prefs.edit { putString(KEY_LIVE_MOTION, motion.name) }
    }

    override fun saveOpenEditorOnApply(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_OPEN_EDITOR, enabled) }
    }

    override fun saveColorSeed(seed: String) {
        prefs.edit { putString(KEY_COLOR_SEED, seed) }
    }

    override fun savePaletteStyle(style: ColorPaletteStyle) {
        prefs.edit { putString(KEY_PALETTE_STYLE, style.name) }
    }

    override fun saveContrastLevel(level: ColorContrast) {
        prefs.edit { putString(KEY_CONTRAST, level.name) }
    }

    override fun saveAmoled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AMOLED, enabled) }
    }

    override fun reset() {
        prefs.edit { clear() }
    }

    companion object {
        /** El servicio del fondo animado lee estas mismas preferencias. */
        const val PREFS_NAME = "dannx_appearance"
        const val KEY_LIVE_MOTION = "live_motion"
        const val KEY_LITE = "lite_mode"

        private const val KEY_THEME = "theme_mode"
        private const val KEY_OPEN_EDITOR = "open_editor_on_apply"
        private const val KEY_COLOR_SEED = "color_seed"
        private const val KEY_PALETTE_STYLE = "palette_style"
        private const val KEY_CONTRAST = "contrast_level"
        private const val KEY_AMOLED = "amoled"
    }
}

/** Versión en memoria: sirve para pruebas y vistas previas. */
class InMemoryAppearanceStore(
    private var theme: ThemeMode = ThemeMode.SYSTEM,
    private var lite: Boolean = false,
    private var openEditor: Boolean = true,
    private var seed: String = WALLPAPER_COLOR_SEED,
    private var style: ColorPaletteStyle = ColorPaletteStyle.TONAL_SPOT,
    private var contrast: ColorContrast = ColorContrast.STANDARD,
    private var pureBlack: Boolean = false,
    private var motion: LiveMotion = LiveMotion.NORMAL,
) : AppearanceStore {
    override fun themeMode(): ThemeMode = theme

    override fun liteMode(): Boolean = lite

    override fun liveMotion(): LiveMotion = motion

    override fun openEditorOnApply(): Boolean = openEditor

    override fun colorSeed(): String = seed

    override fun paletteStyle(): ColorPaletteStyle = style

    override fun contrastLevel(): ColorContrast = contrast

    override fun amoled(): Boolean = pureBlack

    override fun saveThemeMode(mode: ThemeMode) {
        theme = mode
    }

    override fun saveLiteMode(enabled: Boolean) {
        lite = enabled
    }

    override fun saveLiveMotion(motion: LiveMotion) {
        this.motion = motion
    }

    override fun saveOpenEditorOnApply(enabled: Boolean) {
        openEditor = enabled
    }

    override fun saveColorSeed(seed: String) {
        this.seed = seed
    }

    override fun savePaletteStyle(style: ColorPaletteStyle) {
        this.style = style
    }

    override fun saveContrastLevel(level: ColorContrast) {
        contrast = level
    }

    override fun saveAmoled(enabled: Boolean) {
        pureBlack = enabled
    }

    override fun reset() {
        theme = ThemeMode.SYSTEM
        lite = false
        openEditor = true
        seed = WALLPAPER_COLOR_SEED
        style = ColorPaletteStyle.TONAL_SPOT
        contrast = ColorContrast.STANDARD
        pureBlack = false
        motion = LiveMotion.NORMAL
    }
}
