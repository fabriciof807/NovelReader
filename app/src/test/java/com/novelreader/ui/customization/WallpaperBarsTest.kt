package com.novelreader.ui.customization

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WallpaperBarsTest {

    private val surface = Color(0xFF16213E)

    @Test
    fun `bars stay opaque when there is no wallpaper`() {
        assertThat(barColorFor(surface, wallpaperActive = false, behindBars = true))
            .isEqualTo(surface)
        assertThat(barColorFor(surface, wallpaperActive = false, behindBars = false))
            .isEqualTo(surface)
    }

    @Test
    fun `bars stay opaque when the user opted out`() {
        assertThat(barColorFor(surface, wallpaperActive = true, behindBars = false))
            .isEqualTo(surface)
    }

    @Test
    fun `bars turn translucent when a wallpaper is shown behind them`() {
        val bar = barColorFor(surface, wallpaperActive = true, behindBars = true)

        assertThat(bar.alpha).isEqualTo(BAR_VEIL_ALPHA)
        assertThat(bar.red).isEqualTo(surface.red)
        assertThat(bar.green).isEqualTo(surface.green)
        assertThat(bar.blue).isEqualTo(surface.blue)
    }

    @Test
    fun `the veil keeps the bar readable rather than fully transparent`() {
        assertThat(BAR_VEIL_ALPHA).isAtLeast(0.7f)
        assertThat(BAR_VEIL_ALPHA).isLessThan(1f)
    }
}
