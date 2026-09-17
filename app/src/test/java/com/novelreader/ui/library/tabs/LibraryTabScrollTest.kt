package com.novelreader.ui.library.tabs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.ui.library.ViewMode
import com.novelreader.ui.theme.NovelReaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp")
class LibraryTabScrollTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `rememberSaveable LazyListState persists scroll index across state restoration`() {
        val restorationTester = StateRestorationTester(composeTestRule)
        var state: LazyListState? = null
        restorationTester.setContent {
            NovelReaderTheme {
                val s = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                state = s
                SimpleLazyColumn(state = s, count = 50)
            }
        }
        composeTestRule.runOnUiThread { runBlocking { state!!.scrollToItem(15) } }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { assertThat(state!!.firstVisibleItemIndex).isEqualTo(15) }

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.runOnUiThread { assertThat(state!!.firstVisibleItemIndex).isEqualTo(15) }
    }

    // Opening a novel sends it to the top (`ORDER BY lastReadAt DESC`) while the reader is on screen, so
    // coming back used to drop the reader at the top of the library. Both views must keep the place.
    @Test
    fun `the grid keeps the reader's place across state restoration`() {
        setLibrary(viewMode = ViewMode.GRID)
    }

    @Test
    fun `the list keeps the reader's place across state restoration`() {
        setLibrary(viewMode = ViewMode.LIST)
    }

    private fun setLibrary(viewMode: ViewMode) = setLibrary(viewMode) { 0 until 40 }

    private fun setLibrary(viewMode: ViewMode, ids: () -> IntRange) {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            NovelReaderTheme {
                LibraryTab(
                    novels = ids().map { NovelEntity(id = it.toLong(), title = "Novel $it") },
                    backgroundImportState = BackgroundImportState(),
                    viewMode = viewMode,
                    onNovelClick = {},
                    onLongClick = {},
                    onToggleAutoUpdate = {},
                    onCheckForUpdates = {},
                    onResyncChapters = {},
                    onChapters = {},
                    onAddToCollection = {},
                    onToggleFavorite = {},
                    onRequestChangeCover = {},
                    onRequestCoverByUrl = {},
                    onContinueReading = {},
                    onCancelImport = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Novel 0").assertIsDisplayed()
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Novel 0").assertDoesNotExist()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Novel 30").assertIsDisplayed()
        composeTestRule.onNodeWithText("Novel 0").assertDoesNotExist()
    }

    // Opening a novel re-sorts it to the top (`ORDER BY lastReadAt DESC`). The library is not composed
    // while the reader is open (that is why the search box is empty on return), so the reorder reaches
    // it through the saved state — and the default LazyListState.Saver drags the place to the top with
    // the key of the first visible item, which is the novel that just moved.
    @Test
    fun `the list keeps the place when the opened novel moves to the top`() {
        libraryThatLeavesTheScreen(viewMode = ViewMode.LIST).let { (goToReader, comeBack, reorder) ->
            composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Novel 10").assertIsDisplayed()

            goToReader()
            reorder()
            comeBack()

            composeTestRule.onNodeWithText("Novel 10").assertDoesNotExist()
            composeTestRule.onNodeWithText("Novel 9").assertIsDisplayed()
        }
    }

    @Test
    fun `the grid keeps the place when the opened novel moves to the top`() {
        libraryThatLeavesTheScreen(viewMode = ViewMode.GRID).let { (goToReader, comeBack, reorder) ->
            composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Novel 10").assertIsDisplayed()

            goToReader()
            reorder()
            comeBack()

            composeTestRule.onNodeWithText("Novel 10").assertDoesNotExist()
        }
    }

    // The reorder reaches the library while it is still composed (it stays on screen through the
    // navigation transition, which is also why the search box is empty on return), and the default
    // item keys made the viewport follow the novel that moved instead of holding the place.
    @Test
    fun `the list holds the place when a novel moves to the top while it is on screen`() {
        composedReorder(viewMode = ViewMode.LIST)

        composeTestRule.onNodeWithText("Novel 10").assertDoesNotExist()
        composeTestRule.onNodeWithText("Novel 9").assertIsDisplayed()
    }

    @Test
    fun `the grid holds the place when a novel moves to the top while it is on screen`() {
        composedReorder(viewMode = ViewMode.GRID)

        composeTestRule.onNodeWithText("Novel 10").assertDoesNotExist()
    }

    private fun composedReorder(viewMode: ViewMode) {
        val novels = (0 until 40).map { NovelEntity(id = it.toLong(), title = "Novel $it") }
        val shown = mutableStateOf(novels)
        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryTab(
                    novels = shown.value,
                    backgroundImportState = BackgroundImportState(),
                    viewMode = viewMode,
                    onNovelClick = {},
                    onLongClick = {},
                    onToggleAutoUpdate = {},
                    onCheckForUpdates = {},
                    onResyncChapters = {},
                    onChapters = {},
                    onAddToCollection = {},
                    onToggleFavorite = {},
                    onRequestChangeCover = {},
                    onRequestCoverByUrl = {},
                    onContinueReading = {},
                    onCancelImport = {}
                )
            }
        }

        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Novel 10").assertIsDisplayed()

        shown.value = listOf(novels[10]) + novels.filter { it.id != 10L }
        composeTestRule.waitForIdle()
    }

    /** Library on screen, then "reader", then back, with the data re-sorted while it is away. */
    private fun libraryThatLeavesTheScreen(
        viewMode: ViewMode
    ): Triple<() -> Unit, () -> Unit, () -> Unit> {
        val novels = (0 until 40).map { NovelEntity(id = it.toLong(), title = "Novel $it") }
        val shown = mutableStateOf(novels)
        val onLibrary = mutableStateOf(true)
        composeTestRule.setContent {
            NovelReaderTheme {
                val holder = rememberSaveableStateHolder()
                if (onLibrary.value) {
                    holder.SaveableStateProvider("library") {
                        LibraryTab(
                            novels = shown.value,
                            backgroundImportState = BackgroundImportState(),
                            viewMode = viewMode,
                            onNovelClick = {},
                            onLongClick = {},
                            onToggleAutoUpdate = {},
                            onCheckForUpdates = {},
                            onResyncChapters = {},
                            onChapters = {},
                            onAddToCollection = {},
                            onToggleFavorite = {},
                            onRequestChangeCover = {},
                            onRequestCoverByUrl = {},
                            onContinueReading = {},
                            onCancelImport = {}
                        )
                    }
                } else {
                    Text("reader")
                }
            }
        }
        return Triple(
            { onLibrary.value = false; composeTestRule.waitForIdle() },
            { onLibrary.value = true; composeTestRule.waitForIdle() },
            { shown.value = listOf(novels[10]) + novels.filter { it.id != 10L } }
        )
    }
}

@Composable
private fun SimpleLazyColumn(state: LazyListState, count: Int) {
    LazyColumn(state = state) {
        items(count) { i ->
            androidx.compose.material3.Text("Item $i")
        }
    }
}
