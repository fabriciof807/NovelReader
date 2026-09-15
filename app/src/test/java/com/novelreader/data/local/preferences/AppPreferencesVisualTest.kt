package com.novelreader.data.local.preferences

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppPreferencesVisualTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = AppPreferences(context)

    @After
    fun reset() = runTest {
        prefs.updateWallpaperBehindBars(true)
        prefs.updateSavedThemes(emptyList())
    }

    @Test
    fun `wallpaper behind the bars defaults to on`() = runTest {
        assertThat(prefs.wallpaperBehindBars.first()).isTrue()
    }

    @Test
    fun `wallpaper behind the bars persists both states`() = runTest {
        prefs.updateWallpaperBehindBars(false)
        assertThat(prefs.wallpaperBehindBars.first()).isFalse()

        prefs.updateWallpaperBehindBars(true)
        assertThat(prefs.wallpaperBehindBars.first()).isTrue()
    }

    @Test
    fun `saved themes default to an empty list and round trip`() = runTest {
        assertThat(prefs.savedThemes.first()).isEmpty()

        val theme = SavedTheme("Noite", "amoled", "#7c4dff", "papel:dark", null)
        prefs.updateSavedThemes(listOf(theme))

        assertThat(prefs.savedThemes.first()).containsExactly(theme)
    }

}
