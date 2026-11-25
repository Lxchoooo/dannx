package com.dannx.walls

import com.dannx.walls.util.StaticWallpaperSlack
import com.dannx.walls.util.staticWallpaperLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticWallpaperGeometryTest {

    @Test
    fun `el fondo estatico se guarda mas ancho que la pantalla`() {
        val layout = staticWallpaperLayout(1080, 2400)
        assertEquals(2400, layout.height)
        assertTrue(layout.width > 1080)
        assertEquals(1080, layout.visibleWidth)
        assertEquals(2400, layout.visibleHeight)
    }

    @Test
    fun `la porcion visible queda centrada`() {
        val layout = staticWallpaperLayout(1080, 2400)
        assertEquals((layout.width - 1080) / 2, layout.left)
        assertEquals(layout.width - layout.left - 1080, layout.left)
        assertEquals(0, layout.top)
        assertEquals(2400, layout.top + layout.visibleHeight)
    }

    @Test
    fun `el margen por lado es el esperado`() {
        val layout = staticWallpaperLayout(1000, 2000, slack = 1.2f)
        assertEquals(1200, layout.width)
        assertEquals(100, layout.left)
        assertEquals(1100, layout.left + layout.visibleWidth)
    }

    @Test
    fun `sin margen el bitmap mide exactamente la pantalla`() {
        val layout = staticWallpaperLayout(1080, 2400, slack = 1f)
        assertEquals(1080, layout.width)
        assertEquals(0, layout.left)
        assertEquals(1080, layout.left + layout.visibleWidth)
    }

    @Test
    fun `un tamano invalido no rompe`() {
        val layout = staticWallpaperLayout(0, 0)
        assertTrue(layout.width >= 1)
        assertTrue(layout.height >= 1)
        assertEquals(0, layout.left)
    }

    @Test
    fun `el margen por defecto es el documentado`() {
        val layout = staticWallpaperLayout(1000, 2000)
        assertEquals(1150, layout.width)
        assertEquals(75, layout.left)
        assertEquals(StaticWallpaperSlack, layout.width.toFloat() / 1000f, 0.001f)
    }
}
