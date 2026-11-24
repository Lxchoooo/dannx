package com.dannx.walls

import com.dannx.walls.wallpaper.LiveWallpaperGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWallpaperGeometryTest {

    private val delta = 0.0001f

    @Test
    fun `el centro de la pantalla no mueve ninguna capa`() {
        assertEquals(0f, LiveWallpaperGeometry.normalizedOffset(0.5f), delta)
        assertEquals(
            0f,
            LiveWallpaperGeometry.shift(1080f, 0.5f, LiveWallpaperGeometry.ForegroundShift),
            delta,
        )
    }

    @Test
    fun `el desplazamiento se reparte a los dos lados`() {
        assertEquals(-1f, LiveWallpaperGeometry.normalizedOffset(0f), delta)
        assertEquals(1f, LiveWallpaperGeometry.normalizedOffset(1f), delta)
        assertEquals(-0.5f, LiveWallpaperGeometry.normalizedOffset(0.25f), delta)
        assertEquals(0.5f, LiveWallpaperGeometry.normalizedOffset(0.75f), delta)
    }

    @Test
    fun `un launcher que informa de mas no mueve la foto fuera de rango`() {
        assertEquals(-1f, LiveWallpaperGeometry.normalizedOffset(-2f), delta)
        assertEquals(1f, LiveWallpaperGeometry.normalizedOffset(4f), delta)
    }

    @Test
    fun `la foto nitida recorre el ancho acordado`() {
        assertEquals(
            -1080f * LiveWallpaperGeometry.ForegroundShift,
            LiveWallpaperGeometry.shift(1080f, 0f, LiveWallpaperGeometry.ForegroundShift),
            delta,
        )
        assertEquals(
            1080f * LiveWallpaperGeometry.ForegroundShift,
            LiveWallpaperGeometry.shift(1080f, 1f, LiveWallpaperGeometry.ForegroundShift),
            delta,
        )
    }

    @Test
    fun `el fondo se mueve menos que la foto`() {
        val foreground =
            LiveWallpaperGeometry.shift(1080f, 1f, LiveWallpaperGeometry.ForegroundShift)
        val backdrop =
            LiveWallpaperGeometry.shift(1080f, 1f, LiveWallpaperGeometry.BackgroundShift)
        assertTrue(backdrop > 0f)
        assertTrue(backdrop < foreground)
    }

    @Test
    fun `el margen del fondo tapa el costado que descubre la foto`() {
        // El fondo gana medio margen por lado, y como se corre menos que la
        // foto, siempre queda imagen detrás del hueco que se abre.
        assertTrue(LiveWallpaperGeometry.BackgroundSlack / 2f > LiveWallpaperGeometry.BackgroundShift)
        assertTrue(LiveWallpaperGeometry.BackgroundShift < LiveWallpaperGeometry.ForegroundShift)
    }

    @Test
    fun `la foto cubre el destino sin deformarse`() {
        assertEquals(
            1f,
            LiveWallpaperGeometry.coverScale(1080, 2400, 1080f, 2400f),
            delta,
        )
        assertEquals(
            2f,
            LiveWallpaperGeometry.coverScale(1080, 2400, 2160f, 2400f),
            delta,
        )
        assertEquals(
            1f,
            LiveWallpaperGeometry.coverScale(1080, 2400, 540f, 2400f),
            delta,
        )
    }

    @Test
    fun `un tamano invalido no produce escala`() {
        assertEquals(0f, LiveWallpaperGeometry.coverScale(0, 2400, 1080f, 2400f), delta)
        assertEquals(0f, LiveWallpaperGeometry.coverScale(1080, 2400, 0f, 2400f), delta)
    }
}
