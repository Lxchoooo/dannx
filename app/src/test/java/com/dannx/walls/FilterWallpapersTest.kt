package com.dannx.walls

import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.filterWallpapers
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterWallpapersTest {

    private val items = listOf(
        Wallpaper(
            "1",
            "Aurora boreal",
            "Naturaleza",
            "https://example.com/1.jpg",
            featured = true,
            tags = listOf("cielo", "noche"),
        ),
        Wallpaper("2", "Duna", "Abstracto", "https://example.com/2.jpg"),
        Wallpaper(
            "3",
            "Órbita",
            "Espacio",
            "https://example.com/3.jpg",
            featured = true,
            tags = listOf("planeta"),
        ),
        Wallpaper(
            "4",
            "Calle nocturna",
            "Ciudad",
            "https://example.com/4.jpg",
            tags = listOf("noche", "luces"),
        ),
    )

    @Test
    fun `empty filters return everything`() {
        assertEquals(items, filterWallpapers(items, "", null))
    }

    @Test
    fun `query ignores case and accents`() {
        assertEquals(listOf(items[2]), filterWallpapers(items, "orbita", null))
        assertEquals(listOf(items[3]), filterWallpapers(items, "NOCTURN", null))
    }

    @Test
    fun `query searches tags`() {
        assertEquals(listOf(items[0], items[3]), filterWallpapers(items, "noche", null))
    }

    @Test
    fun `tag filter is case insensitive and combines with category`() {
        assertEquals(listOf(items[0], items[3]), filterWallpapers(items, "", null, "NOCHE"))
        assertEquals(listOf(items[3]), filterWallpapers(items, "", "Ciudad", "noche"))
    }

    @Test
    fun `category filter matches only that category`() {
        assertEquals(listOf(items[2]), filterWallpapers(items, "", "Espacio"))
    }

    @Test
    fun `featured filter returns highlighted items`() {
        assertEquals(listOf(items[0], items[2]), filterWallpapers(items, "", "Destacados"))
    }

    @Test
    fun `query and category combine`() {
        assertEquals(listOf(items[0]), filterWallpapers(items, "aurora", "Naturaleza"))
        assertEquals(emptyList<Wallpaper>(), filterWallpapers(items, "aurora", "Ciudad"))
    }
}
