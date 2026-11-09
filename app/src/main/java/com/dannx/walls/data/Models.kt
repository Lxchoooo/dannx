package com.dannx.walls.data

import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable

/** Valor interno del chip “Todos”. No se muestra tal cual en la interfaz. */
const val CategoryAll = "__all__"

/** Valor interno del chip “Destacados”. No se muestra tal cual en la interfaz. */
const val CategoryFeatured = "__featured__"

@Immutable
data class Wallpaper(
    val id: String,
    val title: String,
    val category: String,
    val imageUrl: String,
    val thumbUrl: String = "",
    val author: String = "DannX",
    val width: Int = 0,
    val height: Int = 0,
    val featured: Boolean = false,
    val featuredPosition: Int = 0,
    val createdAt: String = "",
    val tags: List<String> = emptyList(),
    val categoryId: String? = null,
    val storagePath: String = "",
    val createdBy: String = "",
    val updatedAt: String = "",
    val archivedAt: String = "",
    val deletedAt: String = "",
) {
    val thumbnailUrl: String
        get() = thumbUrl.ifBlank { imageUrl }

    val isArchived: Boolean
        get() = archivedAt.isNotBlank()

    val isDeleted: Boolean
        get() = deletedAt.isNotBlank()
}

@Immutable
data class WallpaperCategory(
    val id: String,
    val name: String,
    val position: Int = 0,
)

/**
 * Contenido editable de la portada. Si un texto queda vacío, la app usa el
 * valor localizado de siempre.
 */
@Immutable
data class AppSettings(
    val tagline: String = "",
    val bannerText: String = "",
    val bannerEnabled: Boolean = false,
) {
    fun normalized(): AppSettings {
        val cleanBanner = bannerText.trim().take(240)
        return copy(
            tagline = tagline.trim().take(120),
            bannerText = cleanBanner,
            bannerEnabled = bannerEnabled && cleanBanner.isNotBlank(),
        )
    }
}

@Immutable
data class AdminSession(
    val email: String,
    val token: String,
    val userId: String = "",
)

@Immutable
@Serializable
data class WallpaperCollection(
    val id: String,
    val name: String,
    val description: String = "",
    val ownerId: String = "",
    val ownerName: String = "DannX",
    val isPublic: Boolean = false,
    val createdAt: String = "",
    val wallpaperIds: List<String> = emptyList(),
)

/** Dueño reservado para las colecciones que solo viven en el teléfono. */
const val LOCAL_COLLECTION_OWNER = "local"
const val LOCAL_COLLECTION_PREFIX = "local-"

/** Una colección local no depende de la cuenta ni del servidor. */
val WallpaperCollection.isLocal: Boolean
    get() = ownerId == LOCAL_COLLECTION_OWNER || id.startsWith(LOCAL_COLLECTION_PREFIX)

val DefaultCategories = listOf(
    "Naturaleza",
    "Abstracto",
    "Minimalista",
    "Ciudad",
    "Espacio",
    "Texturas",
)

private val accents = "\\p{M}+".toRegex()

private fun normalize(value: String): String =
    Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(accents, "")

fun parseTags(value: String): List<String> =
    value.split(',', ';', '\n')
        .map { it.trim().trimStart('#').trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(12)

/**
 * Etiquetas de la fila de Inicio: solo el primer tag de cada fondo (el principal),
 * sin repetir y ordenadas de más a menos usadas; el desempate es alfabético.
 * Los fondos sin tags no aportan etiqueta. Los demás tags solo guardan y se
 * usan para buscar, no aparecen acá.
 */
fun collectTags(items: List<Wallpaper>): List<String> {
    val counts = linkedMapOf<String, Pair<String, Int>>()
    items.forEach { wallpaper ->
        val primary = wallpaper.tags.firstOrNull() ?: return@forEach
        val key = primary.lowercase(Locale.ROOT)
        val previous = counts[key]
        counts[key] = if (previous == null) {
            primary to 1
        } else {
            previous.first to previous.second + 1
        }
    }
    return counts.values
        .sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenBy { it.first.lowercase(Locale.ROOT) },
        )
        .map { it.first }
}

fun filterWallpapers(
    items: List<Wallpaper>,
    query: String,
    category: String?,
    tag: String? = null,
): List<Wallpaper> {
    val term = normalize(query)
    return items.filter { wallpaper ->
        val matchesCategory = when (category) {
            null, CategoryAll, "Todos" -> true
            CategoryFeatured, "Destacados" -> wallpaper.featured
            else -> wallpaper.category.equals(category, ignoreCase = true)
        }
        val matchesTag = tag == null ||
            wallpaper.tags.any { it.equals(tag, ignoreCase = true) }
        val matchesQuery = term.isBlank() ||
            normalize(wallpaper.title).contains(term) ||
            normalize(wallpaper.category).contains(term) ||
            normalize(wallpaper.author).contains(term) ||
            wallpaper.tags.any { normalize(it).contains(term) }
        matchesCategory && matchesTag && matchesQuery
    }
}
