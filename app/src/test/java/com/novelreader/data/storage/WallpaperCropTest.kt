package com.novelreader.data.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WallpaperCropTest {

    @Test
    fun `cover scale fills the frame on both axes`() {
        // A wide image on a tall frame is scaled by height, and the other way round.
        assertThat(coverScale(4000f, 1000f, 1000f, 2000f)).isEqualTo(2f)
        assertThat(coverScale(1000f, 4000f, 2000f, 1000f)).isEqualTo(2f)
        assertThat(coverScale(1000f, 2000f, 1000f, 2000f)).isEqualTo(1f)
    }

    @Test
    fun `an unzoomed crop keeps the whole frame of a matching image`() {
        val region = cropRectFor(WallpaperCrop(), 1000f, 2000f, 1000f, 2000f)

        assertThat(region.left).isEqualTo(0f)
        assertThat(region.top).isEqualTo(0f)
        assertThat(region.width).isEqualTo(1000f)
        assertThat(region.height).isEqualTo(2000f)
    }

    @Test
    fun `an unzoomed crop centres an image that does not match the frame aspect`() {
        // 4000x1000 image in a 1000x2000 frame: scale 2, so the crop keeps 500x1000 of the source.
        val region = cropRectFor(WallpaperCrop(), 4000f, 1000f, 1000f, 2000f)

        assertThat(region.height).isEqualTo(1000f)
        assertThat(region.width).isEqualTo(500f)
        assertThat(region.left).isEqualTo(1750f)
        assertThat(region.top).isEqualTo(0f)
    }

    @Test
    fun `zooming in keeps a smaller region of the source`() {
        val region = cropRectFor(WallpaperCrop(zoom = 2f), 1000f, 2000f, 1000f, 2000f)

        assertThat(region.width).isEqualTo(500f)
        assertThat(region.height).isEqualTo(1000f)
        assertThat(region.left).isEqualTo(250f)
        assertThat(region.top).isEqualTo(500f)
    }

    @Test
    fun `panning moves the region and stops at the image edge`() {
        val centred = cropRectFor(WallpaperCrop(zoom = 2f), 1000f, 2000f, 1000f, 2000f)
        val panned = cropRectFor(WallpaperCrop(zoom = 2f, panX = 1f), 1000f, 2000f, 1000f, 2000f)
        val overPanned = cropRectFor(WallpaperCrop(zoom = 2f, panX = 9f), 1000f, 2000f, 1000f, 2000f)

        assertThat(panned.left).isGreaterThan(centred.left)
        assertThat(overPanned.left).isEqualTo(500f)
        assertThat(overPanned.width).isEqualTo(500f)
        assertThat(overPanned.left + overPanned.width).isAtMost(1000f)
    }

    @Test
    fun `the region always stays inside the source image`() {
        for (zoom in listOf(1f, 1.5f, 2f, 4f)) {
            for (pan in listOf(-1f, -0.4f, 0f, 0.4f, 1f)) {
                val region = cropRectFor(
                    WallpaperCrop(zoom = zoom, panX = pan, panY = pan),
                    1600f, 900f, 1080f, 2400f
                )
                assertThat(region.left).isAtLeast(0f)
                assertThat(region.top).isAtLeast(0f)
                assertThat(region.left + region.width).isAtMost(1600f + 0.01f)
                assertThat(region.top + region.height).isAtMost(900f + 0.01f)
                assertThat(region.width).isGreaterThan(0f)
                assertThat(region.height).isGreaterThan(0f)
            }
        }
    }

    @Test
    fun `the region matches the frame aspect`() {
        val region = cropRectFor(WallpaperCrop(zoom = 1.7f, panX = 0.3f), 3000f, 1200f, 1080f, 2400f)
        val frameAspect = 1080f / 2400f

        assertThat(region.width / region.height).isWithin(0.001f).of(frameAspect)
    }

    @Test
    fun `zoom and pan are clamped to the supported range`() {
        assertThat(clampCrop(zoom = 99f, panX = 5f, panY = -5f))
            .isEqualTo(WallpaperCrop(zoom = MAX_CROP_ZOOM, panX = 1f, panY = -1f))
        assertThat(clampCrop(zoom = 0.1f, panX = 0f, panY = 0f).zoom)
            .isEqualTo(MIN_CROP_ZOOM)
        assertThat(clampZoom(99f)).isEqualTo(MAX_CROP_ZOOM)
    }

    @Test
    fun `an image that overflows at zoom one can still be panned`() {
        // A tall image in a shorter frame already overflows vertically before any zoom.
        val (tallSlackX, tallSlackY) = cropSlack(1000f, 4000f, 1000f, 2000f, zoom = 1f)
        assertThat(tallSlackX).isEqualTo(0f)
        assertThat(tallSlackY).isEqualTo(1000f)

        // A wide image in a taller frame overflows the other way.
        val (wideSlackX, wideSlackY) = cropSlack(4000f, 1000f, 1000f, 2000f, zoom = 1f)
        assertThat(wideSlackX).isGreaterThan(0f)
        assertThat(wideSlackY).isEqualTo(0f)
    }

    @Test
    fun `panning that overflows moves the crop region in both directions`() {
        val top = cropRectFor(WallpaperCrop(panY = -1f), 1000f, 4000f, 1000f, 2000f)
        val bottom = cropRectFor(WallpaperCrop(panY = 1f), 1000f, 4000f, 1000f, 2000f)
        assertThat(bottom.top).isGreaterThan(top.top)

        val left = cropRectFor(WallpaperCrop(panX = -1f), 4000f, 1000f, 1000f, 2000f)
        val right = cropRectFor(WallpaperCrop(panX = 1f), 4000f, 1000f, 1000f, 2000f)
        assertThat(right.left).isGreaterThan(left.left)
    }

    @Test
    fun `the decode sample keeps big images inside the memory budget`() {
        assertThat(decodeSampleSize(1000, 1000, 4096)).isEqualTo(1)
        assertThat(decodeSampleSize(4000, 3000, 4096)).isEqualTo(1)
        assertThat(decodeSampleSize(8000, 6000, 4096)).isEqualTo(2)
        assertThat(decodeSampleSize(12000, 9000, 4096)).isEqualTo(4)
        assertThat(decodeSampleSize(60000, 1000, 4096)).isEqualTo(16)
    }
}
