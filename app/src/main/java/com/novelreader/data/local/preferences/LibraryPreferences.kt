package com.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(name = "library_prefs")

@Singleton
class LibraryPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val CHAPTER_SORT_ORDER = stringPreferencesKey("chapter_sort_order")
        val VIEW_MODE = stringPreferencesKey("view_mode")
    }

    val sortOrder: Flow<String> = context.libraryDataStore.data.map { prefs ->
        PreferenceAllowlists.sanitizeSortOrder(prefs[Keys.SORT_ORDER])
    }

    suspend fun updateSortOrder(order: String) {
        context.libraryDataStore.edit { it[Keys.SORT_ORDER] = PreferenceAllowlists.sanitizeSortOrder(order) }
    }

    val chapterSortOrder: Flow<String> = context.libraryDataStore.data.map { prefs ->
        PreferenceAllowlists.sanitizeChapterSortOrder(prefs[Keys.CHAPTER_SORT_ORDER])
    }

    suspend fun updateChapterSortOrder(order: String) {
        context.libraryDataStore.edit {
            it[Keys.CHAPTER_SORT_ORDER] = PreferenceAllowlists.sanitizeChapterSortOrder(order)
        }
    }

    val viewMode: Flow<String> = context.libraryDataStore.data.map { prefs ->
        PreferenceAllowlists.sanitizeViewMode(prefs[Keys.VIEW_MODE])
    }

    suspend fun updateViewMode(mode: String) {
        context.libraryDataStore.edit { it[Keys.VIEW_MODE] = PreferenceAllowlists.sanitizeViewMode(mode) }
    }
}
