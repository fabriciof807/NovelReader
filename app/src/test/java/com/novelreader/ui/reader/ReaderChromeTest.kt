package com.novelreader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderChromeTest {

    @Test
    fun `the reader opens with every bar hidden`() {
        val chrome = readerChrome(
            optionsVisible = READER_OPTIONS_INITIALLY_VISIBLE,
            searchActive = false,
            batteryAlwaysOn = false,
            sleepRunning = false
        )

        assertThat(chrome).isEqualTo(
            ReaderChromeState(topBar = false, optionsBar = false, status = false, sleepPill = false)
        )
    }

    @Test
    fun `default state hides everything`() {
        val chrome = readerChrome(
            optionsVisible = false,
            searchActive = false,
            batteryAlwaysOn = false,
            sleepRunning = false
        )

        assertThat(chrome).isEqualTo(
            ReaderChromeState(topBar = false, optionsBar = false, status = false, sleepPill = false)
        )
    }

    @Test
    fun `tap shows top bar, options bar and status`() {
        val chrome = readerChrome(
            optionsVisible = true,
            searchActive = false,
            batteryAlwaysOn = false,
            sleepRunning = false
        )

        assertThat(chrome.topBar).isTrue()
        assertThat(chrome.optionsBar).isTrue()
        assertThat(chrome.status).isTrue()
    }

    @Test
    fun `battery always on keeps status without tap`() {
        val chrome = readerChrome(
            optionsVisible = false,
            searchActive = false,
            batteryAlwaysOn = true,
            sleepRunning = false
        )

        assertThat(chrome.status).isTrue()
        assertThat(chrome.topBar).isFalse()
        assertThat(chrome.optionsBar).isFalse()
    }

    @Test
    fun `search active forces the top bar`() {
        val chrome = readerChrome(
            optionsVisible = false,
            searchActive = true,
            batteryAlwaysOn = false,
            sleepRunning = false
        )

        assertThat(chrome.topBar).isTrue()
    }

    @Test
    fun `search active alone does not show the options bar or the status`() {
        val chrome = readerChrome(
            optionsVisible = false,
            searchActive = true,
            batteryAlwaysOn = false,
            sleepRunning = false
        )

        assertThat(chrome.optionsBar).isFalse()
        assertThat(chrome.status).isFalse()
    }

    @Test
    fun `running sleep timer keeps the sleep pill visible`() {
        val chrome = readerChrome(
            optionsVisible = false,
            searchActive = false,
            batteryAlwaysOn = false,
            sleepRunning = true
        )

        assertThat(chrome.sleepPill).isTrue()
        assertThat(chrome.status).isFalse()
    }

    @Test
    fun `a dark reader background asks for light status bar icons and a light one for dark icons`() {
        assertThat(readerBackgroundIsDark(Color(0xFF111111))).isTrue()
        assertThat(readerBackgroundIsDark(Color(0xFFF7F4EC))).isFalse()
    }

    @Test
    fun `the captured status bar appearance is restored exactly`() {
        assertThat(restoreBarAppearanceDark(capturedLightStatusBars = true)).isFalse()
        assertThat(restoreBarAppearanceDark(capturedLightStatusBars = false)).isTrue()
    }

    @Test
    fun `the reserve is 24dp with a 16dp line height`() {
        assertThat(readerStatusReserve(labelLineHeight = 16.dp)).isEqualTo(24.dp)
    }

    @Test
    fun `the reserve grows with the label line height`() {
        assertThat(readerStatusReserve(labelLineHeight = 32.dp)).isEqualTo(40.dp)
    }

    @Test
    fun `the reserve never drops below the icon plus padding`() {
        assertThat(readerStatusReserve(labelLineHeight = 8.dp)).isEqualTo(24.dp)
        assertThat(readerStatusReserve(labelLineHeight = 0.dp)).isEqualTo(24.dp)
    }
}
