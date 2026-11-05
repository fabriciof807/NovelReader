package com.novelreader.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.components.APP_NAV_BAR_TAG
import com.novelreader.ui.components.AppDestination
import com.novelreader.ui.components.appNavBarItemTag
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class LibraryBottomBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val stats = LibraryStats(totalNovels = 1, totalChapters = 109, totalBookmarks = 0)

    @Test
    fun `the nav bar is shown on the library and hidden when a novel is selected`() {
        var hasSelectedNovel by mutableStateOf(false)

        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryBottomBar(
                    selectedTab = if (hasSelectedNovel) 1 else 0,
                    hasSelectedNovel = hasSelectedNovel,
                    stats = stats,
                    wallpaperActive = false,
                    onNavigate = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(APP_NAV_BAR_TAG).assertIsDisplayed()

        composeTestRule.runOnUiThread { hasSelectedNovel = true }

        composeTestRule.onNodeWithTag(APP_NAV_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun `selecting Collections shows the collections tab without navigating`() {
        var selectedTab by mutableStateOf(0)
        val navigated = mutableListOf<AppDestination>()

        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryBottomBar(
                    selectedTab = selectedTab,
                    hasSelectedNovel = false,
                    stats = stats,
                    wallpaperActive = false,
                    onNavigate = { destination ->
                        val tab = libraryDestinationTab(destination)
                        if (tab != null) selectedTab = tab else navigated += destination
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag(appNavBarItemTag(AppDestination.COLLECTIONS))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(navigated).isEmpty()
        assertThat(selectedTab).isEqualTo(1)
        composeTestRule.onNodeWithTag(appNavBarItemTag(AppDestination.COLLECTIONS)).assertIsSelected()
        composeTestRule.onNodeWithTag(appNavBarItemTag(AppDestination.LIBRARY)).assertIsNotSelected()
    }

    @Test
    fun `selecting Favorites leaves the tab alone and navigates`() {
        var selectedTab by mutableStateOf(0)
        val navigated = mutableListOf<AppDestination>()

        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryBottomBar(
                    selectedTab = selectedTab,
                    hasSelectedNovel = false,
                    stats = stats,
                    wallpaperActive = false,
                    onNavigate = { destination ->
                        val tab = libraryDestinationTab(destination)
                        if (tab != null) selectedTab = tab else navigated += destination
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag(appNavBarItemTag(AppDestination.FAVORITES))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(navigated).containsExactly(AppDestination.FAVORITES)
        assertThat(selectedTab).isEqualTo(0)
    }
}
