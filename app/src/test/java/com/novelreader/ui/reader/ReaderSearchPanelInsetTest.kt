package com.novelreader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class ReaderSearchPanelInsetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val results = listOf(
        ChapterEntity(id = 1, novelId = 1, title = "Alpha chapter", content = "needle alpha", orderIndex = 0, fileName = "a")
    )

    private fun setScreen(searchActive: Boolean) {
        composeTestRule.setContent {
            NovelReaderTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (searchActive) {
                        SearchResultsPanel(
                            query = "needle",
                            results = results,
                            onResultClick = { _, _ -> },
                            onClose = {},
                            topPadding = searchPanelTopPadding(searchActive)
                        )
                    }
                    ReaderTopBar(
                        title = "Chapter 11",
                        isSearchActive = searchActive,
                        searchQuery = "needle",
                        onSearchQueryChange = {},
                        onBack = {},
                        onCloseSearch = {},
                        onActivateSearch = {},
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }
    }

    @Test
    fun `the first result starts below the search bar`() {
        setScreen(searchActive = true)

        val result = composeTestRule.onNodeWithText("Alpha chapter", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertThat(result.top).isAtLeast(READER_SEARCH_BAR_HEIGHT)
    }

    @Test
    fun `with the search closed the panel adds no padding`() {
        setScreen(searchActive = false)

        composeTestRule.onNodeWithText("Alpha chapter").assertDoesNotExist()
        assertThat(searchPanelTopPadding(searchActive = false)).isEqualTo(0.dp)
    }
}
