package com.novelreader.ui.customization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.SavedTheme
import com.novelreader.data.local.preferences.SavedThemeCodec
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class SavedThemesSectionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val noite = SavedTheme("Noite", "amoled", "#7c4dff", "papel:dark", null)

    private fun setSection(
        themes: List<SavedTheme> = listOf(noite),
        onSave: (String) -> Unit = {},
        onApply: (SavedTheme) -> Unit = {},
        onDelete: (SavedTheme) -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                Column(Modifier.fillMaxWidth()) {
                    SavedThemesSection(
                        themes = themes,
                        dark = true,
                        onSave = onSave,
                        onApply = onApply,
                        onDelete = onDelete
                    )
                }
            }
        }
    }

    @Test
    fun `shows a chip per saved theme with the slot counter`() {
        setSection(themes = listOf(noite, SavedTheme("Dia", "papel", null, "auto", null)))

        composeTestRule.onNodeWithText("Meus temas").assertExists()
        composeTestRule.onNodeWithText("2/${SavedThemeCodec.MAX_THEMES}").assertExists()
        composeTestRule.onNodeWithContentDescription("Noite").assertExists()
        composeTestRule.onNodeWithContentDescription("Dia").assertExists()
    }

    @Test
    fun `applies the tapped theme`() {
        var applied: SavedTheme? = null
        setSection(onApply = { applied = it })

        composeTestRule.onNodeWithContentDescription("Noite")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(applied).isEqualTo(noite)
    }

    // The name dialog itself is not exercised here: with a Compose TextField on screen, Robolectric
    // never reports idle (the blinking cursor keeps animating), so any waitForIdle hangs. The
    // name -> SavedTheme path is covered by VisualThemeUseCaseTest and SavedThemeCodecTest.
    @Test
    fun `the save action is offered while there is a free slot`() {
        setSection(themes = listOf(noite))

        composeTestRule.onNodeWithContentDescription("Salvar tema atual").assertExists()
        composeTestRule.onNodeWithText("1/${SavedThemeCodec.MAX_THEMES}").assertExists()
    }

    @Test
    fun `deleting a theme asks for confirmation first`() {
        var deleted: SavedTheme? = null
        setSection(onDelete = { deleted = it })

        composeTestRule.onNodeWithContentDescription("Remover Noite")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.onNodeWithText("Remover tema").assertExists()
        assertThat(deleted).isNull()

        composeTestRule.onNodeWithText("Remover").performClick()
        assertThat(deleted).isEqualTo(noite)
    }

    @Test
    fun `dismissing the delete dialog keeps the theme`() {
        var deleted: SavedTheme? = null
        setSection(onDelete = { deleted = it })

        composeTestRule.onNodeWithContentDescription("Remover Noite")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText("Cancelar").performClick()

        assertThat(deleted).isNull()
    }

    @Test
    fun `the save action disappears when the five slots are taken`() {
        val full = (1..SavedThemeCodec.MAX_THEMES).map {
            SavedTheme("Tema $it", "papel", null, "auto", null)
        }
        setSection(themes = full)

        composeTestRule.onNodeWithContentDescription("Salvar tema atual").assertDoesNotExist()
        composeTestRule.onNodeWithText(
            "Limite de ${SavedThemeCodec.MAX_THEMES} temas. Remova um para salvar outro."
        ).assertExists()
    }
}
