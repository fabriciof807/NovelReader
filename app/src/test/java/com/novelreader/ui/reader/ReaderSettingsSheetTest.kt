package com.novelreader.ui.reader

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.novelreader.data.local.preferences.ReaderConfig
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class ReaderSettingsSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSheet(
        config: ReaderConfig = ReaderConfig(theme = "light"),
        themeSelection: String = "light",
        onThemeChange: (String) -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                SettingsSheet(
                    config = config,
                    themeSelection = themeSelection,
                    onThemeChange = onThemeChange,
                    onFontSizeChange = {},
                    onLineHeightChange = {},
                    onAutoScrollSpeedChange = {},
                    onKeepScreenOnChange = {},
                    onSwipeDirectionChange = {},
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun `shows the auto option with the other reader themes`() {
        setSheet(themeSelection = "auto")

        composeTestRule.onNodeWithContentDescription("Auto").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Claro").assertIsNotSelected()
        composeTestRule.onNodeWithText("Cinza").assertExists()
    }

    @Test
    fun `selects the auto chip while the page theme is resolved to light`() {
        setSheet(config = ReaderConfig(theme = "light"), themeSelection = "auto")

        composeTestRule.onNodeWithContentDescription("Auto").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Claro").assertIsNotSelected()
    }

    @Test
    fun `selects the auto chip while the page theme is resolved to dark`() {
        setSheet(config = ReaderConfig(theme = "dark"), themeSelection = "auto")

        composeTestRule.onNodeWithContentDescription("Auto").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Escuro").assertIsNotSelected()
    }

    @Test
    fun `selects the explicit chip when the stored theme is not auto`() {
        setSheet(config = ReaderConfig(theme = "sepia"), themeSelection = "sepia")

        composeTestRule.onNodeWithContentDescription("Sépia").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Auto").assertIsNotSelected()
    }

    @Test
    fun `exposes the theme chips as a radio group`() {
        setSheet(themeSelection = "auto")

        composeTestRule.onNodeWithContentDescription("Auto").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        )
    }

    @Test
    fun `reports the auto selection when the auto chip is tapped`() {
        var selected: String? = null
        setSheet(config = ReaderConfig(theme = "sepia"), themeSelection = "sepia") { selected = it }

        composeTestRule.onNodeWithContentDescription("Auto").performClick()

        assert(selected == "auto") { "expected auto but got $selected" }
    }
}
