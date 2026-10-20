package com.dannx.walls.data

import java.text.Normalizer
import java.util.Locale

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
)

data class AdminSession(
    val email: String,
    val token: String,
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
    Normalizer.normalize(value.trim().lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        .replace(accents, "")

fun filterWallpapers(
    items: List<Wallpaper>,
    query: String,
    category: String?,
): List<Wallpaper> {
    val term = normalize(query)
    return items.filter { wallpaper ->
        val matchesCategory = when (category) {
            null, "Todos" -> true
            "Destacados" -> wallpaper.featured
            else -> wallpaper.category.equals(category, ignoreCase = true)
        }
        val matchesQuery = term.isBlank() ||
            normalize(wallpaper.title).contains(term) ||
            normalize(wallpaper.category).contains(term)
        matchesCategory && matchesQuery
    }
}
