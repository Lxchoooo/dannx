package com.dannx.walls

import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.collectTags
import com.dannx.walls.data.filterWallpapers
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
    fun `collect tags keeps only the first tag of each wallpaper`() {
        val wallpapers = listOf(
            Wallpaper("1", "Uno", "Naturaleza", "url", tags = listOf("flores", "cielo", "noche")),
            Wallpaper("2", "Dos", "Ciudad", "url", tags = listOf("cielo", "otro")),
        )
        // Un uso cada uno; el desempate es alfabético: cielo antes que flores.
        assertEquals(listOf("cielo", "flores"), collectTags(wallpapers))
    }

    @Test
    fun `collect tags dedupes case insensitively keeping the first casing`() {
        val wallpapers = listOf(
            Wallpaper("1", "Uno", "Naturaleza", "url", tags = listOf("Cielo")),
            Wallpaper("2", "Dos", "Ciudad", "url", tags = listOf("CIELO")),
        )
        assertEquals(listOf("Cielo"), collectTags(wallpapers))
    }

    @Test
    fun `a wallpaper with eight tags contributes a single label`() {
        val wallpaper = Wallpaper(
            "1",
            "Uno",
            "Naturaleza",
            "url",
            tags = listOf(
                "principal",
                "dos",
                "tres",
                "cuatro",
                "cinco",
                "seis",
                "siete",
                "ocho",
            ),
        )
        assertEquals(listOf("principal"), collectTags(listOf(wallpaper)))
    }

    @Test
    fun `collect tags orders by usage descending with alphabetical tie`() {
        val wallpapers = listOf(
            Wallpaper("1", "Uno", "Naturaleza", "url", tags = listOf("b", "extra")),
            Wallpaper("2", "Dos", "Ciudad", "url", tags = listOf("a")),
            Wallpaper("3", "Tres", "Espacio", "url", tags = listOf("b")),
            Wallpaper("4", "Cuatro", "Ciudad", "url", tags = listOf("c")),
            Wallpaper("5", "Cinco", "Ciudad", "url", tags = listOf("a")),
            Wallpaper("6", "Seis", "Ciudad", "url", tags = listOf("a")),
        )
        // a: 3 usos, b: 2, c: 1.
        assertEquals(listOf("a", "b", "c"), collectTags(wallpapers))
    }

    @Test
    fun `collect tags tie is alphabetical`() {
        val wallpapers = listOf(
            Wallpaper("1", "Uno", "Naturaleza", "url", tags = listOf("zorro")),
            Wallpaper("2", "Dos", "Ciudad", "url", tags = listOf("abeto")),
        )
        assertEquals(listOf("abeto", "zorro"), collectTags(wallpapers))
    }

    @Test
    fun `wallpapers without tags contribute no label`() {
        val wallpapers = listOf(
            Wallpaper("1", "Uno", "Naturaleza", "url"),
            Wallpaper("2", "Dos", "Ciudad", "url", tags = emptyList()),
        )
        assertEquals(emptyList<String>(), collectTags(wallpapers))
    }

    @Test
    fun `filtering by the primary tag finds the wallpaper`() {
        val wallpaper = Wallpaper(
            "1",
            "Uno",
            "Naturaleza",
            "url",
            tags = listOf("flores", "cielo", "noche"),
        )
        val filtered = filterWallpapers(listOf(wallpaper), "", null, "flores")
        assertEquals(listOf(wallpaper), filtered)
    }

    @Test
    fun `searching by a secondary tag still finds the wallpaper`() {
        val wallpaper = Wallpaper(
            "1",
            "Uno",
            "Naturaleza",
            "url",
            tags = listOf("flores", "cielo", "noche"),
        )
        val filtered = filterWallpapers(listOf(wallpaper), "noche", null)
        assertEquals(listOf(wallpaper), filtered)
    }
}
