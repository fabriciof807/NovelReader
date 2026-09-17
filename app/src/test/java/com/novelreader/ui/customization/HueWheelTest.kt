package com.novelreader.ui.customization

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HueWheelTest {

    private val radius = 100f

    @Test
    fun `the angle picks the hue starting at the right and growing clockwise`() {
        assertThat(wheelSelectionAt(radius, 0f, radius).hue).isWithin(0.5f).of(0f)
        assertThat(wheelSelectionAt(0f, radius, radius).hue).isWithin(0.5f).of(90f)
        assertThat(wheelSelectionAt(-radius, 0f, radius).hue).isWithin(0.5f).of(180f)
        assertThat(wheelSelectionAt(0f, -radius, radius).hue).isWithin(0.5f).of(270f)
    }

    @Test
    fun `the distance from the middle picks the saturation`() {
        assertThat(wheelSelectionAt(0f, 0f, radius).saturation).isEqualTo(0)
        assertThat(wheelSelectionAt(radius / 2f, 0f, radius).saturation).isEqualTo(50)
        assertThat(wheelSelectionAt(radius, 0f, radius).saturation).isEqualTo(100)
    }

    @Test
    fun `a press outside the wheel stops at full saturation`() {
        val selection = wheelSelectionAt(radius * 3f, 0f, radius)

        assertThat(selection.saturation).isEqualTo(100)
        assertThat(selection.hue).isWithin(0.5f).of(0f)
    }

    @Test
    fun `a press on the middle keeps the hue so the handle does not jump`() {
        val selection = wheelSelectionAt(0.4f, 0f, radius, currentHue = 137f)

        assertThat(selection.saturation).isEqualTo(0)
        assertThat(selection.hue).isWithin(0.01f).of(137f)
    }

    @Test
    fun `a negative hue is normalised into the full circle`() {
        assertThat(wheelSelectionAt(0f, -radius, radius).hue).isWithin(0.5f).of(270f)
        assertThat(normalizeHue(-90f)).isWithin(0.01f).of(270f)
        assertThat(normalizeHue(450f)).isWithin(0.01f).of(90f)
    }

    @Test
    fun `the handle and the selection are inverse of each other`() {
        for (hue in listOf(0f, 45f, 137f, 240f, 359f)) {
            for (saturation in listOf(0, 25, 60, 100)) {
                val point = wheelPointFor(hue, saturation, radius)
                val selection = wheelSelectionAt(point.x, point.y, radius, currentHue = hue)

                assertThat(selection.saturation).isEqualTo(saturation)
                if (saturation > 0) {
                    val delta = kotlin.math.abs(selection.hue - hue)
                    assertThat(kotlin.math.min(delta, 360f - delta)).isLessThan(1.5f)
                }
            }
        }
    }

    @Test
    fun `a degenerate radius does not divide by zero`() {
        val selection = wheelSelectionAt(10f, 10f, 0f, currentHue = 42f)

        assertThat(selection.saturation).isEqualTo(0)
        assertThat(selection.hue).isWithin(0.01f).of(42f)
    }

    @Test
    fun `the handle keeps its distance from the middle proportional to the saturation`() {
        val point = wheelPointFor(0f, 50, radius)

        assertThat(point.x).isWithin(0.01f).of(radius / 2f)
        assertThat(point.y).isWithin(0.01f).of(0f)
        assertThat(wheelPointFor(0f, 0, radius)).isEqualTo(Offset.Zero)
    }
}
