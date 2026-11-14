package com.dannx.walls.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.ui.screens.HomeTagFilterRow
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
        show(state = UiState(tagCatalog = listOf("arte", "cielo")), onTagChange = { received = it })

        rule.onNodeWithText("#cielo").performClick()

        rule.runOnIdle { assertEquals("cielo", received) }
    }

    @Test
    fun aCompoundTagSendsTheStoredValue() {
        var received: String? = null
        show(state = UiState(tagCatalog = listOf("playa #sol")), onTagChange = { received = it })

        rule.onNodeWithText("#playa").performClick()

        rule.runOnIdle { assertEquals("playa #sol", received) }
    }

    @Test
    fun theSelectionIsMarkedCaseInsensitive() {
        show(
            state = UiState(tagCatalog = listOf("Cielo"), tags = setOf("cielo")),
            onTagChange = {},
        )

        rule.onNodeWithText("#Cielo").assertIsSelected()
    }

    @Test
    fun theAllChipClearsTheSelection() {
        var received: String? = "sin tocar"
        show(
            state = UiState(tagCatalog = listOf("arte", "cielo"), tags = setOf("cielo")),
            onTagChange = { received = it },
        )

        rule.onNodeWithText(context.getString(R.string.filter_all_tags)).performClick()

        rule.runOnIdle { assertEquals(null, received) }
    }

    private fun show(state: UiState, onTagChange: (String?) -> Unit) {
        rule.setContent {
            DannXTheme(darkTheme = false) {
                HomeTagFilterRow(
                    tags = state.availableTags,
                    allSelected = state.tags.isEmpty(),
                    isSelected = state::isTagSelected,
                    filterValue = state::tagFilterValue,
                    onTagChange = onTagChange,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }
}
