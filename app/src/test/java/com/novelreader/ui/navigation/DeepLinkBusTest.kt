package com.novelreader.ui.navigation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class DeepLinkBusTest {

    private val bus = DeepLinkBus()

    @Test
    fun `emitted OpenFailedChapters is received by subscriber`() = runTest {
        bus.emit(DeepLinkAction.OpenFailedChapters(42L))
        val action = bus.events.first()
        assertThat(action).isInstanceOf(DeepLinkAction.OpenFailedChapters::class.java)
        assertThat((action as DeepLinkAction.OpenFailedChapters).novelId).isEqualTo(42L)
    }

    @Test
    fun `replays last event for late subscriber`() = runTest {
        bus.emit(DeepLinkAction.OpenFailedChapters(7L))
        val action = bus.events.first()
        assertThat((action as DeepLinkAction.OpenFailedChapters).novelId).isEqualTo(7L)
    }
}
