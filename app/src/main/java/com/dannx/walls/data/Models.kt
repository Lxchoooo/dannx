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
    val createdAt: String = "",
    val tags: List<String> = emptyList(),
    val categoryId: String? = null,
    val storagePath: String = "",
    val createdBy: String = "",
    val updatedAt: String = "",
    val archivedAt: String = "",
) {
    val thumbnailUrl: String
        get() = thumbUrl.ifBlank { imageUrl }

    val isArchived: Boolean
        get() = archivedAt.isNotBlank()
}

@Immutable
data class WallpaperCategory(
    val id: String,
    val name: String,
    val position: Int = 0,
)

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

fun collectTags(items: List<Wallpaper>): List<String> =
    items.flatMap { wallpaper -> wallpaper.tags }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedBy { it.lowercase(Locale.ROOT) }

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
            wallpaper.tags.any { normalize(it).contains(term) }
        matchesCategory && matchesTag && matchesQuery
    }
}
