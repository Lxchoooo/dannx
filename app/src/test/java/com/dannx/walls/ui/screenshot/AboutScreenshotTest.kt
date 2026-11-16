package com.dannx.walls.ui.screenshot

import com.dannx.walls.ui.screens.DecoyScreen
import com.dannx.walls.ui.screens.AboutScreen
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Capturas de las pantallas estáticas: Sobre y la pantalla señuelo del acceso
 * secreto al Studio. Ambas resuelven todo su texto por recursos, así que
 * cubren los cuatro idiomas.
 */
@Config(sdk = [34], qualifiers = "en")
class AboutScreenshotEn : ScreenshotTestBase() {
    @Test fun `about in English`() = capture("about_en", darkTheme = false) { AboutScreen(onBack = {}) }
    @Test fun `decoy in English`() = capture("decoy_en", darkTheme = false) { DecoyScreen(onBack = {}) }
}

@Config(sdk = [34], qualifiers = "es")
class AboutScreenshotEs : ScreenshotTestBase() {
    @Test fun `about in Spanish`() = capture("about_es", darkTheme = false) { AboutScreen(onBack = {}) }
    @Test fun `decoy in Spanish`() = capture("decoy_es", darkTheme = false) { DecoyScreen(onBack = {}) }
}

@Config(sdk = [34], qualifiers = "pt-rBR")
class AboutScreenshotPtBr : ScreenshotTestBase() {
    @Test fun `about in Portuguese`() = capture("about_pt-BR", darkTheme = false) { AboutScreen(onBack = {}) }
    @Test fun `decoy in Portuguese`() = capture("decoy_pt-BR", darkTheme = false) { DecoyScreen(onBack = {}) }
}

@Config(sdk = [34], qualifiers = "fr")
class AboutScreenshotFr : ScreenshotTestBase() {
    @Test fun `about in French`() = capture("about_fr", darkTheme = false) { AboutScreen(onBack = {}) }
    @Test fun `decoy in French`() = capture("decoy_fr", darkTheme = false) { DecoyScreen(onBack = {}) }
}
