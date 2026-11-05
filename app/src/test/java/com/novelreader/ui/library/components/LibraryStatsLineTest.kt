package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.library.LibraryBottomBar
import com.novelreader.ui.library.LibraryStats
import com.novelreader.ui.theme.AppPalette
import com.novelreader.ui.theme.MinContrast
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.contrastRatio
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class LibraryStatsLineTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val stats = LibraryStats(totalNovels = 1, totalChapters = 109, totalBookmarks = 0)

    @Test
    fun `counters show novels, chapters and bookmarks`() {
        composeTestRule.setContent {
            NovelReaderTheme { LibraryStatsLine(stats, wallpaperActive = false) }
        }

        composeTestRule.onNodeWithText("1 Romances", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("109 Capítulos", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("0 Favoritos", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the FAB clears the stats line and the nav bar`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        LibraryBottomBar(
                            selectedTab = 0,
                            hasSelectedNovel = false,
                            stats = stats,
                            wallpaperActive = false,
                            onNavigate = {}
                        )
                    },
                    floatingActionButton = { LibraryFab(onClick = {}) }
                ) { }
            }
        }

        val fabBottom = composeTestRule.onNodeWithText("+")
            .fetchSemanticsNode().boundsInRoot.bottom
        val statsTop = composeTestRule.onNodeWithTag(LIBRARY_STATS_LINE_TAG)
            .fetchSemanticsNode().boundsInRoot.top
        val scaffoldLift = with(composeTestRule.density) { 16.dp.toPx() }
        val extraClearance = with(composeTestRule.density) { LibraryFabClearance.toPx() }

        assertThat(statsTop - fabBottom).isAtLeast(scaffoldLift + extraClearance)
    }

    @Test
    fun `the stats line text clears MinContrast on the container`() {
        AppPalette.entries.forEach { palette ->
            listOf(palette.light, palette.dark).forEach { scheme ->
                listOf(true, false).forEach { wallpaperActive ->
                    val colors = libraryStatsLineColors(scheme, wallpaperActive)

                    assertThat(colors.container.alpha).isEqualTo(1f)
                    assertThat(contrastRatio(colors.text, colors.container)).isAtLeast(MinContrast)
                }
            }
        }
    }

    @Test
    fun `a library without novels shows no stats line`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryBottomBar(
                    selectedTab = 0,
                    hasSelectedNovel = false,
                    stats = LibraryStats(),
                    wallpaperActive = false,
                    onNavigate = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_STATS_LINE_TAG).assertDoesNotExist()
    }
}
