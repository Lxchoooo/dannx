package com.dannx.walls.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dannx.walls.R
import com.dannx.walls.ui.components.EmptyState
import com.dannx.walls.ui.components.ErrorState
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Estados vacío y de error en los cuatro idiomas y en claro/oscuro. Son las
 * vistas con más texto de la app, así que son el mejor detector de cortes o
 * desbordes cuando entra una traducción nueva.
 *
 * El tag del idioma entra por el constructor: es lo que distingue los dorados
 * de cada variante (`empty_state_fr.png`, `empty_state_fr_dark.png`, …).
 */
@Config(sdk = [34], qualifiers = "en")
class StateScreenshotEn : StateScreenshotBase("en") {
    @Test fun `empty state in English`() = emptyState()
    @Test fun `empty state in English dark`() = emptyState(dark = true)
    @Test fun `error state in English`() = errorState()
    @Test fun `error state in English dark`() = errorState(dark = true)
}

@Config(sdk = [34], qualifiers = "es")
class StateScreenshotEs : StateScreenshotBase("es") {
    @Test fun `empty state in Spanish`() = emptyState()
    @Test fun `empty state in Spanish dark`() = emptyState(dark = true)
    @Test fun `error state in Spanish`() = errorState()
    @Test fun `error state in Spanish dark`() = errorState(dark = true)
}

@Config(sdk = [34], qualifiers = "pt-rBR")
class StateScreenshotPtBr : StateScreenshotBase("pt-BR") {
    @Test fun `empty state in Portuguese`() = emptyState()
    @Test fun `empty state in Portuguese dark`() = emptyState(dark = true)
    @Test fun `error state in Portuguese`() = errorState()
    @Test fun `error state in Portuguese dark`() = errorState(dark = true)
}

@Config(sdk = [34], qualifiers = "fr")
class StateScreenshotFr : StateScreenshotBase("fr") {
    @Test fun `empty state in French`() = emptyState()
    @Test fun `empty state in French dark`() = emptyState(dark = true)
    @Test fun `error state in French`() = errorState()
    @Test fun `error state in French dark`() = errorState(dark = true)
}

abstract class StateScreenshotBase(private val tag: String) : ScreenshotTestBase() {

    @Composable
    protected fun sampleEmptyState() = EmptyState(
        title = stringResource(R.string.state_empty_favorites_title),
        message = stringResource(R.string.state_empty_favorites_body),
        actionLabel = stringResource(R.string.state_explore),
        onAction = {},
    )

    @Composable
    protected fun sampleErrorState() = ErrorState(
        message = stringResource(R.string.error_offline),
        onRetry = {},
    )

    protected fun emptyState(dark: Boolean = false) {
        capture("empty_state${darkSuffix(dark)}_$tag", darkTheme = dark) {
            sampleEmptyState()
        }
    }

    protected fun errorState(dark: Boolean = false) {
        capture("error_state${darkSuffix(dark)}_$tag", darkTheme = dark) {
            sampleErrorState()
        }
    }

    private fun darkSuffix(dark: Boolean) = if (dark) "_dark" else ""
}
