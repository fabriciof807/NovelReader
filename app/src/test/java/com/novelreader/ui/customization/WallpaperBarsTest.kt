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

    // Text drawn straight on a light wallpaper washes out (the unselected filter chips measured 2.19:1
    // and the stats bar labels 3.22:1 against "Amanhecer"), so over a wallpaper the library draws its
    // text-bearing containers opaque, the way the novel cards already do.
    @Test
    fun `library containers turn opaque over a wallpaper`() {
        val translucent = surface.copy(alpha = 0.5f)
        val transparent = Color.Transparent

        assertThat(libraryContainerColor(translucent, surface, wallpaperActive = true))
            .isEqualTo(surface)
        assertThat(libraryContainerColor(transparent, surface, wallpaperActive = true))
            .isEqualTo(surface)
        assertThat(libraryContainerColor(translucent, surface, wallpaperActive = true).alpha)
            .isEqualTo(1f)
    }

    @Test
    fun `library containers keep their look when there is no wallpaper`() {
        val translucent = surface.copy(alpha = 0.5f)
        val transparent = Color.Transparent

        assertThat(libraryContainerColor(translucent, surface, wallpaperActive = false))
            .isEqualTo(translucent)
        assertThat(libraryContainerColor(transparent, surface, wallpaperActive = false))
            .isEqualTo(transparent)
    }
}
