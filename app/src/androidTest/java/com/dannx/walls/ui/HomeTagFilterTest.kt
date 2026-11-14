package com.dannx.walls.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.screens.HomeScreen
import com.dannx.walls.ui.theme.DannXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeTagFilterTest {
    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun clickingATagChipSendsTheTag() {
        var received: String? = null
        show(tags = emptySet(), onTagChange = { received = it })

        rule.onNodeWithText("#cielo").performClick()

        rule.runOnIdle { assertEquals("cielo", received) }
    }

    @Test
    fun theSelectedTagIsMarked() {
        show(tags = setOf("cielo"), onTagChange = {})

        rule.onNodeWithText("#cielo").assertIsSelected()
    }

    @Test
    fun theAllChipClearsTheSelection() {
        var received: String? = "sin tocar"
        show(tags = setOf("cielo"), onTagChange = { received = it })

        rule.onNodeWithText(context.getString(R.string.filter_all_tags)).performClick()

        rule.runOnIdle { assertEquals(null, received) }
    }

    private fun show(tags: Set<String>, onTagChange: (String?) -> Unit) {
        rule.setContent {
            DannXTheme(darkTheme = false) {
                HomeScreen(
                    state = UiState(
                        wallpapers = listOf(wallpaper()),
                        tagCatalog = listOf("arte", "cielo"),
                        tags = tags,
                    ),
                    onQueryChange = {},
                    onCategoryChange = {},
                    onTagChange = onTagChange,
                    onRefresh = {},
                    onOpenWallpaper = {},
                    onToggleFavorite = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun wallpaper() = Wallpaper(
        id = "a",
        title = "Fondo A",
        category = "Abstracto",
        imageUrl = "https://example.com/a.jpg",
    )
}
