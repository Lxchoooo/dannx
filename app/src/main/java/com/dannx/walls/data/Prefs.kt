package com.dannx.walls.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Prefs(context: Context) : LocalStore {
    private val prefs = context.getSharedPreferences("dannx", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun favorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    override fun toggleFavorite(id: String): Set<String> {
        val updated = favorites().toMutableSet()
        if (!updated.add(id)) {
            updated.remove(id)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
        return updated
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var adminEmail: String?
        get() = prefs.getString(KEY_ADMIN_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_ADMIN_EMAIL, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    override fun session(): AdminSession? {
        val email = adminEmail ?: return null
        val token = accessToken ?: return null
        return AdminSession(email, token, userId.orEmpty())
    }

    override fun saveSession(
        email: String,
        access: String?,
        refresh: String?,
        id: String? = null,
    ) {
        adminEmail = email
        accessToken = access
        refreshToken = refresh
        userId = id
    }

    override fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ADMIN_EMAIL)
            .remove(KEY_USER_ID)
            .apply()
    }

    override fun localCollections(): List<WallpaperCollection> {
        val raw = prefs.getString(KEY_LOCAL_COLLECTIONS, null)
            ?: return DemoWallpapers.collections
        return runCatching {
            json.decodeFromString<List<WallpaperCollection>>(raw)
        }.getOrElse { DemoWallpapers.collections }
    }

    override fun saveLocalCollections(items: List<WallpaperCollection>) {
        prefs.edit()
            .putString(KEY_LOCAL_COLLECTIONS, json.encodeToString(items))
            .apply()
    }

    override fun downloadedUri(wallpaperId: String): String? =
        runCatching { downloads()[wallpaperId] }.getOrNull()

    override fun downloadedIds(): Set<String> =
        runCatching { downloads().keys.toSet() }.getOrDefault(emptySet())

    override fun rememberDownload(wallpaperId: String, uri: String) {
        val updated = downloads().toMutableMap().apply { put(wallpaperId, uri) }
        prefs.edit()
            .putString(KEY_DOWNLOADS, json.encodeToString(updated))
            .apply()
    }

    private fun downloads(): Map<String, String> {
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(raw)
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ADMIN_EMAIL = "admin_email"
        const val KEY_USER_ID = "user_id"
        const val KEY_LOCAL_COLLECTIONS = "local_collections"
        const val KEY_DOWNLOADS = "downloads"
    }
}
