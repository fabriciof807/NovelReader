package com.novelreader.ui.customization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class WallpaperBehindBarsRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit = {}) {
        composeTestRule.setContent {
            NovelReaderTheme {
                Column(Modifier.fillMaxWidth()) {
                    WallpaperBehindBarsRow(checked = checked, onCheckedChange = onCheckedChange)
                }
            }
        }
    }

    @Test
    fun `shows the title and the explanation`() {
        setRow(checked = true)

        composeTestRule.onNodeWithText("Fundo também nas barras").assertExists()
        composeTestRule.onNodeWithText(
            "Deixa a barra de cima e as abas translúcidas, com o fundo aparecendo"
        ).assertExists()
    }

    @Test
    fun `reflects the stored value`() {
        setRow(checked = true)
        composeTestRule.onNodeWithText("Fundo também nas barras").assertIsOn()
    }

    @Test
    fun `reflects an opted out value`() {
        setRow(checked = false)
        composeTestRule.onNodeWithText("Fundo também nas barras").assertIsOff()
    }

    @Test
    fun `reports the toggle action`() {
        var reported: Boolean? = null
        setRow(checked = true, onCheckedChange = { reported = it })

        composeTestRule.onNodeWithText("Fundo também nas barras")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(reported).isFalse()
    }
}
