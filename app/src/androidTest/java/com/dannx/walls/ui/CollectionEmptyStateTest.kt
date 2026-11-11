package com.dannx.walls.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.ui.screens.CollectionDetailScreen
import com.dannx.walls.ui.theme.DannXTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionEmptyStateTest {
    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun publicEmptyCollectionOnlyShowsTheTitle() {
        val body = context.getString(R.string.collection_empty_body)
        showCollection(isPublic = true)

        rule.onNodeWithText(context.getString(R.string.collection_empty_title))
            .assertIsDisplayed()
        rule.onNodeWithText(body).assertDoesNotExist()
    }

    @Test
    fun privateEmptyCollectionKeepsTheHelpText() {
        val body = context.getString(R.string.collection_empty_body)
        showCollection(isPublic = false)

        rule.onNodeWithText(context.getString(R.string.collection_empty_title))
            .assertIsDisplayed()
        rule.onNodeWithText(body).assertIsDisplayed()
    }

    private fun showCollection(isPublic: Boolean) {
        rule.setContent {
            DannXTheme(darkTheme = false) {
                CollectionDetailScreen(
                    state = UiState(isDemo = false, session = null),
                    collection = WallpaperCollection(
                        id = "collection-test",
                        name = "Prueba",
                        isPublic = isPublic,
                        wallpaperIds = emptyList(),
                    ),
                    items = emptyList(),
                    onBack = {},
                    onOpenWallpaper = {},
                    onToggleFavorite = {},
                    onRemove = {},
                    onDelete = {},
                    onExplore = {},
                )
            }
        }
    }
}
