package com.novelreader.ui.test

import androidx.compose.material3.Text
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class ComposeUiTestBaseSmokeTest : ComposeUiTestBase() {

    @Test
    fun `setNovelReaderContent renders text and assertTextDisplayed passes`() {
        setNovelReaderContent { Text("hello") }
        assertTextDisplayed("hello")
    }
}
