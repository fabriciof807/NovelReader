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

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val APP_THEME = stringPreferencesKey("app_theme")
        val APP_PALETTE = stringPreferencesKey("app_palette")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val WALLPAPER_HOME = stringPreferencesKey("wallpaper_home")
        val WALLPAPER_HOME_BLUR = intPreferencesKey("wallpaper_home_blur")
        val SAVED_THEMES = stringPreferencesKey("saved_themes")
        val WALLPAPER_BEHIND_BARS = booleanPreferencesKey("wallpaper_behind_bars")
        val LOCALE = stringPreferencesKey("locale")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
    }

    val appTheme: Flow<String> = context.appDataStore.data.map { prefs ->
        PreferenceAllowlists.sanitizeAppTheme(prefs[Keys.APP_THEME])
    }

    suspend fun updateAppTheme(theme: String) {
        context.appDataStore.edit { it[Keys.APP_THEME] = PreferenceAllowlists.sanitizeAppTheme(theme) }
    }

    val appPalette: Flow<String> = context.appDataStore.data.map { prefs ->
        val stored = prefs[Keys.APP_PALETTE]
        when {
            stored != null -> PreferenceAllowlists.sanitizeAppPalette(stored)
            prefs[Keys.DYNAMIC_COLOR_ENABLED] == false -> "indigo"
            else -> PreferenceAllowlists.PALETTE_DYNAMIC
        }
    }

    suspend fun updateAppPalette(palette: String) {
        val sanitized = PreferenceAllowlists.sanitizeAppPalette(palette)
        context.appDataStore.edit {
            it[Keys.APP_PALETTE] = sanitized
            it[Keys.DYNAMIC_COLOR_ENABLED] = sanitized == PreferenceAllowlists.PALETTE_DYNAMIC
        }
    }

    val accentColor: Flow<String?> = context.appDataStore.data.map { prefs ->
        PreferenceAllowlists.sanitizeAccentColor(prefs[Keys.ACCENT_COLOR])
    }

    suspend fun updateAccentColor(color: String?) {
        context.appDataStore.edit {
            val sanitized = PreferenceAllowlists.sanitizeAccentColor(color)
            if (sanitized == null) it.remove(Keys.ACCENT_COLOR) else it[Keys.ACCENT_COLOR] = sanitized
        }
    }

    val homeWallpaper: Flow<String> = context.appDataStore.data.map { prefs ->
        PreferenceAllowlists.sanitizeWallpaperRef(prefs[Keys.WALLPAPER_HOME])
    }

    suspend fun updateHomeWallpaper(ref: String) {
        context.appDataStore.edit {
            it[Keys.WALLPAPER_HOME] = PreferenceAllowlists.sanitizeWallpaperRef(ref)
        }
    }

    val homeWallpaperBlur: Flow<Int> = context.appDataStore.data.map { prefs ->
        PreferenceAllowlists.sanitizeBlur(prefs[Keys.WALLPAPER_HOME_BLUR])
    }

    suspend fun updateHomeWallpaperBlur(blur: Int) {
        context.appDataStore.edit {
            it[Keys.WALLPAPER_HOME_BLUR] = PreferenceAllowlists.sanitizeBlur(blur)
        }
    }

    val wallpaperBehindBars: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[Keys.WALLPAPER_BEHIND_BARS] ?: true
    }

    suspend fun updateWallpaperBehindBars(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.WALLPAPER_BEHIND_BARS] = enabled }
    }

    val savedThemes: Flow<List<SavedTheme>> = context.appDataStore.data.map { prefs ->
        SavedThemeCodec.decode(prefs[Keys.SAVED_THEMES])
    }

    suspend fun updateSavedThemes(themes: List<SavedTheme>) {
        context.appDataStore.edit {
            it[Keys.SAVED_THEMES] = SavedThemeCodec.encode(themes)
        }
    }

    val locale: Flow<String> = context.appDataStore.data.map { prefs ->
        PreferenceAllowlists.sanitizeLocale(prefs[Keys.LOCALE])
    }

    suspend fun updateLocale(locale: String) {
        val sanitized = PreferenceAllowlists.sanitizeLocale(locale)
        context.appDataStore.edit { it[Keys.LOCALE] = sanitized }
        context.getSharedPreferences("locale_sync", Context.MODE_PRIVATE)
            .edit().putString("locale", sanitized).apply()
    }

    val dynamicColorEnabled: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: true
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }
    }
}
