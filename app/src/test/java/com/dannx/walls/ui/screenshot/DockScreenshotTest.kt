package com.dannx.walls.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.ui.components.DockItem
import com.dannx.walls.ui.components.FloatingDock
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * La barra flotante en los cuatro idiomas y en claro/oscuro, con la pestaña
 * activa más ancha que las demás. Sirve para detectar cortes en las etiquetas
 * largas (Collections/Collections/Favoritos) y contrastes flojos.
 */
@Config(qualifiers = "en-w411dp-h891dp-420dpi")
class DockScreenshotEn : DockScreenshotBase("en") {
    @Test fun `dock in English`() = dock()
    @Test fun `dock in English dark`() = dock(dark = true)
}

@Config(qualifiers = "es-w411dp-h891dp-420dpi")
class DockScreenshotEs : DockScreenshotBase("es") {
    @Test fun `dock in Spanish`() = dock()
    @Test fun `dock in Spanish dark`() = dock(dark = true)
}

@Config(qualifiers = "pt-rBR-w411dp-h891dp-420dpi")
class DockScreenshotPtBr : DockScreenshotBase("pt-BR") {
    @Test fun `dock in Portuguese`() = dock()
    @Test fun `dock in Portuguese dark`() = dock(dark = true)
}

@Config(qualifiers = "fr-w411dp-h891dp-420dpi")
class DockScreenshotFr : DockScreenshotBase("fr") {
    @Test fun `dock in French`() = dock()
    @Test fun `dock in French dark`() = dock(dark = true)
}

abstract class DockScreenshotBase(private val tag: String) : ScreenshotTestBase() {

    @Composable
    protected fun sampleDock() {
        val items = listOf(
            DockItem(
                route = "home",
                icon = Icons.Rounded.Explore,
                label = stringResource(R.string.home_section_explore),
            ),
            DockItem(
                route = "collections",
                icon = Icons.Rounded.CollectionsBookmark,
                label = stringResource(R.string.collections_title),
            ),
            DockItem(
                route = "favorites",
                icon = Icons.Rounded.FavoriteBorder,
                label = stringResource(R.string.favorites_title),
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            FloatingDock(
                items = items,
                selectedRoute = "collections",
                onNavigate = {},
            )
        }
    }

    protected fun dock(dark: Boolean = false) {
        capture("dock${darkSuffix(dark)}_$tag", darkTheme = dark) {
            sampleDock()
        }
    }

    private fun darkSuffix(dark: Boolean) = if (dark) "_dark" else ""
}
