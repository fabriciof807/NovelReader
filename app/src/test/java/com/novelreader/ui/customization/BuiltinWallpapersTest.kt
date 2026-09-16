package com.novelreader.ui.customization

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.storage.WallpaperStorage
import org.junit.Test

class BuiltinWallpapersTest {

    private val solids = listOf("areia", "ardosia", "musgo")

    @Test
    fun `every builtin id is accepted by the preferences allowlist`() {
        BUILTIN_WALLPAPERS.keys.forEach { id ->
            assertThat(PreferenceAllowlists.sanitizeWallpaperRef("builtin:$id"))
                .isEqualTo("builtin:$id")
            assertThat(WallpaperStorage.builtinId("builtin:$id")).isEqualTo(id)
        }
    }

    @Test
    fun `the allowlist does not drift from the rendered wallpapers`() {
        assertThat(PreferenceAllowlists.BUILTIN_WALLPAPERS)
            .containsExactlyElementsIn(BUILTIN_WALLPAPERS.keys)
    }

    @Test
    fun `the three solid options are flat single colours`() {
        solids.forEach { id ->
            val colors = BUILTIN_WALLPAPERS.getValue(id)
            assertThat(colors).hasSize(2)
            assertThat(colors[0]).isEqualTo(colors[1])
        }
    }

    @Test
    fun `the gradients are not flat`() {
        (BUILTIN_WALLPAPERS.keys - solids).forEach { id ->
            val colors = BUILTIN_WALLPAPERS.getValue(id)
            assertThat(colors.distinct().size).isAtLeast(2)
        }
    }

    @Test
    fun `solid options keep body text readable if used behind a page`() {
        solids.forEach { id ->
            val color = BUILTIN_WALLPAPERS.getValue(id).first()
            assertThat(color).isNotEqualTo(Color.Transparent)
        }
    }
}
