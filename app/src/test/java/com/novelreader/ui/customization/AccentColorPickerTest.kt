package com.novelreader.ui.customization

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.parseAccentHex
import com.novelreader.ui.theme.contrastRatio
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class AccentColorPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    private fun setPicker(
        selected: String? = null,
        background: Color = Color.White,
        onSelect: (String?) -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                AccentColorPicker(
                    selected = selected,
                    background = background,
                    fallback = Color(0xFF1A237E),
                    onSelect = onSelect
                )
            }
        }
    }

    @Test
    fun `marks the palette default as selected when there is no accent`() {
        setPicker(selected = null)

        composeTestRule.onNodeWithContentDescription("Padrão da paleta").assertIsSelected()
    }

    @Test
    fun `marks the stored accent preset as selected`() {
        setPicker(selected = "#2e7d32")

        composeTestRule.onNodeWithContentDescription("#2e7d32").assertIsSelected()
    }

    @Test
    fun `reports the palette default when it is tapped`() {
        var selected: String? = "#2e7d32"
        setPicker(selected = "#2e7d32", onSelect = { selected = it })

        composeTestRule.onNodeWithContentDescription("Padrão da paleta").performClick()

        assertThat(selected).isNull()
    }

    @Test
    fun `the hue slider emits an allowlisted accent that stays readable`() {
        var selected: String? = null
        setPicker(background = Color(0xFF141414), onSelect = { selected = it })

        composeTestRule.onAllNodes(slider)[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(210f) }

        val emitted = requireNotNull(selected)
        assertThat(emitted).matches("#[0-9a-f]{6}")
        assertThat(PreferenceAllowlists.sanitizeAccentColor(emitted)).isEqualTo(emitted)
        assertThat(contrastRatio(requireNotNull(parseAccentHex(emitted)), Color(0xFF141414)))
            .isAtLeast(4.5f)
    }

    @Test
    fun `the saturation slider keeps the emitted accent readable`() {
        var selected: String? = null
        setPicker(background = Color(0xFFF5F0E8), onSelect = { selected = it })

        composeTestRule.onAllNodes(slider)[1]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }

        val emitted = requireNotNull(selected)
        assertThat(contrastRatio(requireNotNull(parseAccentHex(emitted)), Color(0xFFF5F0E8)))
            .isAtLeast(4.5f)
    }
}
