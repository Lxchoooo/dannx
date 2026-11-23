package com.dannx.walls.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.dannx.walls.data.ColorContrast
import com.dannx.walls.data.ColorPaletteStyle
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprueba que el motor de color genere esquemas distintos y estables para
 * cada estilo, y que las opciones de contraste y negro puro se reflejen.
 */
class ColorEngineTest {

    private val seed = Color(0xFF3366CC)

    private fun scheme(
        style: ColorPaletteStyle = ColorPaletteStyle.TONAL_SPOT,
        dark: Boolean = false,
        contrast: ColorContrast = ColorContrast.STANDARD,
        amoled: Boolean = false,
    ) = materialScheme(seed, dark, style, contrast, amoled)

    private fun chroma(color: Color): Double =
        Hct.fromInt(color.toArgb()).chroma

    @Test
    fun `parsea semillas hex validas`() {
        assertEquals(Color(0xFFFF8A00), parseSeedColor("#FF8A00"))
        assertEquals(Color(0xFF22C7D6), parseSeedColor("22c7d6"))
        assertEquals(Color(0xFF000000), parseSeedColor("#000000"))
    }

    @Test
    fun `ignora semillas invalidas`() {
        assertNull(parseSeedColor(""))
        assertNull(parseSeedColor("#FFF"))
        assertNull(parseSeedColor("no-es-un-color"))
        assertNull(parseSeedColor("#GGGGGG"))
    }

    @Test
    fun `la semilla por defecto es calida`() {
        assertEquals(Color(0xFFFF8A00), DefaultSeed)
    }

    @Test
    fun `el mismo ajuste produce el mismo esquema`() {
        val first = scheme(style = ColorPaletteStyle.EXPRESSIVE, dark = true)
        val second = scheme(style = ColorPaletteStyle.EXPRESSIVE, dark = true)
        assertEquals(first.primary, second.primary)
        assertEquals(first.surfaceContainer, second.surfaceContainer)
        assertEquals(first.onSurface, second.onSurface)
    }

    @Test
    fun `claro y oscuro no comparten fondo`() {
        val light = scheme(dark = false)
        val dark = scheme(dark = true)
        assertNotEquals(light.background, dark.background)
        assertTrue(light.background.luminance() > dark.background.luminance())
    }

    @Test
    fun `el estilo monocromatico no tiene croma`() {
        val primary = scheme(style = ColorPaletteStyle.MONOCHROME).primary
        assertTrue(chroma(primary) < 5.0)
    }

    @Test
    fun `vibrante satura mas que neutro`() {
        val vibrant = chroma(scheme(style = ColorPaletteStyle.VIBRANT).primary)
        val neutral = chroma(scheme(style = ColorPaletteStyle.NEUTRAL).primary)
        assertTrue(vibrant > neutral)
    }

    @Test
    fun `cmf combina la semilla con un tono complementario`() {
        val cmf = scheme(style = ColorPaletteStyle.CMF)
        val primaryHue = Hct.fromInt(cmf.primary.toArgb()).hue
        val tertiaryHue = Hct.fromInt(cmf.tertiary.toArgb()).hue
        assertNotEquals(primaryHue, tertiaryHue, 1.0)
    }

    @Test
    fun `el estilo expresivo cambia los acentos`() {
        val tonal = scheme(style = ColorPaletteStyle.TONAL_SPOT)
        val expressive = scheme(style = ColorPaletteStyle.EXPRESSIVE)
        assertNotEquals(tonal.primary, expressive.primary)
        assertNotEquals(tonal.tertiary, expressive.tertiary)
    }

    @Test
    fun `el contraste alto cambia el texto`() {
        val standard = scheme(contrast = ColorContrast.STANDARD)
        val high = scheme(contrast = ColorContrast.HIGH)
        assertNotEquals(standard.onSurface.toArgb(), high.onSurface.toArgb())
    }

    @Test
    fun `el negro puro apaga las superficies oscuras`() {
        val amoled = scheme(dark = true, amoled = true)
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(Color.Black, amoled.surfaceContainerLowest)
    }

    @Test
    fun `la interpolacion respeta los extremos`() {
        val from = scheme(style = ColorPaletteStyle.TONAL_SPOT)
        val to = scheme(style = ColorPaletteStyle.VIBRANT)
        assertEquals(from.primary, interpolateScheme(from, to, 0f).primary)
        assertEquals(to.primary, interpolateScheme(from, to, 1f).primary)
        assertNotEquals(
            from.primary,
            interpolateScheme(from, to, 0.5f).primary,
        )
    }
}
