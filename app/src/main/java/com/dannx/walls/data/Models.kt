package com.dannx.walls.data

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable

data class Wallpaper(
    val id: String,
    val title: String,
    val category: String,
    val imageUrl: String,
    val author: String = "DannX",
    val width: Int = 0,
    val height: Int = 0,
    val featured: Boolean = false,
    val createdAt: String = "",
    val tags: List<String> = emptyList(),
)

data class AdminSession(
    val email: String,
    val token: String,
    val userId: String = "",
)

@Serializable
data class WallpaperCollection(
    val id: String,
    val name: String,
    val description: String = "",
    val ownerId: String = "",
    val ownerName: String = "DannX",
    val isPublic: Boolean = true,
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
            null, "Todos" -> true
            "Destacados" -> wallpaper.featured
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
