package com.dannx.walls.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.screens.FavoritesScreen
import com.dannx.walls.ui.theme.DannXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun longPressStartsSelectionAndRemoveCallsBackWithTheIds() {
        var removed: Set<String> = emptySet()
        rule.setContent {
            DannXTheme(darkTheme = false) {
                FavoritesScreen(
                    state = UiState(
                        wallpapers = listOf(wallpaper("a"), wallpaper("b")),
                        favorites = setOf("a", "b"),
                    ),
                    onOpenWallpaper = {},
                    onToggleFavorite = {},
                    onExplore = {},
                    onRemoveFavorites = { removed = it },
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)

        rule.onNodeWithTag("wallpaper-a").performTouchInput { longClick() }
        rule.onNodeWithContentDescription(context.getString(R.string.selection_remove))
            .performClick()

        rule.runOnIdle { assertEquals(setOf("a"), removed) }
    }

    @Test
    fun selectAllAddsEveryVisibleWallpaper() {
        var removed: Set<String> = emptySet()
        rule.setContent {
            DannXTheme(darkTheme = false) {
                FavoritesScreen(
                    state = UiState(
                        wallpapers = listOf(wallpaper("a"), wallpaper("b")),
                        favorites = setOf("a", "b"),
                    ),
                    onOpenWallpaper = {},
                    onToggleFavorite = {},
                    onExplore = {},
                    onRemoveFavorites = { removed = it },
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)

        rule.onNodeWithTag("wallpaper-a").performTouchInput { longClick() }
        rule.onNodeWithContentDescription(context.getString(R.string.selection_select_all))
            .performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.selection_remove))
            .performClick()

        rule.runOnIdle { assertEquals(setOf("a", "b"), removed) }
    }

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        title = "Fondo $id",
        category = "Abstracto",
        imageUrl = "https://example.com/$id.jpg",
    )
}
