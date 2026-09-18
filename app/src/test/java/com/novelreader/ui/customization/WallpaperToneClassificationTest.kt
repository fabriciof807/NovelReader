package com.novelreader.ui.customization

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WallpaperToneClassificationTest {

    private val lightBuiltins = listOf("areia", "pergaminho")
    private val darkBuiltins = listOf(
        "ardosia", "musgo", "amanhecer", "aurora", "crepusculo", "bosque", "carvao", "noite", "oceano"
    )

    @Test
    fun `the light builtins ask for the light containers`() {
        lightBuiltins.forEach { id ->
            assertThat(wallpaperIsLight("builtin:$id")).isTrue()
        }
    }

    // "Amanhecer" is the interesting one: it ends on a bright yellow, but its average luminance is
    // 0.42, so the containers stay dark — a mid-tone background is closer to the dark surface than to
    // the light one, and inverting it there is what made the screen read as two themes.
    @Test
    fun `the dark builtins keep the dark containers`() {
        darkBuiltins.forEach { id ->
            assertThat(wallpaperIsLight("builtin:$id")).isFalse()
        }
    }

    @Test
    fun `every builtin has a tone`() {
        assertThat(BUILTIN_WALLPAPERS.keys)
            .containsExactlyElementsIn(lightBuiltins + darkBuiltins)
        BUILTIN_WALLPAPERS.keys.forEach { id ->
            assertThat(wallpaperIsLight("builtin:$id")).isNotNull()
        }
    }

    @Test
    fun `an image wallpaper has no tone until its pixels are sampled`() {
        assertThat(wallpaperIsLight("file:home_1699.jpg")).isNull()
        assertThat(wallpaperIsLight("builtin:desconhecido")).isNull()
        assertThat(wallpaperIsLight("none")).isNull()
    }

    @Test
    fun `the sampled pixels of an image give the tone`() {
        assertThat(sampledWallpaperIsLight(0.9f)).isTrue()
        assertThat(sampledWallpaperIsLight(0.1f)).isFalse()
        assertThat(sampledWallpaperIsLight(null)).isNull()
    }
}
