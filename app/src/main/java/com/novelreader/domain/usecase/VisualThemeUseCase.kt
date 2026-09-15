package com.novelreader.domain.usecase

import android.os.Build
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.local.preferences.ReaderPreferences
import com.novelreader.data.local.preferences.SavedTheme
import com.novelreader.data.local.preferences.SavedThemeCodec
import com.novelreader.data.storage.WallpaperStorage
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisualThemeUseCase @Inject constructor(
    private val appPreferences: AppPreferences,
    private val readerPreferences: ReaderPreferences,
    private val wallpaperStorage: WallpaperStorage
) {

    suspend fun saveCurrent(name: String): Boolean {
        val sanitized = SavedThemeCodec.sanitizeName(name) ?: return false
        val saved = appPreferences.savedThemes.first()
        val theme = SavedTheme(
            name = sanitized,
            palette = appPreferences.appPalette.first(),
            accentColor = appPreferences.accentColor.first(),
            readerTheme = readerPreferences.config.first().theme,
            readerAccentColor = readerPreferences.config.first().accentColor
        )
        val existingIndex = saved.indexOfFirst { it.name.equals(sanitized, ignoreCase = true) }
        val updated = when {
            existingIndex >= 0 -> saved.toMutableList().apply { this[existingIndex] = theme }
            saved.size < SavedThemeCodec.MAX_THEMES -> saved + theme
            else -> return false
        }
        appPreferences.updateSavedThemes(updated)
        return true
    }

    suspend fun apply(theme: SavedTheme) {
        appPreferences.updateAppPalette(theme.palette)
        appPreferences.updateAccentColor(theme.accentColor)
        readerPreferences.updateTheme(theme.readerTheme)
        readerPreferences.updateAccentColor(theme.readerAccentColor)
    }

    suspend fun delete(name: String) {
        val saved = appPreferences.savedThemes.first()
        appPreferences.updateSavedThemes(saved.filterNot { it.name.equals(name, ignoreCase = true) })
    }

    suspend fun resetToDefaults(sdkInt: Int = Build.VERSION.SDK_INT) {
        appPreferences.updateAppPalette(PreferenceAllowlists.defaultAppPalette(sdkInt))
        appPreferences.updateAccentColor(null)
        appPreferences.updateHomeWallpaper(PreferenceAllowlists.WALLPAPER_NONE)
        appPreferences.updateHomeWallpaperBlur(0)
        appPreferences.updateWallpaperBehindBars(true)
        wallpaperStorage.clearSlot(WallpaperStorage.SLOT_HOME)

        readerPreferences.updateTheme("auto")
        readerPreferences.updateAccentColor(null)
        readerPreferences.updateWallpaper(PreferenceAllowlists.WALLPAPER_NONE)
        readerPreferences.updateWallpaperBlur(0)
        readerPreferences.updateVeil(PreferenceAllowlists.DEFAULT_VEIL)
        wallpaperStorage.clearSlot(WallpaperStorage.SLOT_READER)
    }
}
