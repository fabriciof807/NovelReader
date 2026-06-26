package com.novelreader.ui.test

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule

abstract class ComposeUiTestBase {

    @get:Rule
    val composeTestRule = createComposeRule()

    protected fun setNovelReaderContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            NovelReaderTheme {
                content()
            }
        }
    }

    protected fun assertTextDisplayed(text: String) {
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    protected fun assertTextNotDisplayed(text: String) {
        composeTestRule.onNodeWithText(text).assertDoesNotExist()
    }

    protected fun performClick(text: String) {
        composeTestRule.onNodeWithText(text).performClick()
    }

    protected fun assertFirstVisibleItem(index: Int, state: LazyListState) {
        composeTestRule.runOnUiThread {
            assertThat(state.firstVisibleItemIndex).isEqualTo(index)
        }
    }
}
