package com.novelreader.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sheet opens where the reader is, not at the top (issue #13). The list state and the scroll live
 * in the sheet body, so this composes the body on its own; the header (search field and counter) stays
 * in [ReaderScreen], because a text field keeps Robolectric from ever reporting idle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class ChapterListSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun chapters(count: Int) = (0 until count).map { i ->
        ChapterEntity(
            id = i.toLong(),
            novelId = 1L,
            title = "Capítulo $i",
            fileName = "ch_$i.html",
            orderIndex = i,
            content = "<p>x</p>"
        )
    }

    private fun show(
        chapters: List<ChapterEntity>,
        currentChapterId: Long?,
        onChapterClick: (Long) -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                ChapterListSheet(
                    chapters = chapters,
                    currentChapterId = currentChapterId,
                    novelTitle = "Novel",
                    onChapterClick = onChapterClick
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `opens at the chapter being read`() {
        show(chapters(60), currentChapterId = 45L)

        composeTestRule.onNodeWithText("Capítulo 45").assertIsDisplayed()
        composeTestRule.onNodeWithText("Capítulo 0").assertDoesNotExist()
    }

    @Test
    fun `opens at the top when the chapter being read is not in the list`() {
        show(chapters(60), currentChapterId = 999L)

        composeTestRule.onNodeWithText("Capítulo 0").assertIsDisplayed()
    }

    @Test
    fun `opens at the top when no chapter is loaded`() {
        show(chapters(60), currentChapterId = null)

        composeTestRule.onNodeWithText("Capítulo 0").assertIsDisplayed()
    }

    @Test
    fun `opens at the chapter being read in a reversed list`() {
        show(chapters(60).asReversed(), currentChapterId = 45L)

        composeTestRule.onNodeWithText("Capítulo 45").assertIsDisplayed()
        composeTestRule.onNodeWithText("Capítulo 0").assertDoesNotExist()
    }

    @Test
    fun `shows the no-matches message for an empty list`() {
        show(emptyList(), currentChapterId = 3L)

        composeTestRule.onNodeWithText("Nenhum capítulo encontrado").assertIsDisplayed()
    }

    @Test
    fun `tapping a chapter reports its id`() {
        var clicked: Long? = null
        show(chapters(10), currentChapterId = 2L, onChapterClick = { clicked = it })

        composeTestRule.onNodeWithText("Capítulo 2").performClick()

        composeTestRule.runOnUiThread { assertThat(clicked).isEqualTo(2L) }
    }
}
