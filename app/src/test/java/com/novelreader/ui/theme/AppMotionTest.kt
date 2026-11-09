package com.novelreader.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class AppMotionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `reduced is true when animator scale is 0`() {
        assertThat(AppMotion.reduced(0f)).isTrue()
        assertThat(AppMotion.reduced(1f)).isFalse()
        assertThat(AppMotion.reduced(0.5f)).isFalse()
    }

    @Test
    fun `press spring is critically damped`() {
        assertThat(AppMotion.press<Float>().dampingRatio).isEqualTo(Spring.DampingRatioNoBouncy)
    }

    @Test
    fun `momentum spring bounces`() {
        assertThat(AppMotion.settle<Float>(hasMomentum = true).dampingRatio).isLessThan(1f)
    }

    @Test
    fun `without momentum the settle spring is critically damped`() {
        assertThat(AppMotion.settle<Float>(hasMomentum = false).dampingRatio)
            .isEqualTo(Spring.DampingRatioNoBouncy)
    }

    @Test
    fun `with reduced motion the fade duration is zero`() {
        assertThat(AppMotion.fadeMillis(reduced = true)).isEqualTo(0)
    }

    @Test
    fun `with reduced motion the slide duration is zero`() {
        assertThat(AppMotion.slideMillis(reduced = true)).isEqualTo(0)
    }

    @Test
    fun `without reduced motion the fade duration is the fade constant`() {
        assertThat(AppMotion.fadeMillis(reduced = false)).isEqualTo(AppMotion.FadeMillis)
    }

    @Test
    fun `without reduced motion the slide duration is 220`() {
        assertThat(AppMotion.slideMillis(reduced = false)).isEqualTo(220)
    }

    @Test
    fun `rememberReducedMotion is true when the animator scale is 0`() {
        setAnimatorScale(0f)

        var reduced = false
        composeTestRule.setContent { reduced = rememberReducedMotion() }
        composeTestRule.waitForIdle()

        assertThat(reduced).isTrue()
    }

    @Test
    fun `rememberReducedMotion is false when the animator scale is 1`() {
        setAnimatorScale(1f)

        var reduced = true
        composeTestRule.setContent { reduced = rememberReducedMotion() }
        composeTestRule.waitForIdle()

        assertThat(reduced).isFalse()
    }

    @Test
    fun `pressing scales the content down and releasing restores it`() {
        lateinit var scale: State<Float>
        composeTestRule.setContent {
            val source = remember { MutableInteractionSource() }
            scale = rememberPressScale(source, reduced = false)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .pressable(onClick = {}, interactionSource = source)
                    .testTag(PRESSABLE_TAG)
            )
        }

        composeTestRule.onNodeWithTag(PRESSABLE_TAG).performTouchInput { down(center) }
        composeTestRule.waitForIdle()
        assertThat(scale.value).isWithin(0.01f).of(AppMotion.PressedScale)

        composeTestRule.onNodeWithTag(PRESSABLE_TAG).performTouchInput { up() }
        composeTestRule.waitForIdle()
        assertThat(scale.value).isWithin(0.01f).of(1f)
    }

    @Test
    fun `with reduced motion the press scale is applied immediately`() {
        lateinit var scale: State<Float>
        composeTestRule.setContent {
            val source = remember { MutableInteractionSource() }
            scale = rememberPressScale(source, reduced = true)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .pressable(onClick = {}, interactionSource = source)
                    .testTag(PRESSABLE_TAG)
            )
        }

        composeTestRule.onNodeWithTag(PRESSABLE_TAG).performTouchInput { down(center) }
        composeTestRule.waitForIdle()

        assertThat(scale.value).isEqualTo(AppMotion.PressedScale)
    }

    private fun setAnimatorScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            scale
        )
    }

    private companion object {
        const val PRESSABLE_TAG = "pressable"
    }
}
