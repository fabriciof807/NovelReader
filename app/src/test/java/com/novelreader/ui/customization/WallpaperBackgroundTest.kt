package com.novelreader.ui.customization

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WallpaperBackgroundTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setBackground(ref: String, blur: Int = 0, veil: Int = 0) {
        composeTestRule.setContent {
            NovelReaderTheme {
                WallpaperBackground(
                    ref = ref,
                    blur = blur,
                    veil = veil,
                    veilColor = Color.Black,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    @Test
    fun `draws nothing when the reference is none`() {
        setBackground(PreferenceAllowlists.WALLPAPER_NONE)

        composeTestRule.onNodeWithTag(WALLPAPER_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(WALLPAPER_VEIL_TAG).assertDoesNotExist()
    }

    @Test
    fun `draws a builtin wallpaper`() {
        setBackground("builtin:noite")

        composeTestRule.onNodeWithTag(WALLPAPER_TAG).assertExists()
        composeTestRule.onNodeWithTag(WALLPAPER_VEIL_TAG).assertDoesNotExist()
    }

    @Test
    fun `draws the veil over a builtin wallpaper`() {
        setBackground("builtin:aurora", veil = 80)

        composeTestRule.onNodeWithTag(WALLPAPER_TAG).assertExists()
        composeTestRule.onNodeWithTag(WALLPAPER_VEIL_TAG).assertExists()
    }

    @Test
    fun `skips the veil when it is zero`() {
        setBackground("builtin:aurora", veil = 0)

        composeTestRule.onNodeWithTag(WALLPAPER_VEIL_TAG).assertDoesNotExist()
    }

    @Test
    fun `a missing image file draws nothing instead of crashing`() {
        setBackground("file:does_not_exist.jpg", blur = 20, veil = 80)

        composeTestRule.onNodeWithTag(WALLPAPER_TAG).assertDoesNotExist()
    }
}
