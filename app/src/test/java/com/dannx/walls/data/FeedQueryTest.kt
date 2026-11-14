package com.dannx.walls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedQueryTest {
    @Test
    fun `asks for one extra item and skips archived wallpapers`() {
        assertEquals(
            "wallpapers?select=*&order=created_at.desc,id.desc&limit=31" +
                "&offset=0&archived_at=is.null",
            feedQuery(WallpaperFilter(), limit = 30, offset = 0),
        )
    }

    @Test
    fun `the offset moves the window`() {
        assertTrue(
            feedQuery(WallpaperFilter(), limit = 30, offset = 60)
                .contains("limit=31&offset=60"),
        )
    }

    @Test
    fun `all and featured categories do not add a filter`() {
        assertFalse(
            feedQuery(WallpaperFilter(category = CategoryAll), 30, 0).contains("category="),
        )
        assertTrue(
            feedQuery(WallpaperFilter(category = CategoryFeatured), 30, 0)
                .contains("featured=eq.true"),
        )
    }

    @Test
    fun `a category filter is encoded`() {
        assertTrue(
            feedQuery(WallpaperFilter(category = "Arte digital"), 30, 0)
                .contains("category=eq.Arte%20digital"),
        )
    }

    @Test
    fun `one tag uses contains`() {
        assertTrue(
            feedQuery(WallpaperFilter(tags = listOf("Cleopatra")), 30, 0)
                .contains("tags_lower=cs.%7B%22cleopatra%22%7D"),
        )
    }

    @Test
    fun `several tags use overlaps without duplicates`() {
        assertTrue(
            feedQuery(WallpaperFilter(tags = listOf("Arte", "arte", " 4k ")), 30, 0)
                .contains("tags_lower=ov.%7B%22arte%22%2C%224k%22%7D"),
        )
    }

    @Test
    fun `tags with commas and braces stay quoted`() {
        assertTrue(
            feedQuery(WallpaperFilter(tag = "día, noche"), 30, 0)
                .contains("tags_lower=cs.%7B%22d%C3%ADa%2C%20noche%22%7D"),
        )
    }

    @Test
    fun `the selected tags win over a single tag`() {
        val query = feedQuery(
            WallpaperFilter(tag = "vieja", tags = listOf("nueva")),
            30,
            0,
        )

        assertTrue(query.contains("tags_lower=cs.%7B%22nueva%22%7D"))
        assertFalse(query.contains("vieja"))
    }

    @Test
    fun `search text drops accents and spaces`() {
        assertTrue(
            feedQuery(WallpaperFilter(query = "  Café "), 30, 0)
                .contains("search_text=ilike.*cafe*"),
        )
    }
}
