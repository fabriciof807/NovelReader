package com.novelreader.ui.test

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp")
class ComposeUiTestBaseSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `createComposeRule renders text and isDisplayable`() {
        composeTestRule.setContent {
            com.novelreader.ui.theme.NovelReaderTheme {
                Text("hello")
            }
        }
        composeTestRule.onNodeWithText("hello").assertIsDisplayed()
        assertThat(true).isTrue()
    }
}
