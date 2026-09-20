package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TapZonesTest {

    @Test
    fun `the width splits into three equal zones`() {
        assertThat(tapZoneFor(x = 0, width = 300)).isEqualTo(TapZone.LEFT)
        assertThat(tapZoneFor(x = 99, width = 300)).isEqualTo(TapZone.LEFT)
        assertThat(tapZoneFor(x = 100, width = 300)).isEqualTo(TapZone.CENTER)
        assertThat(tapZoneFor(x = 199, width = 300)).isEqualTo(TapZone.CENTER)
        assertThat(tapZoneFor(x = 200, width = 300)).isEqualTo(TapZone.RIGHT)
        assertThat(tapZoneFor(x = 299, width = 300)).isEqualTo(TapZone.RIGHT)
    }

    @Test
    fun `a width the webview has not measured yet keeps the tap in the centre`() {
        assertThat(tapZoneFor(x = 10, width = 0)).isEqualTo(TapZone.CENTER)
        assertThat(tapZoneFor(x = 10, width = -5)).isEqualTo(TapZone.CENTER)
    }

    @Test
    fun `a coordinate outside the viewport never turns the page on its own`() {
        assertThat(tapZoneFor(x = -1, width = 300)).isEqualTo(TapZone.CENTER)
        assertThat(tapZoneFor(x = 300, width = 300)).isEqualTo(TapZone.CENTER)
    }

    @Test
    fun `with the zones off every tap toggles the controls`() {
        TapZone.entries.forEach { zone ->
            assertThat(tapActionFor(zone, enabled = false)).isEqualTo(TapAction.TOGGLE_CONTROLS)
        }
    }

    @Test
    fun `with the zones on the sides change chapter and the centre toggles`() {
        assertThat(tapActionFor(TapZone.LEFT, enabled = true)).isEqualTo(TapAction.PREV_CHAPTER)
        assertThat(tapActionFor(TapZone.CENTER, enabled = true)).isEqualTo(TapAction.TOGGLE_CONTROLS)
        assertThat(tapActionFor(TapZone.RIGHT, enabled = true)).isEqualTo(TapAction.NEXT_CHAPTER)
    }
}
