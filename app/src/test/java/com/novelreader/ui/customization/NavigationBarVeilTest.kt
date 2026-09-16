package com.novelreader.ui.customization

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class NavigationBarVeilTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders the bottom strip that receives the bar colour`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                Box(Modifier.fillMaxSize()) {
                    NavigationBarVeil(color = Color.Red)
                }
            }
        }

        composeTestRule.onNodeWithTag(NAVIGATION_BAR_VEIL_TAG).assertExists()
    }
}
