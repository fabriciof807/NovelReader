package com.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_prefs")

data class ReaderConfig(
    val fontSize: Int = 20,
    val fontFamily: String = "serif",
    val lineHeight: Float = 1.8f,
    val theme: String = "indigo",
    val themeDark: Boolean = false,
    val accentColor: String? = null,
    val wallpaper: String = PreferenceAllowlists.WALLPAPER_NONE,
    val wallpaperBlur: Int = 0,
    val veil: Int = PreferenceAllowlists.DEFAULT_VEIL,
    val autoScrollSpeed: Float = 0f,
    val keepScreenOn: Boolean = true,
    val swipeDirection: String = "vertical",
    val brightness: Int = PreferenceAllowlists.BRIGHTNESS_SYSTEM,
    val tapZones: Boolean = false
)

@Singleton
class ReaderPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val FONT_SIZE = intPreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val LINE_HEIGHT = stringPreferencesKey("line_height")
        val THEME = stringPreferencesKey("theme")
        val ACCENT_COLOR = stringPreferencesKey("reader_accent_color")
        val WALLPAPER = stringPreferencesKey("reader_wallpaper")
        val WALLPAPER_BLUR = intPreferencesKey("reader_wallpaper_blur")
        val VEIL = intPreferencesKey("reader_veil")
        val AUTO_SCROLL_SPEED = stringPreferencesKey("auto_scroll_speed")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SWIPE_DIRECTION = stringPreferencesKey("swipe_direction")
        val BRIGHTNESS = intPreferencesKey("reader_brightness")
        val TAP_ZONES = booleanPreferencesKey("reader_tap_zones")
    }

    val config: Flow<ReaderConfig> = context.dataStore.data.map { prefs ->
        ReaderConfig(
            fontSize = prefs[Keys.FONT_SIZE] ?: 20,
            fontFamily = PreferenceAllowlists.sanitizeFontFamily(prefs[Keys.FONT_FAMILY]),
            lineHeight = prefs[Keys.LINE_HEIGHT]?.toFloatOrNull() ?: 1.8f,
            theme = PreferenceAllowlists.sanitizeReaderTheme(prefs[Keys.THEME] ?: "auto"),
            accentColor = PreferenceAllowlists.sanitizeAccentColor(prefs[Keys.ACCENT_COLOR]),
            wallpaper = PreferenceAllowlists.sanitizeWallpaperRef(prefs[Keys.WALLPAPER]),
            wallpaperBlur = PreferenceAllowlists.sanitizeBlur(prefs[Keys.WALLPAPER_BLUR]),
            veil = PreferenceAllowlists.sanitizeVeil(prefs[Keys.VEIL]),
            autoScrollSpeed = prefs[Keys.AUTO_SCROLL_SPEED]?.toFloatOrNull() ?: 0f,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            swipeDirection = PreferenceAllowlists.sanitizeSwipeDirection(prefs[Keys.SWIPE_DIRECTION]),
            brightness = PreferenceAllowlists.sanitizeBrightness(prefs[Keys.BRIGHTNESS]),
            tapZones = prefs[Keys.TAP_ZONES] ?: false
        )
    }

    suspend fun updateAutoScrollSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.AUTO_SCROLL_SPEED] = speed.toString() }
    }

    suspend fun updateFontSize(size: Int) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = size }
    }

    suspend fun updateFontFamily(family: String) {
        context.dataStore.edit { it[Keys.FONT_FAMILY] = PreferenceAllowlists.sanitizeFontFamily(family) }
    }

    suspend fun updateLineHeight(height: Float) {
        context.dataStore.edit { it[Keys.LINE_HEIGHT] = height.toString() }
    }

    suspend fun updateTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = PreferenceAllowlists.sanitizeReaderTheme(theme) }
    }

    suspend fun updateAccentColor(color: String?) {
        context.dataStore.edit {
            val sanitized = PreferenceAllowlists.sanitizeAccentColor(color)
            if (sanitized == null) it.remove(Keys.ACCENT_COLOR) else it[Keys.ACCENT_COLOR] = sanitized
        }
    }

    suspend fun updateWallpaper(ref: String) {
        context.dataStore.edit {
            it[Keys.WALLPAPER] = PreferenceAllowlists.sanitizeWallpaperRef(ref)
        }
    }

    suspend fun updateWallpaperBlur(blur: Int) {
        context.dataStore.edit {
            it[Keys.WALLPAPER_BLUR] = PreferenceAllowlists.sanitizeBlur(blur)
        }
    }

    suspend fun updateVeil(veil: Int) {
        context.dataStore.edit {
            it[Keys.VEIL] = PreferenceAllowlists.sanitizeVeil(veil)
        }
    }

    suspend fun updateKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }
    }

    suspend fun updateSwipeDirection(direction: String) {
        context.dataStore.edit {
            it[Keys.SWIPE_DIRECTION] = PreferenceAllowlists.sanitizeSwipeDirection(direction)
        }
    }

    suspend fun updateBrightness(value: Int) {
        context.dataStore.edit {
            it[Keys.BRIGHTNESS] = PreferenceAllowlists.sanitizeBrightness(value)
        }
    }

    suspend fun updateTapZones(value: Boolean) {
        context.dataStore.edit { it[Keys.TAP_ZONES] = value }
    }
}
