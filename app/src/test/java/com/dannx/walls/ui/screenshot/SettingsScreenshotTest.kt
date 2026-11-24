package com.dannx.walls.ui.screenshot

import android.content.Context
import androidx.compose.runtime.Composable
import com.dannx.walls.UiState
import com.dannx.walls.data.update.ReleaseInfo
import com.dannx.walls.data.update.UpdateError
import com.dannx.walls.data.update.UpdateRepository
import com.dannx.walls.ui.screens.SettingsScreen
import com.dannx.walls.ui.update.UpdateViewModel
import com.dannx.walls.util.stringResolver
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

/**
 * Compone Ajustes con su estado por defecto: la vista con más filas y texto
 * de la app. El ViewModel se construye en esta función normal, no dentro de
 * la composición, que es donde el lint prohíbe crearlo.
 */
private fun settingsFixture(context: Context): @Composable () -> Unit {
    val updates = UpdateViewModel(NoUpdateRepository(), context.stringResolver())
    return {
        SettingsScreen(
            state = UiState(),
            updateViewModel = updates,
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
}

/**
 * Capturas de Ajustes en los cuatro idiomas: es la pantalla con más filas y
 * texto de la app, así que es la mejor detectora de cortes en las traducciones.
 * Se usa `captureSettled` porque sus ítems entran escalonados.
 */
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-420dpi")
class SettingsScreenshotEn : ScreenshotTestBase() {
    @Test
    fun `settings in English`() = captureSettled(
        "settings_en",
        darkTheme = false,
        content = settingsFixture(RuntimeEnvironment.getApplication()),
    )
}

@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-420dpi")
class SettingsScreenshotEs : ScreenshotTestBase() {
    @Test
    fun `settings in Spanish`() = captureSettled(
        "settings_es",
        darkTheme = false,
        content = settingsFixture(RuntimeEnvironment.getApplication()),
    )
}

@Config(sdk = [34], qualifiers = "fr-w411dp-h891dp-420dpi")
class SettingsScreenshotFr : ScreenshotTestBase() {
    @Test
    fun `settings in French`() = captureSettled(
        "settings_fr",
        darkTheme = false,
        content = settingsFixture(RuntimeEnvironment.getApplication()),
    )
}

@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h891dp-420dpi")
class SettingsScreenshotPtBr : ScreenshotTestBase() {
    @Test
    fun `settings in Portuguese`() = captureSettled(
        "settings_pt-BR",
        darkTheme = false,
        content = settingsFixture(RuntimeEnvironment.getApplication()),
    )
}
