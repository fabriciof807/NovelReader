package com.novelreader.ui.reader

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class ReaderStatusBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders the battery percentage`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(battery = BatteryState(percent = 85, charging = false))
            }
        }

        composeTestRule.onNodeWithText("85%").assertIsDisplayed()
    }

    @Test
    fun `renders only the battery percentage`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(battery = BatteryState(percent = 85, charging = false))
            }
        }

        composeTestRule.onAllNodesWithText("%", substring = true).assertCountEquals(1)
    }

    @Test
    fun `describes the battery as charging while plugged in`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(battery = BatteryState(percent = 12, charging = true))
            }
        }

        composeTestRule.onNodeWithContentDescription("carregando", substring = true)
            .assertIsDisplayed()
    }
}
