package com.dannx.walls

import com.dannx.walls.data.LiveMotion
import com.dannx.walls.wallpaper.LiveWallpaperGeometry
import kotlin.math.abs
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
        // En la primera página el fondo muestra su lado izquierdo y la capa se
        // corre a la derecha; en la última, al revés.
        assertEquals(
            1080f * LiveWallpaperGeometry.ForegroundShift,
            LiveWallpaperGeometry.shift(1080f, 0f, LiveWallpaperGeometry.ForegroundShift),
            delta,
        )
        assertEquals(
            -1080f * LiveWallpaperGeometry.ForegroundShift,
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
        assertTrue(backdrop < 0f)
        assertTrue(abs(backdrop) < abs(foreground))
    }

    @Test
    fun `el margen del fondo tapa el costado que descubre la foto`() {
        // El fondo gana medio margen por lado, y como se corre menos que la
        // foto, siempre queda imagen detrás del hueco que se abre.
        assertTrue(LiveWallpaperGeometry.BackgroundSlack / 2f > LiveWallpaperGeometry.BackgroundShift)
        assertTrue(LiveWallpaperGeometry.BackgroundShift < LiveWallpaperGeometry.ForegroundShift)
    }

    @Test
    fun `el fondo desenfocado siempre tapa toda la pantalla`() {
        // Recorre todas las posiciones posibles del launcher en las tres
        // intensidades: la copia de fondo nunca puede dejar ver un borde.
        val width = 1080f
        val layerWidth = width * (1f + LiveWallpaperGeometry.BackgroundSlack)
        for (step in 0..40) {
            val offset = step / 40f
            for (motion in LiveMotion.entries) {
                val shift = LiveWallpaperGeometry.shift(
                    width,
                    offset,
                    LiveWallpaperGeometry.BackgroundShift,
                    motion,
                )
                val left = width / 2f + shift - layerWidth / 2f
                val right = width / 2f + shift + layerWidth / 2f
                assertTrue("borde izquierdo descubierto: $offset, $motion", left <= 0f)
                assertTrue("borde derecho descubierto: $offset, $motion", right >= width)
            }
        }
    }

    @Test
    fun `la foto nitida nunca deja mas hueco que su recorrido`() {
        val width = 1080f
        val maxShift = width * LiveWallpaperGeometry.ForegroundShift
        for (step in 0..40) {
            val offset = step / 40f
            for (motion in LiveMotion.entries) {
                val shift = abs(
                    LiveWallpaperGeometry.shift(
                        width,
                        offset,
                        LiveWallpaperGeometry.ForegroundShift,
                        motion,
                    ),
                )
                assertTrue("se corrió de más: $offset, $motion", shift <= maxShift + delta)
            }
        }
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

    @Test
    fun `cada intensidad pesa lo suyo`() {
        assertEquals(1f, LiveWallpaperGeometry.weight(LiveMotion.NORMAL), delta)
        assertEquals(0f, LiveWallpaperGeometry.weight(LiveMotion.STILL), delta)
        assertTrue(
            LiveWallpaperGeometry.weight(LiveMotion.SUBTLE) <
                LiveWallpaperGeometry.weight(LiveMotion.NORMAL),
        )
        assertTrue(LiveWallpaperGeometry.weight(LiveMotion.SUBTLE) > 0f)
    }

    @Test
    fun `quieto deja la foto centrada`() {
        assertEquals(
            0f,
            LiveWallpaperGeometry.shift(
                1080f,
                1f,
                LiveWallpaperGeometry.ForegroundShift,
                LiveMotion.STILL,
            ),
            delta,
        )
    }

    @Test
    fun `suave recorre menos que normal`() {
        val subtle = abs(
            LiveWallpaperGeometry.shift(
                1080f,
                1f,
                LiveWallpaperGeometry.ForegroundShift,
                LiveMotion.SUBTLE,
            ),
        )
        val normal = abs(
            LiveWallpaperGeometry.shift(
                1080f,
                1f,
                LiveWallpaperGeometry.ForegroundShift,
                LiveMotion.NORMAL,
            ),
        )
        assertTrue(subtle > 0f)
        assertTrue(subtle < normal)
    }
}
