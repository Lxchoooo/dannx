package com.dannx.walls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperColorTest {
    @Test
    fun `clasifica los colores base`() {
        assertEquals(WallpaperColor.RED, colorBucketFor(0xFFE53935.toInt()))
        assertEquals(WallpaperColor.ORANGE, colorBucketFor(0xFFFB8C00.toInt()))
        assertEquals(WallpaperColor.YELLOW, colorBucketFor(0xFFFDD835.toInt()))
        assertEquals(WallpaperColor.GREEN, colorBucketFor(0xFF43A047.toInt()))
        assertEquals(WallpaperColor.BLUE, colorBucketFor(0xFF1E88E5.toInt()))
        assertEquals(WallpaperColor.PURPLE, colorBucketFor(0xFF8E24AA.toInt()))
        assertEquals(WallpaperColor.PINK, colorBucketFor(0xFFEC407A.toInt()))
    }

    @Test
    fun `separa negro de blancos y grises`() {
        assertEquals(WallpaperColor.BLACK, colorBucketFor(0xFF111111.toInt()))
        assertEquals(WallpaperColor.GRAY, colorBucketFor(0xFFFFFFFF.toInt()))
        assertEquals(WallpaperColor.GRAY, colorBucketFor(0xFF9E9E9E.toInt()))
    }

    @Test
    fun `un tag de color gana sobre el analisis de la imagen`() {
        assertEquals(WallpaperColor.RED, colorFromTags(listOf("noche", "ROJO")))
        assertEquals(WallpaperColor.BLUE, colorFromTags(listOf("Azul")))
        assertEquals(WallpaperColor.GRAY, colorFromTags(listOf("blanco")))
        assertEquals(WallpaperColor.PURPLE, colorFromTags(listOf("violeta")))
        assertNull(colorFromTags(listOf("noche", "ciudad")))
    }

    @Test
    fun `el color dominante ignora los pixeles transparentes`() {
        val pixels = IntArray(100) { index ->
            if (index < 80) 0xFF1E88E5.toInt() else 0x00E53935
        }
        assertEquals(WallpaperColor.BLUE, dominantColorBucket(pixels))
        assertNull(dominantColorBucket(IntArray(0)))
        assertNull(dominantColorBucket(IntArray(10) { 0x00111111 }))
    }

    @Test
    fun `restaura el color guardado por su clave`() {
        assertEquals(WallpaperColor.PINK, WallpaperColor.fromKey("pink"))
        assertNull(WallpaperColor.fromKey("turquesa"))
        assertNull(WallpaperColor.fromKey(null))
    }
}
