package com.novelreader.ui.customization

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.hsvOf
import com.novelreader.ui.theme.parseAccentHex
import com.novelreader.ui.theme.contrastRatio
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class AccentColorPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun `tapping the wheel emits an allowlisted accent that stays readable`() {
        var selected: String? = null
        setPicker(background = Color(0xFF141414), onSelect = { selected = it })

        composeTestRule.onNodeWithTag(ACCENT_WHEEL_TAG).performTouchInput {
            click(center + Offset(width * 0.4f, 0f))
        }

        val emitted = requireNotNull(selected)
        assertThat(emitted).matches("#[0-9a-f]{6}")
        assertThat(PreferenceAllowlists.sanitizeAccentColor(emitted)).isEqualTo(emitted)
        assertThat(contrastRatio(requireNotNull(parseAccentHex(emitted)), Color(0xFF141414)))
            .isAtLeast(4.5f)
    }

    @Test
    fun `dragging the wheel keeps the emitted accent readable`() {
        var selected: String? = null
        setPicker(background = Color(0xFFF5F0E8), onSelect = { selected = it })

        composeTestRule.onNodeWithTag(ACCENT_WHEEL_TAG).performTouchInput {
            swipe(center, center + Offset(0f, height * 0.45f), 200)
        }

        val emitted = requireNotNull(selected)
        assertThat(contrastRatio(requireNotNull(parseAccentHex(emitted)), Color(0xFFF5F0E8)))
            .isAtLeast(4.5f)
    }

    // The gesture handler is built once, so reading the parameters it was built with made a drag emit
    // the selection it started from.
    @Test
    fun `dragging to the left of the wheel selects the hue under the finger`() {
        var selected: String? = null
        setPicker(selected = "#2e7d32", background = Color(0xFF141414), onSelect = { selected = it })

        composeTestRule.onNodeWithTag(ACCENT_WHEEL_TAG).performTouchInput {
            swipe(center, Offset(width * 0.05f, center.y), 200)
        }

        val hue = hsvOf(requireNotNull(parseAccentHex(requireNotNull(selected)))).hue
        assertThat(hue).isAtLeast(150f)
        assertThat(hue).isAtMost(210f)
    }

    @Test
    fun `the wheel announces the hue and the saturation`() {
        setPicker(selected = "#2e7d32")

        composeTestRule.onNodeWithTag(ACCENT_WHEEL_TAG)
            .assertContentDescriptionContains("Matiz", substring = true)
        composeTestRule.onNodeWithTag(ACCENT_WHEEL_TAG)
            .assertContentDescriptionContains("saturação", substring = true)
    }

    // A drawn control is unreachable for a screen reader, so the wheel exposes four actions.
    @Test
    fun `the accessibility actions move the hue and the saturation`() {
        var selected: String? = null
        setPicker(background = Color(0xFF141414), onSelect = { selected = it })

        performWheelAction("Aumentar matiz")
        val afterHue = requireNotNull(selected)
        assertThat(contrastRatio(requireNotNull(parseAccentHex(afterHue)), Color(0xFF141414)))
            .isAtLeast(4.5f)

        performWheelAction("Aumentar saturação")
        val afterMore = requireNotNull(selected)
        assertThat(afterMore).isNotEqualTo(afterHue)

        performWheelAction("Diminuir saturação")
        assertThat(requireNotNull(selected)).isNotEqualTo(afterMore)
    }

    private fun performWheelAction(label: String) {
        composeTestRule.onNodeWithTag(ACCENT_WHEEL_TAG)
            .performCustomAccessibilityActionWithLabel(label)
    }
}
