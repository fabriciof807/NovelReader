package com.novelreader.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class SettingsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSection(summary: String? = null, startExpanded: Boolean = false) {
        composeTestRule.setContent {
            NovelReaderTheme {
                Column(Modifier.fillMaxWidth()) {
                    var expanded by remember { mutableStateOf(startExpanded) }
                    SettingsSection(
                        title = "Cores e tema",
                        summary = summary,
                        expanded = expanded,
                        onToggle = { expanded = !expanded }
                    ) {
                        Text("conteudo da secao")
                    }
                }
            }
        }
    }

    @Test
    fun `shows the title and the summary while collapsed`() {
        setSection(summary = "Claro · Índigo")

        composeTestRule.onNodeWithText("Cores e tema").assertExists()
        composeTestRule.onNodeWithText("Claro · Índigo").assertExists()
        composeTestRule.onNodeWithText("conteudo da secao").assertDoesNotExist()
    }

    @Test
    fun `hides the content until the header is tapped`() {
        setSection(summary = "Claro · Índigo")

        composeTestRule.onNodeWithText("Cores e tema")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.onNodeWithText("conteudo da secao").assertExists()
    }

    @Test
    fun `tapping the header again collapses the content`() {
        setSection(summary = "Claro · Índigo")

        composeTestRule.onNodeWithText("Cores e tema")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText("conteudo da secao").assertExists()
        composeTestRule.onNodeWithText("Cores e tema")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.onNodeWithText("conteudo da secao").assertDoesNotExist()
    }

    @Test
    fun `works without a summary`() {
        setSection(summary = null, startExpanded = true)

        composeTestRule.onNodeWithText("Cores e tema").assertExists()
        composeTestRule.onNodeWithText("conteudo da secao").assertExists()
    }
}
