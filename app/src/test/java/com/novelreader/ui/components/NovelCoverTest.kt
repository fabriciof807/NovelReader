package com.novelreader.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.ui.library.components.NovelImportOverlay
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class NovelCoverTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val longTitle = (1..40).joinToString(" ") { "Long" }

    private fun novel(title: String) = NovelEntity(
        title = title,
        sourceFolder = "",
        totalChapters = 12
    )

    @Test
    fun `a one-line and a three-line title produce the same card height`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                Row {
                    NovelCover(
                        novel = novel("Short"),
                        readProgress = 0f,
                        newChapterCount = 0,
                        importOverlay = NovelImportOverlay.None,
                        onClick = {},
                        onLongClick = {},
                        modifier = Modifier.width(120.dp).testTag("shortCover")
                    )
                    NovelCover(
                        novel = novel(longTitle),
                        readProgress = 0f,
                        newChapterCount = 0,
                        importOverlay = NovelImportOverlay.None,
                        onClick = {},
                        onLongClick = {},
                        modifier = Modifier.width(120.dp).testTag("longCover")
                    )
                }
            }
        }

        val short = composeTestRule.onNodeWithTag("shortCover")
            .fetchSemanticsNode().boundsInRoot.height
        val long = composeTestRule.onNodeWithTag("longCover")
            .fetchSemanticsNode().boundsInRoot.height

        assertThat(long).isWithin(1f).of(short)
    }

    @Test
    fun `title longer than two lines is ellipsized`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                NovelCover(
                    novel = novel(longTitle),
                    readProgress = 0f,
                    newChapterCount = 0,
                    importOverlay = NovelImportOverlay.None,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.width(120.dp)
                )
            }
        }

        composeTestRule.onNodeWithText(longTitle).assertIsDisplayed()

        val title = composeTestRule.onNodeWithTag(NOVEL_COVER_TITLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        title.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)

        assertThat(layouts).hasSize(1)
        assertThat(layouts.first().layoutInput.maxLines).isEqualTo(2)
        assertThat(layouts.first().lineCount).isAtMost(2)
    }

    @Test
    fun `blank title still renders at the same height`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                Row {
                    NovelCover(
                        novel = novel("Short"),
                        readProgress = 0f,
                        newChapterCount = 0,
                        importOverlay = NovelImportOverlay.None,
                        onClick = {},
                        onLongClick = {},
                        modifier = Modifier.width(120.dp).testTag("titledCover")
                    )
                    NovelCover(
                        novel = novel("   "),
                        readProgress = 0f,
                        newChapterCount = 0,
                        importOverlay = NovelImportOverlay.None,
                        onClick = {},
                        onLongClick = {},
                        modifier = Modifier.width(120.dp).testTag("blankCover")
                    )
                }
            }
        }

        val titled = composeTestRule.onNodeWithTag("titledCover")
            .fetchSemanticsNode().boundsInRoot.height
        val blank = composeTestRule.onNodeWithTag("blankCover")
            .fetchSemanticsNode().boundsInRoot.height

        assertThat(blank).isWithin(1f).of(titled)
    }

    @Test
    fun `title with 200 CJK characters keeps the card width`() {
        val cjkTitle = "长".repeat(200)
        composeTestRule.setContent {
            NovelReaderTheme {
                NovelCover(
                    novel = novel(cjkTitle),
                    readProgress = 0f,
                    newChapterCount = 0,
                    importOverlay = NovelImportOverlay.None,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.width(120.dp).testTag("cover")
                )
            }
        }

        val width = with(composeTestRule.density) { 120.dp.toPx() }
        val card = composeTestRule.onNodeWithTag("cover")
            .fetchSemanticsNode().boundsInRoot
        val title = composeTestRule.onNodeWithTag(NOVEL_COVER_TITLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertThat(card.width).isWithin(1f).of(width)
        assertThat(title.width).isAtMost(card.width + 1f)
    }

    @Test
    fun `progress bar is absent when readProgress is 0`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                NovelCover(
                    novel = novel("Short"),
                    readProgress = 0f,
                    newChapterCount = 0,
                    importOverlay = NovelImportOverlay.None,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.width(120.dp)
                )
            }
        }

        composeTestRule.onNodeWithTag(NOVEL_COVER_PROGRESS_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `progress bar shows when the novel has been read`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                NovelCover(
                    novel = novel("Short"),
                    readProgress = 0.4f,
                    newChapterCount = 0,
                    importOverlay = NovelImportOverlay.None,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.width(120.dp)
                )
            }
        }

        composeTestRule.onNodeWithTag(NOVEL_COVER_PROGRESS_TAG, useUnmergedTree = true).assertExists()
    }
}
