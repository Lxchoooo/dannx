package com.dannx.walls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorCacheTest {
    @Test
    fun `round trip keeps colors and failed attempts`() {
        val colors = linkedMapOf(
            "a" to WallpaperColor.RED,
            "b" to null,
            "c" to WallpaperColor.BLUE,
        )

        val decoded = decodeColorCache(encodeColorCache(colors))

        assertEquals(3, decoded.size)
        assertEquals(WallpaperColor.RED, decoded["a"])
        assertTrue(decoded.containsKey("b"))
        assertNull(decoded["b"])
        assertEquals(WallpaperColor.BLUE, decoded["c"])
    }

    @Test
    fun `the limit keeps the most recently used entries`() {
        val colors = linkedMapOf(
            "1" to WallpaperColor.RED,
            "2" to WallpaperColor.BLUE,
            "3" to WallpaperColor.GREEN,
        )

        val decoded = decodeColorCache(encodeColorCache(colors, limit = 2))

        assertEquals(setOf("2", "3"), decoded.keys)
    }

    @Test
    fun `unknown color keys are ignored`() {
        val decoded = decodeColorCache("""{"a":"violeta","b":"red"}""")

        assertEquals(setOf("b"), decoded.keys)
        assertEquals(WallpaperColor.RED, decoded["b"])
    }

    @Test
    fun `broken payloads fall back to an empty cache`() {
        assertTrue(decodeColorCache("{nope").isEmpty())
        assertTrue(decodeColorCache(null).isEmpty())
    }
}
