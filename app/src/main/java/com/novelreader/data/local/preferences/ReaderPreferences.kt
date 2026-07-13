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
    val theme: String = "light",
    val autoScrollSpeed: Float = 0f,
    val keepScreenOn: Boolean = true,
    val swipeDirection: String = "vertical"
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
        val AUTO_SCROLL_SPEED = stringPreferencesKey("auto_scroll_speed")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SWIPE_DIRECTION = stringPreferencesKey("swipe_direction")
    }

    val config: Flow<ReaderConfig> = context.dataStore.data.map { prefs ->
        ReaderConfig(
            fontSize = prefs[Keys.FONT_SIZE] ?: 20,
            fontFamily = prefs[Keys.FONT_FAMILY] ?: "serif",
            lineHeight = prefs[Keys.LINE_HEIGHT]?.toFloatOrNull() ?: 1.8f,
            theme = prefs[Keys.THEME] ?: "light",
            autoScrollSpeed = prefs[Keys.AUTO_SCROLL_SPEED]?.toFloatOrNull() ?: 0f,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            swipeDirection = prefs[Keys.SWIPE_DIRECTION]?.takeIf { it in setOf("vertical", "horizontal", "both", "none") } ?: "vertical"
        )
    }

    suspend fun updateAutoScrollSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.AUTO_SCROLL_SPEED] = speed.toString() }
    }

    suspend fun updateFontSize(size: Int) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = size }
    }

    suspend fun updateLineHeight(height: Float) {
        context.dataStore.edit { it[Keys.LINE_HEIGHT] = height.toString() }
    }

    suspend fun updateTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = theme }
    }

    suspend fun updateKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }
    }

    suspend fun updateSwipeDirection(direction: String) {
        context.dataStore.edit { it[Keys.SWIPE_DIRECTION] = direction }
    }
}
