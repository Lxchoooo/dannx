package com.dannx.walls

import com.dannx.walls.data.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionsTest {
    @Test
    fun `short queries do not suggest anything`() {
        val state = UiState(wallpapers = listOf(wallpaper("Naturaleza")), query = "n")

        assertTrue(state.searchSuggestions.isEmpty())
    }

    @Test
    fun `tags and authors match ignoring case and accents`() {
        val state = UiState(
            wallpapers = listOf(
                wallpaper(title = "Bosque", tags = listOf("Naturaleza"), author = "Ana"),
                wallpaper(title = "Playa", tags = listOf("Verano"), author = "Natalia"),
            ),
            query = "NATU",
        )

        assertEquals(listOf("Naturaleza", "Natalia"), state.searchSuggestions)
    }

    @Test
    fun `suggestions are unique and capped`() {
        val items = (1..8).map { index ->
            wallpaper(
                title = "Fondo $index",
                tags = listOf("Naturaleza"),
                author = "Naturaleza",
            )
        }

        val suggestions = UiState(wallpapers = items, query = "naturaleza").searchSuggestions

        assertEquals(listOf("Naturaleza"), suggestions)
    }

    private fun wallpaper(
        title: String,
        tags: List<String> = emptyList(),
        author: String = "DannX",
    ) = Wallpaper(
        id = "id-${title.lowercase()}",
        title = title,
        category = "Abstracto",
        imageUrl = "https://example.com/${title.lowercase()}.jpg",
        author = author,
        tags = tags,
    )
}
