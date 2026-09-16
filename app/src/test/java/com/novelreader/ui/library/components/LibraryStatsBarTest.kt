package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
}
