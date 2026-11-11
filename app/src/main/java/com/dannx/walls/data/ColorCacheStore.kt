package com.dannx.walls.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Guarda los colores ya resueltos para no volver a analizar las miniaturas en
 * cada inicio. Un valor nulo significa "se intentó y no se pudo resolver".
 */
interface ColorCacheStore {
    fun read(): Map<String, WallpaperColor?>
    fun write(colors: Map<String, WallpaperColor?>)
}

object NoColorCacheStore : ColorCacheStore {
    override fun read(): Map<String, WallpaperColor?> = emptyMap()
    override fun write(colors: Map<String, WallpaperColor?>) = Unit
}

class ColorCachePrefs(context: Context) : ColorCacheStore {
    private val prefs = context.getSharedPreferences("dannx_colors", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): Map<String, WallpaperColor?> {
        val raw = prefs.getString(KEY_COLORS, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(raw).mapNotNull { (id, value) ->
                if (value == NONE) {
                    id to null
                } else {
                    WallpaperColor.fromKey(value)?.let { id to it }
                }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    override fun write(colors: Map<String, WallpaperColor?>) {
        val payload = LinkedHashMap<String, String>()
        colors.entries.toList().takeLast(MAX_COLORS).forEach { (id, color) ->
            payload[id] = color?.key ?: NONE
        }
        prefs.edit { putString(KEY_COLORS, json.encodeToString(payload)) }
    }

    private companion object {
        const val KEY_COLORS = "wallpaper_colors"
        const val NONE = "none"
        const val MAX_COLORS = 800
    }
}
