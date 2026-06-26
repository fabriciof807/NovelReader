package com.novelreader.ui.library.tabs

import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.ui.library.ChapterSortOrder
import com.novelreader.ui.library.LibraryViewModel
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp")
class ChaptersTabScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ChaptersTab renders without crashing given initialScroll and onScroll`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ChaptersTab(
                    novelId = 1L,
                    chapters = (0L..29L).map {
                        ChapterEntity(
                            id = it,
                            novelId = 1L,
                            title = "Ch $it",
                            fileName = "ch_$it.html",
                            orderIndex = it.toInt(),
                            content = "<p>x</p>"
                        )
                    },
                    bookmarkCounts = emptyMap(),
                    sortOrder = ChapterSortOrder.ASCENDING,
                    onChapterClick = { },
                    initialScroll = LibraryViewModel.ChaptersScrollState(
                        firstVisibleItemIndex = 7,
                        firstVisibleItemScrollOffset = 0
                    ),
                    onScroll = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `ChaptersTab renders without crashing when initialScroll is null`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ChaptersTab(
                    novelId = 2L,
                    chapters = (0L..9L).map {
                        ChapterEntity(
                            id = it,
                            novelId = 2L,
                            title = "Ch $it",
                            fileName = "ch_$it.html",
                            orderIndex = it.toInt(),
                            content = "<p>x</p>"
                        )
                    },
                    bookmarkCounts = emptyMap(),
                    sortOrder = ChapterSortOrder.DESCENDING,
                    onChapterClick = { },
                    initialScroll = null,
                    onScroll = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `ChaptersTab accepts onScroll lambda without crashing`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ChaptersTab(
                    novelId = 3L,
                    chapters = (0L..49L).map {
                        ChapterEntity(
                            id = it,
                            novelId = 3L,
                            title = "Ch $it",
                            fileName = "ch_$it.html",
                            orderIndex = it.toInt(),
                            content = "<p>x</p>"
                        )
                    },
                    bookmarkCounts = emptyMap(),
                    sortOrder = ChapterSortOrder.ASCENDING,
                    onChapterClick = { },
                    initialScroll = null,
                    onScroll = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `ChaptersTab renders failed chapters section alongside initialScroll`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ChaptersTab(
                    novelId = 4L,
                    chapters = (0L..4L).map {
                        ChapterEntity(
                            id = it,
                            novelId = 4L,
                            title = "Ch $it",
                            fileName = "ch_$it.html",
                            orderIndex = it.toInt(),
                            content = "<p>x</p>"
                        )
                    },
                    bookmarkCounts = emptyMap(),
                    sortOrder = ChapterSortOrder.ASCENDING,
                    onChapterClick = { },
                    initialScroll = LibraryViewModel.ChaptersScrollState(2, 0),
                    onScroll = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `ChaptersTab renders empty state without crashing with initialScroll params`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ChaptersTab(
                    novelId = 5L,
                    chapters = emptyList(),
                    bookmarkCounts = emptyMap(),
                    sortOrder = ChapterSortOrder.ASCENDING,
                    onChapterClick = { },
                    initialScroll = LibraryViewModel.ChaptersScrollState(0, 0),
                    onScroll = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
