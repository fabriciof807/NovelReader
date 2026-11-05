package com.novelreader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class ReaderChromeOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setOverlay(chrome: ReaderChromeState, sleepRemainingSeconds: Int? = null) {
        composeTestRule.setContent {
            NovelReaderTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ReaderChromeOverlay(
                        chrome = chrome,
                        battery = BatteryState(percent = 76, charging = false),
                        sleepRemainingSeconds = sleepRemainingSeconds,
                        barColor = MaterialTheme.colorScheme.surface,
                        topBar = {
                            ReaderTopBar(
                                title = "Chapter 11",
                                isSearchActive = false,
                                searchQuery = "",
                                onSearchQueryChange = {},
                                onBack = {},
                                onCloseSearch = {},
                                onActivateSearch = {}
                            )
                        },
                        optionsBar = {
                            ReaderOptionsBar(
                                barColor = MaterialTheme.colorScheme.surface,
                                prevEnabled = true,
                                nextEnabled = true,
                                bookmarkCount = 0,
                                onPrev = {},
                                onBookmark = {},
                                onSettings = {},
                                onChapterList = {},
                                onNext = {}
                            )
                        }
                    )
                }
            }
        }
    }

    @Test
    fun `with everything hidden no bar is shown`() {
        setOverlay(ReaderChromeState(topBar = false, optionsBar = false, status = false, sleepPill = false))

        composeTestRule.onNodeWithText("Chapter 11").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Capítulos").assertDoesNotExist()
        composeTestRule.onNodeWithText("76%").assertDoesNotExist()
    }

    @Test
    fun `showing the controls shows the top bar, the options bar and the battery`() {
        setOverlay(ReaderChromeState(topBar = true, optionsBar = true, status = true, sleepPill = false))

        composeTestRule.onNodeWithText("Chapter 11").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Capítulos").assertIsDisplayed()
        composeTestRule.onNodeWithText("76%").assertIsDisplayed()
    }

    @Test
    fun `battery always on shows the battery without the bars`() {
        setOverlay(ReaderChromeState(topBar = false, optionsBar = false, status = true, sleepPill = false))

        composeTestRule.onNodeWithText("76%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter 11").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Capítulos").assertDoesNotExist()
    }

    @Test
    fun `a running sleep timer alone shows the timer and no battery`() {
        setOverlay(
            chrome = ReaderChromeState(topBar = false, optionsBar = false, status = false, sleepPill = true),
            sleepRemainingSeconds = 780
        )

        composeTestRule.onNodeWithText("13 min").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Temporizador de sono").assertIsDisplayed()
        composeTestRule.onNodeWithText("76%").assertDoesNotExist()
    }

    @Test
    fun `the sleep-only pill does not span the full width`() {
        setOverlay(
            chrome = ReaderChromeState(topBar = false, optionsBar = false, status = false, sleepPill = true),
            sleepRemainingSeconds = 780
        )

        val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val pill = composeTestRule.onNodeWithTag(READER_SLEEP_PILL_TAG).getUnclippedBoundsInRoot()

        assertThat(pill.width < root.width / 2).isTrue()
        assertThat(root.right - pill.right <= 16.dp).isTrue()
        assertThat(pill.left > root.left).isTrue()
    }
}
