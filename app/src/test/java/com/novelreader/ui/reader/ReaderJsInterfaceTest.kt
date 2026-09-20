package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderJsInterfaceTest {

    @Test
    fun `onSwipe delegates once to the swipe callback with direction and axis`() {
        var received: Pair<String, String>? = null
        val bridge = ReaderJsInterface(
            onTapCallback = { _, _ -> },
            onSwipeCallback = { direction, axis -> received = direction to axis },
            onAutoScrollReachedEndCallback = {},
            onScrollRestoreCompleteCallback = {}
        )

        bridge.onSwipe("next", "v")

        assertThat(received).isEqualTo("next" to "v")
    }

    @Test
    fun `onTap delegates once with the coordinates and the viewport width`() {
        var received: Pair<Int, Int>? = null
        val bridge = ReaderJsInterface(
            onTapCallback = { x, width -> received = x to width },
            onSwipeCallback = { _, _ -> },
            onAutoScrollReachedEndCallback = {},
            onScrollRestoreCompleteCallback = {}
        )

        bridge.onTap(120, 360)

        assertThat(received).isEqualTo(120 to 360)
    }

    @Test
    fun `onAutoScrollReachedEnd delegates once to the auto scroll callback`() {
        var calls = 0
        val bridge = ReaderJsInterface(
            onTapCallback = { _, _ -> },
            onSwipeCallback = { _, _ -> },
            onAutoScrollReachedEndCallback = { calls++ },
            onScrollRestoreCompleteCallback = {}
        )

        bridge.onAutoScrollReachedEnd()

        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `onScrollRestoreComplete delegates once to the scroll restore callback`() {
        var calls = 0
        val bridge = ReaderJsInterface(
            onTapCallback = { _, _ -> },
            onSwipeCallback = { _, _ -> },
            onAutoScrollReachedEndCallback = {},
            onScrollRestoreCompleteCallback = { calls++ }
        )

        bridge.onScrollRestoreComplete(42)

        assertThat(calls).isEqualTo(1)
    }
}
