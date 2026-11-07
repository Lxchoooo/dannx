package com.dannx.walls.data

import android.content.Context

/** Forma de elegir entre tema claro y oscuro. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Preferencias visuales de la app. Se guardan aparte del resto para que los
 * ajustes no dependan de la sesión ni del feed.
 */
interface AppearanceStore {
    fun themeMode(): ThemeMode
    fun liteMode(): Boolean
    fun saveThemeMode(mode: ThemeMode)
    fun saveLiteMode(enabled: Boolean)
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

    override fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    override fun saveLiteMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LITE, enabled).apply()
    }

    override fun reset() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LITE = "lite_mode"
    }
}

/** Versión en memoria: sirve para pruebas y vistas previas. */
class InMemoryAppearanceStore(
    private var theme: ThemeMode = ThemeMode.SYSTEM,
    private var lite: Boolean = false,
) : AppearanceStore {
    override fun themeMode(): ThemeMode = theme

    override fun liteMode(): Boolean = lite

    override fun saveThemeMode(mode: ThemeMode) {
        theme = mode
    }

    override fun saveLiteMode(enabled: Boolean) {
        lite = enabled
    }

    override fun reset() {
        theme = ThemeMode.SYSTEM
        lite = false
    }
}
