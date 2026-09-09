package com.novelreader.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
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
    fun `readPercent clamps the ratio to zero through one hundred`() {
        assertThat(readPercent(0f)).isEqualTo(0)
        assertThat(readPercent(0.735f)).isEqualTo(73)
        assertThat(readPercent(1f)).isEqualTo(100)
        assertThat(readPercent(1.4f)).isEqualTo(100)
        assertThat(readPercent(-0.2f)).isEqualTo(0)
    }

    @Test
    fun `renders the battery percentage and the read percentage`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(
                    battery = BatteryState(percent = 85, charging = false),
                    readRatio = 0.73f
                )
            }
        }

        composeTestRule.onNodeWithText("85%").assertIsDisplayed()
        composeTestRule.onNodeWithText("73%").assertIsDisplayed()
    }

    @Test
    fun `renders a clamped read percentage for an out of range ratio`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(
                    battery = BatteryState(percent = 50, charging = false),
                    readRatio = 1.6f
                )
            }
        }

        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun `describes the battery as charging while plugged in`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(
                    battery = BatteryState(percent = 12, charging = true),
                    readRatio = 0f
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("carregando", substring = true)
            .assertIsDisplayed()
    }
}
