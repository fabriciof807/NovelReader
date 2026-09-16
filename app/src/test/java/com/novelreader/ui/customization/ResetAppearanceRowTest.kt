package com.novelreader.ui.customization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
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
class ResetAppearanceRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setRow(onReset: () -> Unit = {}) {
        composeTestRule.setContent {
            NovelReaderTheme {
                Column(Modifier.fillMaxWidth()) {
                    ResetAppearanceRow(onReset = onReset)
                }
            }
        }
    }

    @Test
    fun `asks for confirmation before resetting`() {
        var reset = false
        setRow(onReset = { reset = true })

        composeTestRule.onNodeWithText("Restaurar aparência padrão")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText("Restaurar aparência padrão?").assertExists()
        assertThat(reset).isFalse()

        composeTestRule.onNodeWithText("Restaurar")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(reset).isTrue()
    }

    @Test
    fun `the dialog explains that saved themes are kept`() {
        setRow()

        composeTestRule.onNodeWithText("Restaurar aparência padrão")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.onNodeWithText(
            "Paleta, cor de acento, papéis de parede, desfoque e véu voltam ao padrão. " +
                "Seus temas salvos são mantidos."
        ).assertExists()
    }

    @Test
    fun `dismissing the dialog resets nothing`() {
        var reset = false
        setRow(onReset = { reset = true })

        composeTestRule.onNodeWithText("Restaurar aparência padrão")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText("Cancelar")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(reset).isFalse()
    }
}
