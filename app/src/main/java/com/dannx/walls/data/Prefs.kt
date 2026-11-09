package com.dannx.walls.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Prefs(context: Context) : LocalStore {
    private val prefs = context.getSharedPreferences("dannx", Context.MODE_PRIVATE)
    private val sessionPrefs =
        context.getSharedPreferences("dannx_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        migrateSession()
    }

    /**
     * Mueve los tokens que versiones anteriores guardaban en "dannx" al archivo
     * "dannx_session", que queda excluido de los backups del dispositivo.
     */
    private fun migrateSession() {
        val legacyAccess = prefs.getString(KEY_ACCESS_TOKEN, null)
        val legacyRefresh = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (legacyAccess == null && legacyRefresh == null) return
        sessionPrefs.edit {
            putString(KEY_ACCESS_TOKEN, legacyAccess)
            putString(KEY_REFRESH_TOKEN, legacyRefresh)
            putString(KEY_ADMIN_EMAIL, prefs.getString(KEY_ADMIN_EMAIL, null))
            putString(KEY_USER_ID, prefs.getString(KEY_USER_ID, null))
        }
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_ADMIN_EMAIL)
            remove(KEY_USER_ID)
        }
    }

    override fun favorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    override fun toggleFavorite(id: String): Set<String> {
        val updated = favorites().toMutableSet()
        if (!updated.add(id)) {
            updated.remove(id)
        }
        prefs.edit { putStringSet(KEY_FAVORITES, updated) }
        return updated
    }

    override fun recentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())
    }

    override fun rememberSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val updated = (listOf(normalized) + recentSearches()
            .filterNot { it.equals(normalized, ignoreCase = true) })
            .take(MAX_RECENT_SEARCHES)
        prefs.edit { putString(KEY_RECENT_SEARCHES, json.encodeToString(updated)) }
    }

    override fun clearRecentSearches() {
        prefs.edit { remove(KEY_RECENT_SEARCHES) }
    }

    override fun appSettings(): AppSettings = AppSettings(
        tagline = prefs.getString(KEY_APP_TAGLINE, null).orEmpty(),
        bannerText = prefs.getString(KEY_APP_BANNER_TEXT, null).orEmpty(),
        bannerEnabled = prefs.getBoolean(KEY_APP_BANNER_ENABLED, false),
    )

    override fun saveAppSettings(settings: AppSettings) {
        prefs.edit {
            putString(KEY_APP_TAGLINE, settings.tagline)
            putString(KEY_APP_BANNER_TEXT, settings.bannerText)
            putBoolean(KEY_APP_BANNER_ENABLED, settings.bannerEnabled)
        }
    }

    var accessToken: String?
        get() = sessionPrefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = sessionPrefs.edit { putString(KEY_ACCESS_TOKEN, value) }

    var refreshToken: String?
        get() = sessionPrefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = sessionPrefs.edit { putString(KEY_REFRESH_TOKEN, value) }

    var adminEmail: String?
        get() = sessionPrefs.getString(KEY_ADMIN_EMAIL, null)
        set(value) = sessionPrefs.edit { putString(KEY_ADMIN_EMAIL, value) }

    var userId: String?
        get() = sessionPrefs.getString(KEY_USER_ID, null)
        set(value) = sessionPrefs.edit { putString(KEY_USER_ID, value) }

    override fun session(): AdminSession? {
        val email = adminEmail ?: return null
        val token = accessToken ?: return null
        return AdminSession(email, token, userId.orEmpty())
    }

    override fun saveSession(
        email: String,
        access: String?,
        refresh: String?,
        id: String?,
    ) {
        // Una sola transacción para que una interrupción no deje una sesión
        // mezclada entre dos inicios de sesión.
        sessionPrefs.edit {
            putString(KEY_ADMIN_EMAIL, email)
            if (access == null) remove(KEY_ACCESS_TOKEN) else putString(KEY_ACCESS_TOKEN, access)
            if (refresh == null) remove(KEY_REFRESH_TOKEN) else putString(KEY_REFRESH_TOKEN, refresh)
            if (id == null) remove(KEY_USER_ID) else putString(KEY_USER_ID, id)
        }
    }

    override fun clearSession() {
        sessionPrefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_ADMIN_EMAIL)
            remove(KEY_USER_ID)
        }
    }

    override fun localCollections(): List<WallpaperCollection> {
        val raw = prefs.getString(KEY_LOCAL_COLLECTIONS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<WallpaperCollection>>(raw)
        }.getOrDefault(emptyList())
    }

    override fun saveLocalCollections(items: List<WallpaperCollection>) {
        prefs.edit { putString(KEY_LOCAL_COLLECTIONS, json.encodeToString(items)) }
    }

    override fun downloadedUri(wallpaperId: String): String? =
        runCatching { downloads()[wallpaperId] }.getOrNull()

    override fun downloadedIds(): Set<String> =
        runCatching { downloads().keys.toSet() }.getOrDefault(emptySet())

    override fun rememberDownload(wallpaperId: String, uri: String) {
        val updated = downloads().toMutableMap().apply { put(wallpaperId, uri) }
        prefs.edit { putString(KEY_DOWNLOADS, json.encodeToString(updated)) }
    }

    override fun onboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    override fun completeOnboarding() {
        prefs.edit { putBoolean(KEY_ONBOARDING_DONE, true) }
    }

    private fun downloads(): Map<String, String> {
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(raw)
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_APP_TAGLINE = "app_tagline"
        const val KEY_APP_BANNER_TEXT = "app_banner_text"
        const val KEY_APP_BANNER_ENABLED = "app_banner_enabled"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ADMIN_EMAIL = "admin_email"
        const val KEY_USER_ID = "user_id"
        const val KEY_LOCAL_COLLECTIONS = "local_collections"
        const val KEY_DOWNLOADS = "downloads"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val MAX_RECENT_SEARCHES = 8
    }
}
