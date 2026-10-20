package com.dannx.walls.data

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("dannx", Context.MODE_PRIVATE)

    fun favorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun toggleFavorite(id: String): Set<String> {
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

    fun session(): AdminSession? {
        val email = adminEmail ?: return null
        val token = accessToken ?: return null
        return AdminSession(email, token)
    }

    fun saveSession(email: String, access: String?, refresh: String?) {
        adminEmail = email
        accessToken = access
        refreshToken = refresh
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ADMIN_EMAIL)
            .apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ADMIN_EMAIL = "admin_email"
    }
}
