package com.novelreader.ui.library.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.ui.library.ViewMode
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A novel sitting in the import queue is visible in the library itself, under the waiting label
 * (issue #5). It used to be reachable only through the import banner's queue sheet, which renders
 * while a job is running — so these compose the tab with `running = false`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class LibraryTabQueuedImportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val waitingLabel = "Aguardando importação"

    @Test
    fun `a queued novel that is not in the library yet is a row with the waiting label`() {
        library(
            novels = emptyList(),
            state = BackgroundImportState(
                running = false,
                pendingInQueue = 1,
                queuedNovelTitles = listOf("Novel na fila")
            )
        )

        composeTestRule.onNodeWithText("Novel na fila").assertIsDisplayed()
        composeTestRule.onNodeWithText(waitingLabel).assertIsDisplayed()
    }

    @Test
    fun `several queued novels each get a row`() {
        library(
            novels = emptyList(),
            state = BackgroundImportState(
                running = false,
                pendingInQueue = 2,
                queuedNovelTitles = listOf("Primeira", "Segunda")
            )
        )

        composeTestRule.onNodeWithText("Primeira").assertIsDisplayed()
        composeTestRule.onNodeWithText("Segunda").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(waitingLabel).assertCountEquals(2)
    }

    @Test
    fun `a queued novel already in the library is labelled, not repeated as a second row`() {
        library(
            novels = listOf(NovelEntity(id = 1L, title = "Novel na fila")),
            state = BackgroundImportState(
                running = false,
                pendingInQueue = 1,
                queuedNovelTitles = listOf("Novel na fila")
            )
        )

        composeTestRule.onAllNodesWithText("Novel na fila").assertCountEquals(1)
        composeTestRule.onAllNodesWithText(waitingLabel).assertCountEquals(1)
    }

    private fun library(novels: List<NovelEntity>, state: BackgroundImportState) {
        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryTab(
                    novels = novels,
                    backgroundImportState = state,
                    viewMode = ViewMode.LIST,
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
        composeTestRule.waitForIdle()
    }
}
