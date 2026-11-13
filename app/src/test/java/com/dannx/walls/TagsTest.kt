package com.dannx.walls

import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.allTags
import com.dannx.walls.data.collectAllTags
import com.dannx.walls.data.collectTagValues
import com.dannx.walls.data.collectTags
import com.dannx.walls.data.filterWallpapers
import com.dannx.walls.data.mergeTagCatalog
import com.dannx.walls.data.parseTags
import com.dannx.walls.data.primaryTag
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
    fun `parse tags separates several hashtags written together`() {
        assertEquals(
            listOf("Cleopatra", "queen", "4k", "art"),
            parseTags("#Cleopatra #queen #4k #art"),
        )
        assertEquals(
            listOf("Dragon Ball", "anime"),
            parseTags("Dragon Ball #anime"),
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
    fun `several hashtags in one tag contribute only the first label`() {
        val wallpaper = Wallpaper(
            "1",
            "Cleopatra",
            "Abstracto",
            "url",
            tags = listOf("#Cleopatra #queen #4k #art #pharaona #texture"),
        )
        assertEquals(listOf("Cleopatra"), collectTags(listOf(wallpaper)))
    }

    @Test
    fun `primary tag keeps spaces when they are not before another hashtag`() {
        assertEquals("Dragon Ball", primaryTag(listOf("Dragon Ball")))
        assertEquals("Dragon Ball", primaryTag(listOf("Dragon Ball #anime")))
    }

    @Test
    fun `filtering by a later hashtag of the same tag does not match`() {
        val wallpaper = Wallpaper(
            "1",
            "Cleopatra",
            "Abstracto",
            "url",
            tags = listOf("Cleopatra #queen #4k"),
        )
        assertEquals(
            listOf(wallpaper),
            filterWallpapers(listOf(wallpaper), "", null, "Cleopatra #queen #4k"),
        )
        assertEquals(
            emptyList<Wallpaper>(),
            filterWallpapers(listOf(wallpaper), "", null, "queen"),
        )
    }

    @Test
    fun `home chip uses the first hashtag but keeps the full tag to filter`() {
        val wallpaper = Wallpaper(
            "1",
            "Cleopatra",
            "Abstracto",
            "url",
            tags = listOf("Cleopatra #queen #4k"),
        )
        val state = UiState(wallpapers = listOf(wallpaper), tag = "Cleopatra #queen #4k")

        assertEquals(listOf("Cleopatra"), state.availableTags)
        assertEquals("Cleopatra", state.selectedTag)
        assertEquals("Cleopatra #queen #4k", state.tagFilterValue("Cleopatra"))
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

    @Test
    fun `tag catalog keeps every label when the feed is filtered`() {
        val cleopatra = Wallpaper(
            "1",
            "Cleopatra",
            "Abstracto",
            "url",
            tags = listOf("Cleopatra #queen #4k"),
        )
        val flores = Wallpaper("2", "Flores", "Naturaleza", "url", tags = listOf("flores"))
        val catalog = collectTagValues(listOf(cleopatra, flores))

        // El feed filtrado solo trae un fondo, pero las chips siguen completas.
        val filteredState = UiState(wallpapers = listOf(cleopatra), tagCatalog = catalog)

        assertEquals(listOf("Cleopatra", "flores"), filteredState.availableTags)
        assertEquals("Cleopatra #queen #4k", filteredState.tagFilterValue("Cleopatra"))
    }

    @Test
    fun `merging the catalog only adds new labels`() {
        val first = Wallpaper("1", "Uno", "Naturaleza", "url", tags = listOf("flores"))
        val second = Wallpaper("2", "Dos", "Ciudad", "url", tags = listOf("ciudad"))
        val catalog = collectTagValues(listOf(first))

        val merged = mergeTagCatalog(catalog, listOf(second))

        assertEquals(listOf("flores", "ciudad"), merged)
        assertEquals(catalog, mergeTagCatalog(merged, listOf(first)))
    }

    @Test
    fun `all tags split composite values and keep a single label per wallpaper`() {
        assertEquals(
            listOf("Cleopatra", "queen", "4k"),
            allTags(listOf("Cleopatra #queen #4k")),
        )
        assertEquals(
            listOf("Dragon Ball", "anime"),
            allTags(listOf("Dragon Ball", "anime", "Dragon Ball")),
        )
    }

    @Test
    fun `studio tag counts include composite tags`() {
        val wallpapers = listOf(
            Wallpaper("1", "Uno", "Naturaleza", "url", tags = listOf("Cleopatra #queen")),
            Wallpaper("2", "Dos", "Ciudad", "url", tags = listOf("Cleopatra")),
        )
        assertEquals(
            listOf("Cleopatra" to 2, "queen" to 1),
            collectAllTags(wallpapers),
        )
    }
}
