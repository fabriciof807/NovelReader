package com.novelreader.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.mutedForeground
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

    @Test
    fun `keeps the sleep timer out of the bar while it is off`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(battery = BatteryState(percent = 80, charging = false))
            }
        }

        composeTestRule.onNodeWithText("80%").assertExists()
        composeTestRule.onNodeWithContentDescription("Temporizador de sono").assertDoesNotExist()
    }

    @Test
    fun `shows the remaining sleep timer minutes rounded up`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(
                    battery = BatteryState(percent = 80, charging = false),
                    sleepRemainingSeconds = 1_999
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Temporizador de sono").assertExists()
        composeTestRule.onNodeWithText("34 min").assertExists()
    }

    @Test
    fun `showBattery = false hides the battery and keeps the timer`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(
                    battery = BatteryState(percent = 80, charging = false),
                    sleepRemainingSeconds = 600,
                    showBattery = false
                )
            }
        }

        composeTestRule.onNodeWithText("80%").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Bateria", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Temporizador de sono").assertExists()
        composeTestRule.onNodeWithText("10 min").assertExists()
    }

    @Test
    fun `never shows zero minutes while the sleep timer still has seconds left`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderStatusBar(
                    battery = BatteryState(percent = 80, charging = false),
                    sleepRemainingSeconds = 1
                )
            }
        }

        composeTestRule.onNodeWithText("1 min").assertExists()
    }

    private fun renderedTextColor(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(text)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.style.color
    }

    @Test
    fun `a translucent bar keeps the battery text at full onSurface strength`() {
        var onSurface = Color.Unspecified
        var surface = Color.Unspecified
        composeTestRule.setContent {
            NovelReaderTheme {
                onSurface = MaterialTheme.colorScheme.onSurface
                surface = MaterialTheme.colorScheme.surface
                ReaderStatusBar(
                    battery = BatteryState(percent = 76, charging = false),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            }
        }

        assertThat(renderedTextColor("76%")).isEqualTo(onSurface)
        assertThat(readerStatusBarTint(surface.copy(alpha = 0.85f), onSurface)).isEqualTo(onSurface)
    }

    @Test
    fun `an opaque bar dims the battery text to the muted tone`() {
        var onSurface = Color.Unspecified
        var surface = Color.Unspecified
        composeTestRule.setContent {
            NovelReaderTheme {
                onSurface = MaterialTheme.colorScheme.onSurface
                surface = MaterialTheme.colorScheme.surface
                ReaderStatusBar(battery = BatteryState(percent = 76, charging = false))
            }
        }

        assertThat(renderedTextColor("76%")).isEqualTo(mutedForeground(onSurface, surface))
        assertThat(renderedTextColor("76%")).isNotEqualTo(onSurface)
    }
}
