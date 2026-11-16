package com.dannx.walls.ui.screenshot

import com.dannx.walls.ui.screens.OnboardingScreen
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Capturas de la bienvenida en los cuatro idiomas. Siempre en Modo Lite: el
 * fondo animado del modo normal es infinito y no deja un frame reproducible.
 * El recorte en inglés se agrega también en oscuro para cubrir el tema.
 */
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-420dpi")
class OnboardingScreenshotEn : ScreenshotTestBase() {
    @Test
    fun `onboarding in English`() {
        capture("onboarding_en", darkTheme = false) {
            OnboardingScreen(liteMode = true, onFinish = {})
        }
    }

    @Test
    fun `onboarding in English dark`() {
        capture("onboarding_en_dark", darkTheme = true) {
            OnboardingScreen(liteMode = true, onFinish = {})
        }
    }
}

@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-420dpi")
class OnboardingScreenshotEs : ScreenshotTestBase() {
    @Test
    fun `onboarding in Spanish`() {
        capture("onboarding_es", darkTheme = false) {
            OnboardingScreen(liteMode = true, onFinish = {})
        }
    }
}

@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h891dp-420dpi")
class OnboardingScreenshotPtBr : ScreenshotTestBase() {
    @Test
    fun `onboarding in Portuguese`() {
        capture("onboarding_pt-BR", darkTheme = false) {
            OnboardingScreen(liteMode = true, onFinish = {})
        }
    }
}

@Config(sdk = [34], qualifiers = "fr-w411dp-h891dp-420dpi")
class OnboardingScreenshotFr : ScreenshotTestBase() {
    @Test
    fun `onboarding in French`() {
        capture("onboarding_fr", darkTheme = false) {
            OnboardingScreen(liteMode = true, onFinish = {})
        }
    }
}
