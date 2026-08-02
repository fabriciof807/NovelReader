package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderJsInterfaceTest {

    @Test
    fun `onSwipe delegates once to the swipe callback with direction and axis`() {
        var received: Pair<String, String>? = null
        val bridge = ReaderJsInterface(
            onTextSelectedCallback = {},
            onTapCallback = {},
            onSwipeCallback = { direction, axis -> received = direction to axis },
            onAutoScrollReachedEndCallback = {},
            onScrollRestoreCompleteCallback = {}
        )

        bridge.onSwipe("next", "v")

        assertThat(received).isEqualTo("next" to "v")
    }

    @Test
    fun `onTap delegates once to the tap callback`() {
        var calls = 0
        val bridge = ReaderJsInterface(
            onTextSelectedCallback = {},
            onTapCallback = { calls++ },
            onSwipeCallback = { _, _ -> },
            onAutoScrollReachedEndCallback = {},
            onScrollRestoreCompleteCallback = {}
        )

        bridge.onTap()

        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `onTextSelected delegates once to the text selection callback`() {
        var calls = 0
        val bridge = ReaderJsInterface(
            onTextSelectedCallback = { calls++ },
            onTapCallback = {},
            onSwipeCallback = { _, _ -> },
            onAutoScrollReachedEndCallback = {},
            onScrollRestoreCompleteCallback = {}
        )

        bridge.onTextSelected("selection")

        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `onAutoScrollReachedEnd delegates once to the auto scroll callback`() {
        var calls = 0
        val bridge = ReaderJsInterface(
            onTextSelectedCallback = {},
            onTapCallback = {},
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
            onTextSelectedCallback = {},
            onTapCallback = {},
            onSwipeCallback = { _, _ -> },
            onAutoScrollReachedEndCallback = {},
            onScrollRestoreCompleteCallback = { calls++ }
        )

        bridge.onScrollRestoreComplete(42)

        assertThat(calls).isEqualTo(1)
    }
}
