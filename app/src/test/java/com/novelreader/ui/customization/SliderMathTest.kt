package com.novelreader.ui.customization

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SliderMathTest {

    // A 100px slider with a 20px thumb: the thumb's centre travels from 10px to 90px.
    private val total = 100f
    private val thumb = 20f

    @Test
    fun `the ends of the travel are the ends of the range`() {
        assertThat(sliderFractionAt(10f, total, thumb)).isEqualTo(0f)
        assertThat(sliderFractionAt(90f, total, thumb)).isEqualTo(1f)
        assertThat(sliderValueAt(sliderFractionAt(50f, total, thumb), 0f..3f, 0))
            .isWithin(1e-4f).of(1.5f)
    }

    @Test
    fun `a press off either end of the track clamps to the range`() {
        assertThat(sliderFractionAt(-40f, total, thumb)).isEqualTo(0f)
        assertThat(sliderFractionAt(500f, total, thumb)).isEqualTo(1f)
    }

    @Test
    fun `steps snap to the nearest admissible value`() {
        val range = 0f..10f

        assertThat(sliderValueAt(0f, range, 4)).isEqualTo(0f)
        assertThat(sliderValueAt(0.49f, range, 4)).isEqualTo(4f)
        assertThat(sliderValueAt(0.51f, range, 4)).isEqualTo(6f)
        assertThat(sliderValueAt(1f, range, 4)).isEqualTo(10f)
    }

    @Test
    fun `a slider without steps keeps the value the finger asked for`() {
        assertThat(sliderValueAt(0.37f, 1.2f..2.5f, 0)).isWithin(1e-4f).of(1.2f + 0.37f * 1.3f)
    }

    @Test
    fun `a right-to-left layout mirrors the mapping`() {
        assertThat(sliderFractionAt(10f, total, thumb, rtl = true)).isEqualTo(1f)
        assertThat(sliderFractionAt(90f, total, thumb, rtl = true)).isEqualTo(0f)
    }

    @Test
    fun `the accessibility action lands on a step too`() {
        assertThat(snapSliderValue(5.9f, 0f..10f, 4)).isEqualTo(6f)
        assertThat(snapSliderValue(50f, 5f..100f, 0)).isEqualTo(50f)
        assertThat(snapSliderValue(-3f, 5f..100f, 0)).isEqualTo(5f)
    }

    @Test
    fun `one key press moves a step, or one percent of the range without steps`() {
        assertThat(sliderStepSize(0f..3f, 11)).isWithin(1e-4f).of(0.25f)
        assertThat(sliderStepSize(0f..3f, 0)).isWithin(1e-4f).of(0.03f)
    }

    @Test
    fun `a degenerate width or range never divides by zero`() {
        assertThat(sliderFractionAt(50f, 20f, 20f)).isEqualTo(0f)
        assertThat(sliderFractionAt(50f, 0f, 0f)).isEqualTo(0f)
        assertThat(snapSliderValue(4f, 2f..2f, 3)).isEqualTo(2f)
    }
}
