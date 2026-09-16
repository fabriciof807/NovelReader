package com.novelreader.ui.customization

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.storage.MAX_CROP_ZOOM
import com.novelreader.data.storage.WallpaperCrop
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class WallpaperCropOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var applied: Triple<WallpaperCrop, Int, Int>? = null
    private var skipped = false
    private var cancelled = false

    private fun setOverlay(veil: Boolean = false, bitmap: Bitmap? = null) {
        composeTestRule.setContent {
            NovelReaderTheme {
                WallpaperCropOverlay(
                    imageModel = bitmap,
                    title = "Biblioteca",
                    topBarColor = Color(0xCC16213E),
                    bottomBarColor = Color(0xCC16213E),
                    veilColor = if (veil) Color(0xFF0A0A0F) else null,
                    veilAlpha = if (veil) 0.8f else 0f,
                    onApply = { crop, width, height -> applied = Triple(crop, width, height) },
                    onSkipCrop = { skipped = true },
                    onCancel = { cancelled = true }
                )
            }
        }
    }

    @Test
    fun `shows the preview, the zoom control and the three actions`() {
        setOverlay()

        composeTestRule.onNodeWithTag(CROP_PREVIEW_TAG).assertExists()
        composeTestRule.onNodeWithText("Zoom: 100%").assertExists()
        composeTestRule.onNodeWithText("Cancelar").assertExists()
        composeTestRule.onNodeWithText("Usar sem ajustar").assertExists()
        composeTestRule.onNodeWithText("Aplicar").assertExists()
    }

    @Test
    fun `applying reports the crop and the screen size`() {
        setOverlay()

        composeTestRule.onNodeWithText("Aplicar")
            .performSemanticsAction(SemanticsActions.OnClick)

        val result = requireNotNull(applied)
        assertThat(result.first).isEqualTo(WallpaperCrop(zoom = 1f, panX = 0f, panY = 0f))
        assertThat(result.second).isGreaterThan(0)
        assertThat(result.third).isGreaterThan(0)
        assertThat(result.second).isLessThan(result.third)
    }

    @Test
    fun `the zoom slider feeds the preview and the reported crop`() {
        setOverlay()

        composeTestRule.onNodeWithText("Zoom: 100%").assertExists()
        composeTestRule.onNodeWithTag(CROP_PREVIEW_TAG).assertExists()

        composeTestRule.onNodeWithText("Aplicar")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(requireNotNull(applied).first.zoom).isEqualTo(1f)

        val slider = androidx.compose.ui.test.SemanticsMatcher
            .keyIsDefined(SemanticsActions.SetProgress)
        composeTestRule.onAllNodes(slider)[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(MAX_CROP_ZOOM) }
        composeTestRule.onNodeWithText("Zoom: 400%").assertExists()

        composeTestRule.onNodeWithText("Aplicar")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(requireNotNull(applied).first.zoom).isEqualTo(MAX_CROP_ZOOM)
    }

    @Test
    fun `skipping keeps the original image`() {
        setOverlay()

        composeTestRule.onNodeWithText("Usar sem ajustar")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(skipped).isTrue()
        assertThat(applied).isNull()
    }

    @Test
    fun `cancelling applies nothing`() {
        setOverlay()

        composeTestRule.onNodeWithText("Cancelar")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(cancelled).isTrue()
        assertThat(applied).isNull()
        assertThat(skipped).isFalse()
    }

    @Test
    fun `works with the reader veil too`() {
        setOverlay(veil = true)

        composeTestRule.onNodeWithTag(CROP_PREVIEW_TAG).assertExists()
        composeTestRule.onNodeWithText("Aplicar")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(applied).isNotNull()
    }

    // The gesture detector is a long-lived pointerInput block, so reading the crop it was built with
    // means a drag made after the zoom slider overwrote the zoom with the stale value (the frame
    // snapped back to 100%) and the pan never accumulated.
    // The gesture detector reads the crop it captured when its pointerInput block was built, so a
    // pan made after the image loaded used the stale pan and overwrote a newer zoom.
    @Test
    fun `dragging after choosing a zoom keeps the zoom and moves the frame`() {
        setOverlay(bitmap = Bitmap.createBitmap(400, 1200, Bitmap.Config.ARGB_8888))

        composeTestRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        composeTestRule.onNodeWithText("Zoom: 200%").assertExists()

        composeTestRule.onNodeWithTag(CROP_PREVIEW_TAG).performTouchInput {
            swipe(center, center + Offset(80f, 0f), 200)
        }

        composeTestRule.onNodeWithText("Zoom: 200%").assertExists()
        composeTestRule.onNodeWithText("Aplicar")
            .performSemanticsAction(SemanticsActions.OnClick)
        val crop = requireNotNull(applied).first
        assertThat(crop.zoom).isEqualTo(2f)
        assertThat(crop.panX).isGreaterThan(0.02f)
    }
}
