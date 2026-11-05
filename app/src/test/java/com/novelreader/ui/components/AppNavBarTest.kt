package com.novelreader.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.AppDimens
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class AppNavBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders four items with labels from the string resources`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                AppNavBar(selected = AppDestination.LIBRARY, onSelect = {})
            }
        }

        composeTestRule.onNodeWithText("Biblioteca").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coleções").assertIsDisplayed()
        composeTestRule.onNodeWithText("Favoritos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Configurações").assertIsDisplayed()
    }

    @Test
    fun `selected item exposes selected true and the others false`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                AppNavBar(selected = AppDestination.COLLECTIONS, onSelect = {})
            }
        }

        AppDestination.entries.forEach { destination ->
            val node = composeTestRule.onNodeWithTag(appNavBarItemTag(destination))
            if (destination == AppDestination.COLLECTIONS) {
                node.assertIsSelected()
            } else {
                node.assertIsNotSelected()
            }
        }
    }

    @Test
    fun `clicking an item calls onSelect with its destination`() {
        val selected = mutableListOf<AppDestination>()
        composeTestRule.setContent {
            NovelReaderTheme {
                AppNavBar(selected = AppDestination.LIBRARY, onSelect = { selected += it })
            }
        }

        AppDestination.entries.forEach { destination ->
            composeTestRule.onNodeWithTag(appNavBarItemTag(destination))
                .performSemanticsAction(SemanticsActions.OnClick)
        }

        assertThat(selected).containsExactly(
            AppDestination.LIBRARY,
            AppDestination.COLLECTIONS,
            AppDestination.FAVORITES,
            AppDestination.SETTINGS
        ).inOrder()
    }

    @Test
    fun `bar height is at most the nav bar token plus the navigation bar inset`() {
        var insetPx = 0f
        composeTestRule.setContent {
            val density = LocalDensity.current
            val navigationBarInsets = WindowInsets.navigationBars
            SideEffect { insetPx = navigationBarInsets.getBottom(density).toFloat() }
            NovelReaderTheme {
                AppNavBar(
                    selected = AppDestination.LIBRARY,
                    onSelect = {},
                    modifier = Modifier.testTag(APP_NAV_BAR_TAG)
                )
            }
        }

        val height = composeTestRule.onNodeWithTag(APP_NAV_BAR_TAG)
            .fetchSemanticsNode().boundsInRoot.height
        val tokenPx = with(composeTestRule.density) { AppDimens.NavBarHeight.toPx() }

        assertThat(height).isAtLeast(tokenPx - 1f)
        assertThat(height).isAtMost(tokenPx + insetPx + 1f)
    }
}
