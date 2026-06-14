package com.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    val theme: String = "light"
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
    }

    val config: Flow<ReaderConfig> = context.dataStore.data.map { prefs ->
        ReaderConfig(
            fontSize = prefs[Keys.FONT_SIZE] ?: 20,
            fontFamily = prefs[Keys.FONT_FAMILY] ?: "serif",
            lineHeight = prefs[Keys.LINE_HEIGHT]?.toFloatOrNull() ?: 1.8f,
            theme = prefs[Keys.THEME] ?: "light"
        )
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
}
