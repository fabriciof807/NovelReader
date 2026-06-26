package com.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        val LOCALE = stringPreferencesKey("locale")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
    }

    val appTheme: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[Keys.APP_THEME] ?: "system"
    }

    suspend fun updateAppTheme(theme: String) {
        context.appDataStore.edit { it[Keys.APP_THEME] = theme }
    }

    val locale: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[Keys.LOCALE] ?: "pt"
    }

    suspend fun updateLocale(locale: String) {
        context.appDataStore.edit { it[Keys.LOCALE] = locale }
        context.getSharedPreferences("locale_sync", Context.MODE_PRIVATE)
            .edit().putString("locale", locale).apply()
    }

    val dynamicColorEnabled: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: true
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }
    }
}
