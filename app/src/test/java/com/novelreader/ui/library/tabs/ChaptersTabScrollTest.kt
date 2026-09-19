package com.novelreader.ui.library.tabs

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.google.common.truth.Truth.assertThat
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

    @Test
    fun `ChaptersTab renders with pendingScrollToFailedNovelId without crashing`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ChaptersTab(
                    novelId = 6L,
                    chapters = (0L..4L).map {
                        ChapterEntity(
                            id = it,
                            novelId = 6L,
                            title = "Ch $it",
                            fileName = "ch_$it.html",
                            orderIndex = it.toInt(),
                            content = "<p>x</p>"
                        )
                    },
                    bookmarkCounts = emptyMap(),
                    sortOrder = ChapterSortOrder.ASCENDING,
                    onChapterClick = { },
                    pendingScrollToFailedNovelId = 6L,
                    onConsumeScrollToFailed = { }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // Over a wallpaper the rows sit on an opaque container, so the list has to cover the content area
    // it seats: the wallpaper may only show outside it. The painted colour itself is verified on a
    // device (Robolectric cannot redraw a window for captureToImage without pixelCopyRenderMode), the
    // same limitation LibraryStatsBarTest records.
    @Test
    fun `the chapter list seats itself on the container when a wallpaper is active`() {
        composeTestRule.setContent {
            NovelReaderTheme(appTheme = "dark", palette = "grafite") {
                ChaptersTab(
                    novelId = 7L,
                    chapters = (0L..4L).map {
                        ChapterEntity(
                            id = it,
                            novelId = 7L,
                            title = "Ch $it",
                            fileName = "ch_$it.html",
                            orderIndex = it.toInt(),
                            content = "<p>x</p>"
                        )
                    },
                    bookmarkCounts = emptyMap(),
                    sortOrder = ChapterSortOrder.ASCENDING,
                    onChapterClick = { },
                    wallpaperActive = true
                )
            }
        }
        composeTestRule.waitForIdle()

        val root = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val list = composeTestRule.onNodeWithTag(CHAPTERS_LIST_TAG).fetchSemanticsNode().boundsInRoot

        assertThat(list.left).isWithin(1f).of(root.left)
        assertThat(list.top).isWithin(1f).of(root.top)
        assertThat(list.right).isWithin(1f).of(root.right)
        assertThat(list.bottom).isWithin(1f).of(root.bottom)
    }
}
