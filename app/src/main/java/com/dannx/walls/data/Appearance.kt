package com.dannx.walls.data

import android.content.Context
import androidx.core.content.edit

/** Forma de elegir entre tema claro y oscuro. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Preferencias visuales de la app. Se guardan aparte del resto para que los
 * ajustes no dependan de la sesión ni del feed.
 */
interface AppearanceStore {
    fun themeMode(): ThemeMode
    fun liteMode(): Boolean
    fun openEditorOnApply(): Boolean
    fun saveThemeMode(mode: ThemeMode)
    fun saveLiteMode(enabled: Boolean)
    fun saveOpenEditorOnApply(enabled: Boolean)
    fun reset()
}

/** Guarda las preferencias en el teléfono. */
class AppearancePrefs(context: Context) : AppearanceStore {
    private val prefs =
        context.getSharedPreferences("dannx_appearance", Context.MODE_PRIVATE)

    override fun themeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(
            prefs.getString(KEY_THEME, null) ?: ThemeMode.SYSTEM.name,
        )
    }.getOrDefault(ThemeMode.SYSTEM)

    override fun liteMode(): Boolean = prefs.getBoolean(KEY_LITE, false)

    override fun openEditorOnApply(): Boolean = prefs.getBoolean(KEY_OPEN_EDITOR, true)

    override fun saveThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
    }

    override fun saveLiteMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LITE, enabled) }
    }

    override fun saveOpenEditorOnApply(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_OPEN_EDITOR, enabled) }
    }

    override fun reset() {
        prefs.edit { clear() }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LITE = "lite_mode"
        const val KEY_OPEN_EDITOR = "open_editor_on_apply"
    }
}

/** Versión en memoria: sirve para pruebas y vistas previas. */
class InMemoryAppearanceStore(
    private var theme: ThemeMode = ThemeMode.SYSTEM,
    private var lite: Boolean = false,
    private var openEditor: Boolean = true,
) : AppearanceStore {
    override fun themeMode(): ThemeMode = theme

    override fun liteMode(): Boolean = lite

    override fun openEditorOnApply(): Boolean = openEditor

    override fun saveThemeMode(mode: ThemeMode) {
        theme = mode
    }

    override fun saveLiteMode(enabled: Boolean) {
        lite = enabled
    }

    override fun saveOpenEditorOnApply(enabled: Boolean) {
        openEditor = enabled
    }

    override fun reset() {
        theme = ThemeMode.SYSTEM
        lite = false
        openEditor = true
    }
}
