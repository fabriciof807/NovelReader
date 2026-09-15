package com.novelreader.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.local.preferences.ReaderConfig
import com.novelreader.data.local.preferences.ReaderPreferences
import com.novelreader.data.local.preferences.SavedTheme
import com.novelreader.data.local.preferences.SavedThemeCodec
import com.novelreader.data.storage.WallpaperStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class VisualThemeUseCaseTest {

    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val readerPreferences: ReaderPreferences = mockk(relaxed = true)
    private val wallpaperStorage: WallpaperStorage = mockk(relaxed = true)
    private lateinit var useCase: VisualThemeUseCase

    private val saved = slot<List<SavedTheme>>()

    @Before
    fun setUp() {
        useCase = VisualThemeUseCase(appPreferences, readerPreferences, wallpaperStorage)
        every { appPreferences.appPalette } returns flowOf("floresta")
        every { appPreferences.accentColor } returns flowOf("#2e7d32")
        every { appPreferences.savedThemes } returns flowOf(emptyList())
        every { readerPreferences.config } returns flowOf(
            ReaderConfig(theme = "papel:dark", accentColor = "#8d6e63")
        )
        coEvery { appPreferences.updateSavedThemes(capture(saved)) } returns Unit
    }

    @Test
    fun `saving snapshots the current palette, accents and reader theme`() = runTest {
        assertThat(useCase.saveCurrent("Noite")).isTrue()

        assertThat(saved.captured).containsExactly(
            SavedTheme(
                name = "Noite",
                palette = "floresta",
                accentColor = "#2e7d32",
                readerTheme = "papel:dark",
                readerAccentColor = "#8d6e63"
            )
        )
    }

    @Test
    fun `saving never touches the wallpapers`() = runTest {
        useCase.saveCurrent("Noite")

        coVerify(exactly = 0) { appPreferences.updateHomeWallpaper(any()) }
        coVerify(exactly = 0) { readerPreferences.updateWallpaper(any()) }
    }

    @Test
    fun `a blank name is refused`() = runTest {
        assertThat(useCase.saveCurrent("   ")).isFalse()

        coVerify(exactly = 0) { appPreferences.updateSavedThemes(any()) }
    }

    @Test
    fun `saving with an existing name overwrites that slot`() = runTest {
        val existing = listOf(
            SavedTheme("Noite", "indigo", null, "auto", null),
            SavedTheme("Dia", "papel", null, "auto", null)
        )
        every { appPreferences.savedThemes } returns flowOf(existing)

        assertThat(useCase.saveCurrent("noite")).isTrue()

        assertThat(saved.captured.map { it.name }).containsExactly("noite", "Dia").inOrder()
        assertThat(saved.captured.first().palette).isEqualTo("floresta")
    }

    @Test
    fun `a sixth theme is refused while the slots are full`() = runTest {
        val full = (1..SavedThemeCodec.MAX_THEMES).map {
            SavedTheme("Tema $it", "papel", null, "auto", null)
        }
        every { appPreferences.savedThemes } returns flowOf(full)

        assertThat(useCase.saveCurrent("Extra")).isFalse()
        assertThat(useCase.saveCurrent("Tema 3")).isTrue()

        assertThat(saved.captured).hasSize(SavedThemeCodec.MAX_THEMES)
        assertThat(saved.captured.map { it.name }).contains("Tema 3")
    }

    @Test
    fun `applying a theme writes the four stored values`() = runTest {
        val theme = SavedTheme("Noite", "amoled", "#7c4dff", "papel:dark", "#8d6e63")

        useCase.apply(theme)

        coVerify { appPreferences.updateAppPalette("amoled") }
        coVerify { appPreferences.updateAccentColor("#7c4dff") }
        coVerify { readerPreferences.updateTheme("papel:dark") }
        coVerify { readerPreferences.updateAccentColor("#8d6e63") }
    }

    @Test
    fun `applying a theme with no accent clears the accent`() = runTest {
        useCase.apply(SavedTheme("Limpo", "floresta", null, "auto", null))

        coVerify { appPreferences.updateAccentColor(null) }
        coVerify { readerPreferences.updateAccentColor(null) }
    }

    @Test
    fun `applying a theme does not touch the wallpapers`() = runTest {
        useCase.apply(SavedTheme("Noite", "amoled", null, "auto", null))

        coVerify(exactly = 0) { appPreferences.updateHomeWallpaper(any()) }
        coVerify(exactly = 0) { readerPreferences.updateWallpaper(any()) }
        coVerify(exactly = 0) { wallpaperStorage.clearSlot(any()) }
    }

    @Test
    fun `deleting removes the theme by name ignoring case`() = runTest {
        val existing = listOf(
            SavedTheme("Noite", "indigo", null, "auto", null),
            SavedTheme("Dia", "papel", null, "auto", null)
        )
        every { appPreferences.savedThemes } returns flowOf(existing)

        useCase.delete("noite")

        assertThat(saved.captured.map { it.name }).containsExactly("Dia")
    }

    @Test
    fun `resetting restores every visual preference and deletes the images`() = runTest {
        useCase.resetToDefaults(sdkInt = 33)

        coVerify { appPreferences.updateAppPalette(PreferenceAllowlists.PALETTE_DYNAMIC) }
        coVerify { appPreferences.updateAccentColor(null) }
        coVerify { appPreferences.updateHomeWallpaper(PreferenceAllowlists.WALLPAPER_NONE) }
        coVerify { appPreferences.updateHomeWallpaperBlur(0) }
        coVerify { appPreferences.updateWallpaperBehindBars(true) }
        coVerify { wallpaperStorage.clearSlot(WallpaperStorage.SLOT_HOME) }
        coVerify { readerPreferences.updateTheme("auto") }
        coVerify { readerPreferences.updateAccentColor(null) }
        coVerify { readerPreferences.updateWallpaper(PreferenceAllowlists.WALLPAPER_NONE) }
        coVerify { readerPreferences.updateWallpaperBlur(0) }
        coVerify { readerPreferences.updateVeil(PreferenceAllowlists.DEFAULT_VEIL) }
        coVerify { wallpaperStorage.clearSlot(WallpaperStorage.SLOT_READER) }
    }

    @Test
    fun `resetting on an older device falls back to the indigo palette`() = runTest {
        useCase.resetToDefaults(sdkInt = 30)

        coVerify { appPreferences.updateAppPalette("indigo") }
    }

    @Test
    fun `resetting keeps the saved themes`() = runTest {
        useCase.resetToDefaults(sdkInt = 33)

        coVerify(exactly = 0) { appPreferences.updateSavedThemes(any()) }
    }
}
