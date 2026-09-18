package com.novelreader.data.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WallpaperToneTest {

    @Test
    fun `relative luminance follows the wcag channels`() {
        assertThat(relativeLuminance(0xFFFFFFFF.toInt())).isWithin(0.001f).of(1f)
        assertThat(relativeLuminance(0xFF000000.toInt())).isWithin(0.001f).of(0f)
        // Green carries most of the luminance, blue the least.
        assertThat(relativeLuminance(0xFF00FF00.toInt()))
            .isGreaterThan(relativeLuminance(0xFFFF0000.toInt()))
        assertThat(relativeLuminance(0xFFFF0000.toInt()))
            .isGreaterThan(relativeLuminance(0xFF0000FF.toInt()))
    }

    @Test
    fun `the average of the sampled pixels decides the tone`() {
        assertThat(isLightWallpaper(averageLuminance(intArrayOf(0xFFFFFFFF.toInt())))).isTrue()
        assertThat(isLightWallpaper(averageLuminance(intArrayOf(0xFF000000.toInt())))).isFalse()

        // A gradient straddling the middle of the scale is the case the issue calls out: the solid
        // light "areia" reads light, a mid-tone sunrise gradient does not.
        assertThat(isLightWallpaper(averageLuminance(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))))
            .isTrue()
        assertThat(isLightWallpaper(averageLuminance(intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt()))))
            .isFalse()
    }

    // WCAG luminance is not linear: mid grey #808080 carries only 0.22 of it, so the threshold needs
    // a genuinely light wallpaper. That is the side we want to err on — a mid-tone background sits
    // closer to the dark surface than to the light one, which is what makes it readable.
    @Test
    fun `mid greys stay dark and only a light wallpaper switches the containers`() {
        assertThat(averageLuminance(intArrayOf(0xFF808080.toInt())))
            .isLessThan(LIGHT_WALLPAPER_LUMINANCE)
        assertThat(isLightWallpaper(averageLuminance(intArrayOf(0xFFB0B0B0.toInt())))).isFalse()
        assertThat(isLightWallpaper(averageLuminance(intArrayOf(0xFFBCBCBC.toInt())))).isTrue()
    }

    @Test
    fun `transparent pixels do not pull the tone down`() {
        val pixels = intArrayOf(0x00000000, 0xFFFFFFFF.toInt())

        assertThat(averageLuminance(pixels)).isWithin(0.001f).of(1f)
    }

    @Test
    fun `an image with nothing opaque falls back to dark`() {
        assertThat(averageLuminance(intArrayOf(0x00000000, 0x00123456))).isWithin(0.001f).of(0f)
        assertThat(averageLuminance(intArrayOf())).isWithin(0.001f).of(0f)
    }

    // Decoding a 4000px wallpaper at full size to average it would cost tens of MB; the sample is
    // deliberately tiny, so the sample size has to shrink the longer edge as far as the target allows
    // without going under half of it (that would be a needless second decode at lower fidelity).
    @Test
    fun `the sample size shrinks the longer edge to the target`() {
        assertThat(sampleSizeFor(width = 4096, height = 3072, targetEdge = 64)).isEqualTo(64)
        assertThat(sampleSizeFor(width = 100, height = 50, targetEdge = 64)).isEqualTo(2)
        assertThat(sampleSizeFor(width = 64, height = 64, targetEdge = 64)).isEqualTo(1)
        assertThat(sampleSizeFor(width = 12, height = 8, targetEdge = 64)).isEqualTo(1)
    }

    @Test
    fun `the sample size is a power of two that keeps the edge within the window`() {
        val target = 64
        listOf(1 to 1, 63 to 64, 100 to 50, 1920 to 1080, 4096 to 3072, 12000 to 9000).forEach { (w, h) ->
            val size = sampleSizeFor(w, h, target)
            val longerEdge = maxOf(w, h) / size

            assertThat(size).isAtLeast(1)
            assertThat(size and (size - 1)).isEqualTo(0)
            assertThat(longerEdge).isAtMost(target)
            if (maxOf(w, h) > target) {
                assertThat(longerEdge).isGreaterThan(target / 2)
            }
        }
    }
}
