package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.library.LibraryStats
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class LibraryStatsBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val stats = LibraryStats(totalNovels = 1, totalChapters = 109, totalBookmarks = 0)

    @Test
    fun `shows the three counters with their labels`() {
        composeTestRule.setContent {
            NovelReaderTheme { LibraryStatsBar(stats) }
        }

        composeTestRule.onNodeWithText("1").assertExists()
        composeTestRule.onNodeWithText("109").assertExists()
        composeTestRule.onNodeWithText("Romances").assertExists()
        composeTestRule.onNodeWithText("Capítulos").assertExists()
        composeTestRule.onNodeWithText("Favoritos").assertExists()
    }

    // The FAB is a Scaffold slot: the Scaffold lifts it above the bottomBar on its own, but its 16dp
    // default reads as flush against the counters, so LibraryFab adds breathing room on top. If this
    // ever regresses, the button sits flush against the counters (or covers them) again.
    @Test
    fun `the scaffold floats the action button clear of the stats bar`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { LibraryStatsBar(stats, modifier = Modifier.testTag("statsBar")) },
                    floatingActionButton = { LibraryFab(onClick = {}) }
                ) { }
            }
        }

        val fabBottom = composeTestRule.onNodeWithText("+")
            .fetchSemanticsNode().boundsInRoot.bottom
        val statsTop = composeTestRule.onNodeWithTag("statsBar")
            .fetchSemanticsNode().boundsInRoot.top
        val scaffoldLift = with(composeTestRule.density) { 16.dp.toPx() }
        val extraClearance = with(composeTestRule.density) { 8.dp.toPx() }

        assertThat(statsTop - fabBottom).isAtLeast(scaffoldLift + extraClearance)
    }

    // The Android navigation bar area (gestures or the three buttons) sits below the counters. The
    // counters bar now paints that strip itself and only its content is inset — putting the inset on
    // the Surface instead shrinks what its background covers, which is how the screen ended in the
    // wallpaper veil while the counters above it were opaque. The painted colour is verified on a
    // device (Robolectric cannot redraw a window for captureToImage without pixelCopyRenderMode);
    // what a unit test can hold is that the strip belongs to the bar and the labels stay above it.
    @Test
    fun `the counters bar owns the navigation bar strip and keeps its labels above it`() {
        composeTestRule.setContent {
            NovelReaderTheme(appTheme = "dark", palette = "grafite") {
                Box(modifier = Modifier.fillMaxSize()) {
                    LibraryStatsBar(
                        stats = stats,
                        wallpaperActive = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .testTag("statsBar"),
                        navigationBarInsets = WindowInsets(0.dp, 0.dp, 0.dp, 48.dp)
                    )
                }
            }
        }

        val root = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val bar = composeTestRule.onNodeWithTag("statsBar").fetchSemanticsNode().boundsInRoot
        val label = composeTestRule.onNodeWithText("Romances").fetchSemanticsNode().boundsInRoot
        val strip = with(composeTestRule.density) { 48.dp.toPx() }

        assertThat(bar.bottom).isWithin(1f).of(root.bottom)
        assertThat(root.bottom - label.bottom).isAtLeast(strip)
    }
}
