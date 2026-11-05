package com.novelreader.ui.library.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.library.LibraryBrowseState
import com.novelreader.ui.library.NovelFilter
import com.novelreader.ui.library.SortOrder
import com.novelreader.ui.library.ViewMode
import com.novelreader.ui.theme.AppDimens
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class LibraryTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun topBar(
        browse: LibraryBrowseState = LibraryBrowseState(),
        viewMode: ViewMode = ViewMode.GRID,
        onSortSelected: (SortOrder) -> Unit = {},
        onViewModeChange: (ViewMode) -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryTopBar(
                    browse = browse,
                    viewMode = viewMode,
                    onSortSelected = onSortSelected,
                    onViewModeChange = onViewModeChange,
                    wallpaperActive = false,
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `title, filters and search share one row within the bar height`() {
        topBar()

        val bar = composeTestRule.onNodeWithTag(LIBRARY_TOP_BAR_TAG)
            .fetchSemanticsNode().boundsInRoot
        val title = composeTestRule.onNodeWithText("Biblioteca").fetchSemanticsNode().boundsInRoot
        val filter = composeTestRule.onNodeWithText("Todos").fetchSemanticsNode().boundsInRoot
        val search = composeTestRule.onNodeWithContentDescription("Buscar")
            .fetchSemanticsNode().boundsInRoot

        listOf(title, filter, search).forEach { node ->
            assertThat(node.top.toFloat()).isAtLeast(bar.top.toFloat())
            assertThat(node.bottom.toFloat()).isAtMost(bar.bottom.toFloat())
        }
        listOf(filter, search).forEach { node ->
            assertThat(node.center.y.toFloat()).isWithin(1f).of(title.center.y.toFloat())
        }
        val barHeight = with(composeTestRule.density) { AppDimens.TopBarHeight.toPx() }
        assertThat((bar.bottom - bar.top).toFloat()).isAtMost(barHeight)
    }

    @Test
    fun `the favorite filter is an icon only star with a content description`() {
        topBar()

        composeTestRule.onNodeWithContentDescription("Favoritas").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Favoritas").assertCountEquals(0)
    }

    @Test
    fun `sort and view mode live in the overflow menu`() {
        topBar()

        composeTestRule.onNodeWithContentDescription("Opções").performClick()

        composeTestRule.onNodeWithText("Título").assertIsDisplayed()
        composeTestRule.onNodeWithText("Data").assertIsDisplayed()
        composeTestRule.onNodeWithText("Última leitura").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lista").assertIsDisplayed()
    }

    @Test
    fun `selecting a filter updates browse filterChip`() {
        val browse = LibraryBrowseState()
        topBar(browse = browse)

        composeTestRule.onNodeWithText("Lendo").performClick()
        assertThat(browse.filterChip).isEqualTo(NovelFilter.READING)

        composeTestRule.onNodeWithText("Fim").performClick()
        assertThat(browse.filterChip).isEqualTo(NovelFilter.COMPLETED)

        composeTestRule.onNodeWithContentDescription("Favoritas").performClick()
        assertThat(browse.filterChip).isEqualTo(NovelFilter.FAVORITES)
    }

    @Test
    fun `selecting a sort option calls onSortSelected`() {
        val sorts = mutableListOf<SortOrder>()
        topBar(onSortSelected = { sorts += it })

        composeTestRule.onNodeWithContentDescription("Opções").performClick()
        composeTestRule.onNodeWithText("Data").performClick()

        assertThat(sorts).containsExactly(SortOrder.CREATED_AT)
    }

    @Test
    fun `every filter has a touch target of at least 48dp`() {
        topBar()

        val minPx = with(composeTestRule.density) { 48.dp.toPx() }
        listOf("Todos", "Lendo", "Concluídos").forEach { description ->
            val bounds = composeTestRule.onNodeWithContentDescription(description)
                .fetchSemanticsNode().boundsInRoot
            assertThat(bounds.height).isAtLeast(minPx - 1f)
            assertThat(bounds.width).isAtLeast(minPx - 1f)
        }
    }
}
