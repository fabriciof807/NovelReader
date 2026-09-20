package com.novelreader.ui.customization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class ValueSliderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val label = "Valor"

    private fun setSlider(
        initial: Float = 0.5f,
        steps: Int = 0,
        enabled: Boolean = true,
        valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        onFinished: () -> Unit = {}
    ): MutableState<Float> {
        val value = mutableStateOf(initial)
        composeTestRule.setContent {
            NovelReaderTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(200.dp))
                    ValueSlider(
                        value = value.value,
                        onValueChange = { value.value = it },
                        onValueChangeFinished = onFinished,
                        valueRange = valueRange,
                        steps = steps,
                        enabled = enabled,
                        contentDescription = label,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2000.dp))
                }
            }
        }
        return value
    }

    private fun top() = composeTestRule.onNodeWithContentDescription(label).getBoundsInRoot().top

    @Test
    fun `a drag that is mostly upright scrolls the column and leaves the value alone`() {
        val value = setSlider()

        val topBefore = top()
        composeTestRule.onNodeWithContentDescription(label).performTouchInput {
            down(center)
            // Os dois eixos cruzam o slop no mesmo evento, e o vertical e' maior.
            moveBy(Offset(30f, -80f))
            moveBy(Offset(6f, -120f))
            up()
        }
        composeTestRule.waitForIdle()

        assertThat(value.value).isEqualTo(0.5f)
        assertThat(top()).isLessThan(topBefore)
    }

    @Test
    fun `a sideways drag moves the value and does not scroll`() {
        val value = setSlider(initial = 0.5f)

        val topBefore = top()
        composeTestRule.onNodeWithContentDescription(label).performTouchInput {
            down(center)
            moveBy(Offset(-90f, 3f))
            moveBy(Offset(-90f, 3f))
            up()
        }
        composeTestRule.waitForIdle()

        assertThat(value.value).isLessThan(0.5f)
        assertThat(top()).isEqualTo(topBefore)
    }

    @Test
    fun `a press that never crossed the slop is a tap and jumps to that point`() {
        val value = setSlider(initial = 0.25f)

        composeTestRule.onNodeWithContentDescription(label).performTouchInput {
            down(Offset(width * 0.75f, center.y))
            up()
        }
        composeTestRule.waitForIdle()

        assertThat(value.value).isGreaterThan(0.6f)
    }

    @Test
    fun `a dragged value lands on a step`() {
        val value = setSlider(initial = 0.5f, steps = 3)

        composeTestRule.onNodeWithContentDescription(label).performTouchInput {
            down(center)
            moveBy(Offset(-70f, 0f))
            up()
        }
        composeTestRule.waitForIdle()

        assertThat(value.value).isAnyOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        assertThat(value.value).isNotEqualTo(0.5f)
    }

    @Test
    fun `a disabled slider ignores both a tap and a drag`() {
        val value = setSlider(initial = 0.25f, enabled = false)

        composeTestRule.onNodeWithContentDescription(label).performTouchInput {
            down(Offset(width * 0.75f, center.y))
            moveBy(Offset(-60f, 0f))
            up()
        }
        composeTestRule.waitForIdle()

        assertThat(value.value).isEqualTo(0.25f)
    }

    @Test
    fun `releasing reports the end of the change once`() {
        var finished = 0
        setSlider(initial = 0.25f, onFinished = { finished++ })

        composeTestRule.onNodeWithContentDescription(label).performTouchInput {
            down(center)
            moveBy(Offset(-70f, 0f))
            moveBy(Offset(-70f, 0f))
            up()
        }
        composeTestRule.waitForIdle()

        assertThat(finished).isEqualTo(1)
    }
}
