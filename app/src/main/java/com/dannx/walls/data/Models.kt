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
 * Regla guardada entre una etiqueta y una categoría. `tag` va en minúsculas y
 * sin almohadilla; `displayTag` conserva el texto tal como se escribió.
 */
@Immutable
data class TagCategory(
    val tag: String,
    val displayTag: String,
    val categoryId: String,
    val categoryName: String = "",
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

internal fun normalizeSearch(value: String): String =
    Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(accents, "")

private val adjacentHashtags = Regex("\\s+(?=#)")

/**
 * Separa un valor guardado que junta varios hashtags ("#playa #sol") en
 * etiquetas individuales. No corta nombres con espacios que no son hashtags.
 */
fun splitTagValue(value: String): List<String> =
    value.split(adjacentHashtags)
        .map { it.trim().trimStart('#').trim() }
        .filter { it.isNotEmpty() }

/** Todas las etiquetas de un fondo, con los valores compuestos ya separados. */
fun allTags(tags: List<String>): List<String> =
    tags.flatMap(::splitTagValue)
        .distinctBy { it.lowercase(Locale.ROOT) }

fun parseTags(value: String): List<String> =
    value.split(',', ';', '\n')
        .flatMap(::splitTagValue)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(12)

private val nextHashtag = Regex("\\s+#")

/**
 * Etiqueta principal de un fondo. Si un tag guardado junta varios hashtags
 * ("#playa #sol"), toma solo el primero. No corta nombres con espacios que no
 * son hashtags ("Dragon Ball").
 */
fun primaryTag(tags: List<String>): String? {
    val first = tags.asSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return null
    val boundary = nextHashtag.find(first)
    val value = if (boundary == null) first else first.take(boundary.range.first)
    return value.trimStart('#').trim().takeIf { it.isNotEmpty() }
}

/**
 * Etiquetas de la fila de Inicio: solo el primer tag de cada fondo (el principal),
 * sin repetir y ordenadas de más a menos usadas; el desempate es alfabético.
 * Los fondos sin tags no aportan etiqueta. Los demás tags solo guardan y se
 * usan para buscar, no aparecen acá.
 */
fun collectTags(items: List<Wallpaper>): List<String> {
    val counts = linkedMapOf<String, Pair<String, Int>>()
    items.forEach { wallpaper ->
        val primary = primaryTag(wallpaper.tags) ?: return@forEach
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

/**
 * Valor completo del primer tag de cada fondo, sin repetir etiqueta y ordenado
 * por uso. Conserva el texto original para que el filtro del servidor siga
 * encontrando los tags viejos que guardan varios hashtags juntos.
 */
fun collectTagValues(items: List<Wallpaper>): List<String> {
    val counts = linkedMapOf<String, Pair<String, Int>>()
    items.forEach { wallpaper ->
        val raw = wallpaper.tags.asSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return@forEach
        val label = primaryTag(listOf(raw)) ?: return@forEach
        val key = label.lowercase(Locale.ROOT)
        val previous = counts[key]
        counts[key] = if (previous == null) {
            raw to 1
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

/**
 * Suma al catálogo las etiquetas nuevas que trajo una página o un filtro, sin
 * quitar las que ya estaban: así la fila de Inicio no pierde chips cuando el
 * feed se reduce a una sola etiqueta.
 */
fun mergeTagCatalog(catalog: List<String>, items: List<Wallpaper>): List<String> {
    val merged = catalog.toMutableList()
    val labels = merged
        .mapNotNull { primaryTag(listOf(it))?.lowercase(Locale.ROOT) }
        .toMutableSet()
    collectTagValues(items).forEach { raw ->
        val label = primaryTag(listOf(raw))?.lowercase(Locale.ROOT) ?: return@forEach
        if (labels.add(label)) merged += raw
    }
    return merged
}

/** Etiquetas del Estudio con cuántos fondos las usan, ya separadas. */
fun collectAllTags(items: List<Wallpaper>): List<Pair<String, Int>> {
    val counts = linkedMapOf<String, Pair<String, Int>>()
    items.forEach { wallpaper ->
        allTags(wallpaper.tags).forEach { tag ->
            val key = tag.lowercase(Locale.ROOT)
            val previous = counts[key]
            counts[key] = if (previous == null) {
                tag to 1
            } else {
                previous.first to previous.second + 1
            }
        }
    }
    return counts.values
        .sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenBy { it.first.lowercase(Locale.ROOT) },
        )
}

fun filterWallpapers(
    items: List<Wallpaper>,
    query: String,
    category: String?,
    tag: String? = null,
    tags: List<String> = emptyList(),
): List<Wallpaper> {
    val term = normalizeSearch(query)
    val wantedTags = tags.filter { it.isNotBlank() }
    return items.filter { wallpaper ->
        val matchesCategory = when (category) {
            null, CategoryAll, "Todos" -> true
            CategoryFeatured, "Destacados" -> wallpaper.featured
            else -> wallpaper.category.equals(category, ignoreCase = true)
        }
        val wallpaperTags = allTags(wallpaper.tags)
        val matchesTag = when {
            wantedTags.isNotEmpty() -> wallpaperTags.any { value ->
                wantedTags.any { it.equals(value, ignoreCase = true) }
            }
            else -> tag == null ||
                wallpaper.tags.firstOrNull()?.equals(tag, ignoreCase = true) == true
        }
        val matchesQuery = term.isBlank() ||
            normalizeSearch(wallpaper.title).contains(term) ||
            normalizeSearch(wallpaper.category).contains(term) ||
            normalizeSearch(wallpaper.author).contains(term) ||
            wallpaper.tags.any { normalizeSearch(it).contains(term) }
        matchesCategory && matchesTag && matchesQuery
    }
}
