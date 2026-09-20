package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.PreferenceAllowlists
import org.junit.Test

class ReaderBrightnessTest {

    @Test
    fun `following the system asks for no window override`() {
        assertThat(windowBrightnessFor(PreferenceAllowlists.BRIGHTNESS_SYSTEM)).isNull()
    }

    @Test
    fun `a fixed value becomes a window fraction of the display maximum`() {
        assertThat(windowBrightnessFor(PreferenceAllowlists.BRIGHTNESS_MIN)).isEqualTo(0.05f)
        assertThat(windowBrightnessFor(35)).isEqualTo(0.35f)
        assertThat(windowBrightnessFor(PreferenceAllowlists.MAX_BRIGHTNESS)).isEqualTo(1f)
    }

    @Test
    fun `a value outside the range clamps instead of reaching the window raw`() {
        assertThat(windowBrightnessFor(0)).isEqualTo(0.05f)
        assertThat(windowBrightnessFor(1_000)).isEqualTo(1f)
    }

    @Test
    fun `the device level maps onto the usable range`() {
        assertThat(systemBrightnessPercent(255)).isEqualTo(PreferenceAllowlists.MAX_BRIGHTNESS)
        assertThat(systemBrightnessPercent(102)).isEqualTo(40)
        assertThat(systemBrightnessPercent(0)).isEqualTo(PreferenceAllowlists.BRIGHTNESS_MIN)
        assertThat(systemBrightnessPercent(9_999)).isEqualTo(PreferenceAllowlists.MAX_BRIGHTNESS)
    }
}
