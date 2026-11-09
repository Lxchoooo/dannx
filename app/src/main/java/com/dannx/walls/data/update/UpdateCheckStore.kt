package com.dannx.walls.data.update

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Último resultado guardado de la consulta de actualizaciones. */
@Serializable
data class CachedUpdate(
    val release: ReleaseInfo,
    val checkedAt: Long,
)

/**
 * Guarda la última release consultada para no repetir la petición a GitHub
 * dentro del mismo día. La persona puede forzar una consulta desde Ajustes.
 */
interface UpdateCheckStore {
    fun read(): CachedUpdate?
    fun write(release: ReleaseInfo, checkedAt: Long)
}

object NoUpdateCheckStore : UpdateCheckStore {
    override fun read(): CachedUpdate? = null

    override fun write(release: ReleaseInfo, checkedAt: Long) = Unit
}

class UpdateCheckPrefs(context: Context) : UpdateCheckStore {
    private val prefs = context.getSharedPreferences("dannx_updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): CachedUpdate? {
        val raw = prefs.getString(KEY_CACHED_UPDATE, null) ?: return null
        return runCatching { json.decodeFromString<CachedUpdate>(raw) }.getOrNull()
    }

    override fun write(release: ReleaseInfo, checkedAt: Long) {
        prefs.edit {
            putString(
                KEY_CACHED_UPDATE,
                json.encodeToString(CachedUpdate(release = release, checkedAt = checkedAt)),
            )
        }
    }

    private companion object {
        const val KEY_CACHED_UPDATE = "cached_update"
    }
}
