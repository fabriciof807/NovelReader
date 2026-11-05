package com.novelreader.ui.library.tabs

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.ui.library.ViewMode
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class LibraryTabGridTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val longTitle = (1..40).joinToString(" ") { "Long" }

    private fun library(titles: List<String>) {
        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryTab(
                    novels = titles.mapIndexed { index, title ->
                        NovelEntity(id = index.toLong(), title = title)
                    },
                    backgroundImportState = BackgroundImportState(),
                    viewMode = ViewMode.GRID,
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
                    onCancelImport = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun cardBounds() =
        composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun topOf(title: String) =
        composeTestRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top.toFloat()

    @Test
    fun `the grid lays out three columns`() {
        library((0 until 6).map { "Novel $it" })

        assertThat(topOf("Novel 1")).isWithin(1f).of(topOf("Novel 0"))
        assertThat(topOf("Novel 2")).isWithin(1f).of(topOf("Novel 0"))
        assertThat(topOf("Novel 3")).isGreaterThan(topOf("Novel 0"))
        assertThat(topOf("Novel 4")).isWithin(1f).of(topOf("Novel 3"))
        assertThat(topOf("Novel 5")).isWithin(1f).of(topOf("Novel 3"))
    }

    @Test
    fun `all novel cards have the same width and height`() {
        library(listOf("Short", "A longer novel title here", longTitle, "长".repeat(20), "", "Last"))

        val cards = cardBounds()

        assertThat(cards).hasSize(6)
        cards.forEach { card ->
            assertThat(card.width.toFloat()).isWithin(1f).of(cards.first().width.toFloat())
            assertThat(card.height.toFloat()).isWithin(1f).of(cards.first().height.toFloat())
        }
    }

    @Test
    fun `long press on a card opens its menu`() {
        library(listOf("Novel"))

        composeTestRule.onNodeWithText("Novel")
            .performSemanticsAction(SemanticsActions.OnLongClick)

        composeTestRule.onNodeWithText("Alterar capa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deletar").assertIsDisplayed()
    }

    @Test
    fun `no per card options button is shown in the grid`() {
        library((0 until 3).map { "Novel $it" })

        composeTestRule.onAllNodesWithContentDescription("Opções").assertCountEquals(0)
    }

    @Test
    fun `a title with 200 characters keeps the grid aligned`() {
        val twoHundred = "L".repeat(200)
        library(listOf("Short", twoHundred, "Another"))

        val cards = cardBounds()

        assertThat(cards).hasSize(3)
        cards.forEach { assertThat(it.top).isEqualTo(cards.first().top) }
        val expectedWidth = with(composeTestRule.density) {
            val available = (400f - 16f - 12f) / 3f
            available.dp.toPx()
        }
        assertThat(cards.first().width.toFloat()).isWithin(1f).of(expectedWidth)
    }
}
