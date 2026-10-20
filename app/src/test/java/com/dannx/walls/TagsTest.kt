package com.dannx.walls

import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.collectTags
import com.dannx.walls.data.parseTags
import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {

    @Test
    fun `parse tags trims, removes hash and dedupes case insensitively`() {
        assertEquals(
            listOf("anime", "Dragon Ball", "flores"),
            parseTags(" #anime, Dragon Ball; flores, ANIME "),
        )
    }

    @Test
    fun `parse tags drops blanks and keeps spaces inside a tag`() {
        assertEquals(
            listOf("Dragon Ball", "cielo nocturno"),
            parseTags(",, Dragon Ball ; ; cielo nocturno, "),
        )
    }

    @Test
    fun `collect tags is unique and sorted`() {
        val wallpapers = listOf(
            Wallpaper("1", "Uno", "Naturaleza", "url", tags = listOf("flores", "cielo")),
            Wallpaper("2", "Dos", "Ciudad", "url", tags = listOf("CIELO", "noche")),
        )
        assertEquals(listOf("cielo", "flores", "noche"), collectTags(wallpapers))
    }
}
