package com.dannx.walls.ui.screenshot

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeTestRule
import androidx.compose.ui.test.onRoot
import com.dannx.walls.UiState
import com.dannx.walls.data.update.ReleaseInfo
import com.dannx.walls.data.update.UpdateError
import com.dannx.walls.data.update.UpdateRepository
import com.dannx.walls.ui.screens.SettingsScreen
import com.dannx.walls.ui.theme.DannXTheme
import com.dannx.walls.ui.update.UpdateViewModel
import com.dannx.walls.util.stringResolver
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Repo de updates sin versiones: la consulta silenciosa que hace Ajustes al
 * abrirse termina sin cambiar el estado, así la pantalla no compone el
 * diálogo de actualización.
 */
private class NoUpdateRepository : UpdateRepository {
    override suspend fun latestRelease(): ReleaseInfo = throw UpdateError.NoRelease

    override suspend fun downloadVerified(
        release: ReleaseInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = throw UpdateError.NoRelease
}

/** Ajustes con su estado por defecto: la vista con más filas y texto de la app. */
@Composable
private fun SettingsFixture(context: Context) {
    SettingsScreen(
        state = UiState(),
        updateViewModel = UpdateViewModel(NoUpdateRepository(), context.stringResolver()),
        onBack = {},
        onThemeModeChange = {},
        onLiteModeChange = {},
        onOpenEditorOnApplyChange = {},
        onColorSeedChange = {},
        onPaletteStyleChange = {},
        onContrastLevelChange = {},
        onAmoledChange = {},
        onRefreshCacheSize = {},
        onClearCache = {},
        onOpenAbout = {},
        onResetAppearance = {},
        onSignOut = {},
    )
}

/**
 * Captura Ajustes pasando el reloj para que termine la entrada escalonada de
 * los ítems: sin esto los items se capturarían invisibles. Se detiene el
 * reloj a propósito para que el avance sea determinista entre corridas.
 */
private fun captureSettings(rule: ComposeTestRule, name: String) {
    rule.mainClock.autoAdvance = false
    rule.setContent {
        DannXTheme(darkTheme = false, dynamicColor = false) {
            Surface(
                modifier = Modifier,
                color = MaterialTheme.colorScheme.background,
            ) {
                SettingsFixture(RuntimeEnvironment.getApplication<Application>())
            }
        }
    }
    rule.mainClock.advanceTimeBy(1_500L)
    rule.waitForIdle()
    rule.onRoot().captureRoboImage("$name.png")
}

/**
 * Capturas de Ajustes en los cuatro idiomas: es la pantalla con más filas y
 * texto de la app, así que es la mejor detectora de cortes en las traducciones.
 */
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-420dpi")
class SettingsScreenshotEn : ScreenshotTestBase() {
    @Test fun `settings in English`() = captureSettings(composeRule, "settings_en")
}

@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-420dpi")
class SettingsScreenshotEs : ScreenshotTestBase() {
    @Test fun `settings in Spanish`() = captureSettings(composeRule, "settings_es")
}

@Config(sdk = [34], qualifiers = "fr-w411dp-h891dp-420dpi")
class SettingsScreenshotFr : ScreenshotTestBase() {
    @Test fun `settings in French`() = captureSettings(composeRule, "settings_fr")
}

@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h891dp-420dpi")
class SettingsScreenshotPtBr : ScreenshotTestBase() {
    @Test fun `settings in Portuguese`() = captureSettings(composeRule, "settings_pt-BR")
}
