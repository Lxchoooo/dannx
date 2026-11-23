package com.dannx.walls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los ajustes de apariencia deben arrancar con valores previsibles: el tema
 * sigue al sistema, el color sale del fondo y no se altera el contraste.
 */
class AppearanceStoreTest {

    @Test
    fun `el store en memoria arranca con los valores de fabrica`() {
        val store = InMemoryAppearanceStore()
        assertEquals(ThemeMode.SYSTEM, store.themeMode())
        assertFalse(store.liteMode())
        assertTrue(store.openEditorOnApply())
        assertEquals(WALLPAPER_COLOR_SEED, store.colorSeed())
        assertEquals(ColorPaletteStyle.TONAL_SPOT, store.paletteStyle())
        assertEquals(ColorContrast.STANDARD, store.contrastLevel())
        assertFalse(store.amoled())
    }

    @Test
    fun `el store en memoria conserva los cambios`() {
        val store = InMemoryAppearanceStore()
        store.saveThemeMode(ThemeMode.DARK)
        store.saveLiteMode(true)
        store.saveOpenEditorOnApply(false)
        store.saveColorSeed("#22C7D6")
        store.savePaletteStyle(ColorPaletteStyle.CMF)
        store.saveContrastLevel(ColorContrast.HIGH)
        store.saveAmoled(true)

        assertEquals(ThemeMode.DARK, store.themeMode())
        assertTrue(store.liteMode())
        assertFalse(store.openEditorOnApply())
        assertEquals("#22C7D6", store.colorSeed())
        assertEquals(ColorPaletteStyle.CMF, store.paletteStyle())
        assertEquals(ColorContrast.HIGH, store.contrastLevel())
        assertTrue(store.amoled())
    }

    @Test
    fun `el reset vuelve a los valores de fabrica`() {
        val store = InMemoryAppearanceStore(
            theme = ThemeMode.DARK,
            lite = true,
            openEditor = false,
            seed = "#FF6F61",
            style = ColorPaletteStyle.RAINBOW,
            contrast = ColorContrast.MEDIUM,
            pureBlack = true,
        )
        store.reset()

        assertEquals(ThemeMode.SYSTEM, store.themeMode())
        assertFalse(store.liteMode())
        assertTrue(store.openEditorOnApply())
        assertEquals(WALLPAPER_COLOR_SEED, store.colorSeed())
        assertEquals(ColorPaletteStyle.TONAL_SPOT, store.paletteStyle())
        assertEquals(ColorContrast.STANDARD, store.contrastLevel())
        assertFalse(store.amoled())
    }

    @Test
    fun `los nombres desconocidos caen en los valores por defecto`() {
        assertEquals(ColorPaletteStyle.TONAL_SPOT, ColorPaletteStyle.fromName(null))
        assertEquals(ColorPaletteStyle.TONAL_SPOT, ColorPaletteStyle.fromName("OTRO"))
        assertEquals(ColorPaletteStyle.CMF, ColorPaletteStyle.fromName("CMF"))
        assertEquals(ColorContrast.STANDARD, ColorContrast.fromName(null))
        assertEquals(ColorContrast.HIGH, ColorContrast.fromName("HIGH"))
    }

    @Test
    fun `el contraste usa los niveles de Material`() {
        assertEquals(0.0, ColorContrast.STANDARD.value, 0.0)
        assertEquals(0.5, ColorContrast.MEDIUM.value, 0.0)
        assertEquals(1.0, ColorContrast.HIGH.value, 0.0)
    }
}
