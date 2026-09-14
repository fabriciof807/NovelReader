package com.novelreader.ui.customization

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class PalettePickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setPicker(
        selectedId: String,
        allowDynamic: Boolean = true,
        dark: Boolean = false,
        onSelect: (String) -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                PalettePicker(
                    selectedId = selectedId,
                    choices = appPaletteChoices(dark = dark, allowDynamic = allowDynamic),
                    onSelect = onSelect
                )
            }
        }
    }

    @Test
    fun `renders a chip for every palette with the selected one highlighted`() {
        setPicker(selectedId = "papel")

        composeTestRule.onNodeWithContentDescription("Papel").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Índigo").assertIsNotSelected()
        composeTestRule.onNodeWithText("Grafite").assertExists()
        composeTestRule.onNodeWithText("Ameixa").assertExists()
    }

    @Test
    fun `offers the dynamic palette only when the platform supports it`() {
        setPicker(selectedId = "indigo", allowDynamic = false)
        composeTestRule.onNodeWithText("Dinâmica").assertDoesNotExist()
    }

    @Test
    fun `shows the dynamic palette as the selected chip when it is active`() {
        setPicker(selectedId = PreferenceAllowlists.PALETTE_DYNAMIC)

        composeTestRule.onNodeWithContentDescription("Dinâmica").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Índigo").assertIsNotSelected()
    }

    @Test
    fun `reports the tapped palette`() {
        var selected: String? = null
        setPicker(selectedId = "indigo", onSelect = { selected = it })

        composeTestRule.onNodeWithContentDescription("Papel").performClick()

        assertThat(selected).isEqualTo("papel")
    }

    @Test
    fun `exposes every chip as a radio button`() {
        setPicker(selectedId = "indigo")

        composeTestRule.onNodeWithContentDescription("Papel").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        )
    }

    @Test
    fun `every palette plus dynamic is offered in the app picker`() {
        var ids: List<String> = emptyList()
        composeTestRule.setContent {
            ids = appPaletteChoices(dark = false, allowDynamic = true).map { it.id }
        }

        assertThat(ids).containsExactlyElementsIn(
            PreferenceAllowlists.PALETTES + PreferenceAllowlists.PALETTE_DYNAMIC
        )
    }

    @Test
    fun `the reader picker offers auto plus the palettes without dynamic`() {
        var ids: List<String> = emptyList()
        composeTestRule.setContent {
            ids = readerPaletteChoices(
                dark = false,
                autoBackground = androidx.compose.ui.graphics.Color.White,
                autoPrimary = androidx.compose.ui.graphics.Color.Black
            ).map { it.id }
        }

        assertThat(ids).containsExactlyElementsIn(
            PreferenceAllowlists.PALETTES + READER_AUTO
        )
    }
}
