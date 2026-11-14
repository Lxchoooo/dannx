package com.dannx.walls.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Codifica un valor para un parámetro de consulta de PostgREST. */
internal fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

/**
 * Ruta del feed con los filtros resueltos en PostgREST, para que la búsqueda no
 * dependa de lo que ya se descargó en el teléfono. Pide un elemento de más para
 * saber si queda otra página.
 */
internal fun feedQuery(filter: WallpaperFilter, limit: Int, offset: Int): String {
    val params = mutableListOf(
        "select=*",
        "order=created_at.desc,id.desc",
        "limit=${limit + 1}",
        "offset=$offset",
        "archived_at=is.null",
    )
    when (filter.category) {
        null, CategoryAll, "Todos" -> Unit
        CategoryFeatured, "Destacados" -> params += "featured=eq.true"
        else -> params += "category=eq.${encodeQueryValue(filter.category)}"
    }
    val selectedTags = filter.tags
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .distinct()
    when {
        // `ov` (overlaps) matchea cualquier fondo que tenga al menos una de las
        // etiquetas, en un solo parámetro.
        selectedTags.size > 1 -> {
            params += "tags_lower=ov.${encodeQueryValue(postgrestArray(selectedTags))}"
        }
        selectedTags.size == 1 -> {
            params += "tags_lower=cs.${encodeQueryValue(postgrestArray(selectedTags))}"
        }
        else -> filter.tag?.takeIf { it.isNotBlank() }?.let { tag ->
            val clean = postgrestArray(listOf(tag.trim().lowercase(Locale.ROOT)))
            params += "tags_lower=cs.${encodeQueryValue(clean)}"
        }
    }
    filter.query.trim().takeIf { it.isNotEmpty() }?.let { query ->
        val value = encodeQueryValue(normalizeSearch(query).ifBlank { query.trim() })
        params += "search_text=ilike.*$value*"
    }
    return "wallpapers?${params.joinToString("&")}"
}
