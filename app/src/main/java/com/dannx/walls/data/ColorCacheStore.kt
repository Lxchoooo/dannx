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

    override fun read(): Map<String, WallpaperColor?> =
        decodeColorCache(prefs.getString(KEY_COLORS, null))

    override fun write(colors: Map<String, WallpaperColor?>) {
        prefs.edit { putString(KEY_COLORS, encodeColorCache(colors)) }
    }

    private companion object {
        const val KEY_COLORS = "wallpaper_colors"
    }
}

internal const val COLOR_CACHE_LIMIT = 800

private const val COLOR_CACHE_NONE = "none"

private val colorCacheJson = Json { ignoreUnknownKeys = true }

/** Serializa los últimos colores usados; el orden del mapa marca la antigüedad. */
internal fun encodeColorCache(
    colors: Map<String, WallpaperColor?>,
    limit: Int = COLOR_CACHE_LIMIT,
): String {
    val payload = LinkedHashMap<String, String>()
    colors.entries.toList().takeLast(limit).forEach { (id, color) ->
        payload[id] = color?.key ?: COLOR_CACHE_NONE
    }
    return colorCacheJson.encodeToString(payload)
}

internal fun decodeColorCache(raw: String?): Map<String, WallpaperColor?> {
    if (raw == null) return emptyMap()
    return runCatching {
        colorCacheJson.decodeFromString<Map<String, String>>(raw).mapNotNull { (id, value) ->
            if (value == COLOR_CACHE_NONE) {
                id to null
            } else {
                WallpaperColor.fromKey(value)?.let { id to it }
            }
        }.toMap()
    }.getOrDefault(emptyMap())
}
