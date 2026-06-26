package com.novelreader.ui.library.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class LibraryEmptyStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `LibraryEmptyState displays illustration, text, and two CTAs`() {
        var localClicks = 0
        var webClicks = 0
        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryEmptyState(
                    onImportLocal = { localClicks++ },
                    onImportWeb = { webClicks++ }
                )
            }
        }
        composeTestRule.onNodeWithText("Adicione sua primeira novel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Importe um arquivo local ou baixe da web").assertIsDisplayed()
        composeTestRule.onNodeWithText("Arquivo local").assertIsDisplayed()
        composeTestRule.onNodeWithText("Da web").assertIsDisplayed()

        composeTestRule.onNodeWithText("Arquivo local").performClick()
        composeTestRule.onNodeWithText("Da web").performClick()
        assertThat(localClicks).isEqualTo(1)
        assertThat(webClicks).isEqualTo(1)
    }
}
