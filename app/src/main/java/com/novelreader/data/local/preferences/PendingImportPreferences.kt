package com.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pendingImportDataStore: DataStore<Preferences> by preferencesDataStore(name = "pending_import_prefs")

@Singleton
class PendingImportPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PENDING_BOOKMARKS = stringSetPreferencesKey("pending_bookmarks")
        val PENDING_CHARACTERS = stringSetPreferencesKey("pending_characters")
    }

    val pendingBookmarks: Flow<List<String>> = context.pendingImportDataStore.data.map { prefs ->
        prefs[Keys.PENDING_BOOKMARKS]?.toList() ?: emptyList()
    }

    val pendingCharacters: Flow<List<String>> = context.pendingImportDataStore.data.map { prefs ->
        prefs[Keys.PENDING_CHARACTERS]?.toList() ?: emptyList()
    }

    suspend fun savePendingBookmarks(bookmarks: List<String>) {
        context.pendingImportDataStore.edit { prefs ->
            val existing = prefs[Keys.PENDING_BOOKMARKS] ?: emptySet()
            prefs[Keys.PENDING_BOOKMARKS] = existing + bookmarks.toSet()
        }
    }

    suspend fun savePendingCharacters(characters: List<String>) {
        context.pendingImportDataStore.edit { prefs ->
            val existing = prefs[Keys.PENDING_CHARACTERS] ?: emptySet()
            prefs[Keys.PENDING_CHARACTERS] = existing + characters.toSet()
        }
    }

    suspend fun clearAll() {
        context.pendingImportDataStore.edit { prefs ->
            prefs[Keys.PENDING_BOOKMARKS] = emptySet()
            prefs[Keys.PENDING_CHARACTERS] = emptySet()
        }
    }

    suspend fun pendingCounts(): Pair<Int, Int> {
        val bookmarks = context.pendingImportDataStore.data.first()[Keys.PENDING_BOOKMARKS]
        val characters = context.pendingImportDataStore.data.first()[Keys.PENDING_CHARACTERS]
        return (bookmarks?.size ?: 0) to (characters?.size ?: 0)
    }

    companion object {
        fun encodeBookmark(bm: JSONObject): String {
            val obj = JSONObject()
            obj.put("title", bm.optString("title"))
            obj.put("note", bm.optString("note"))
            obj.put("page", bm.optInt("page"))
            obj.put("scrollPosition", bm.optInt("scrollPosition"))
            obj.put("createdAt", bm.optLong("createdAt"))
            val chapter = bm.optJSONObject("chapter")
            if (chapter != null) {
                obj.put("chapterTitle", chapter.optString("title"))
                obj.put("chapterFileName", chapter.optString("fileName"))
                obj.put("chapterOrderIndex", chapter.optInt("orderIndex"))
            }
            return obj.toString()
        }

        fun encodeCharacter(char: JSONObject): String {
            val obj = JSONObject()
            obj.put("name", char.optString("name"))
            obj.put("notes", char.optString("notes"))
            obj.put("isFavorite", char.optBoolean("isFavorite"))
            obj.put("photoPath", char.optString("photoPath"))
            obj.put("createdAt", char.optLong("createdAt"))
            obj.put("novelTitle", char.optString("novelTitle"))
            val photos = char.optJSONArray("photos")
            if (photos != null) {
                val arr = JSONArray()
                for (i in 0 until photos.length()) {
                    val p = photos.getJSONObject(i)
                    arr.put(JSONObject().apply {
                        put("photoPath", p.optString("photoPath"))
                        put("orderIndex", p.optInt("orderIndex"))
                    })
                }
                obj.put("photos", arr)
            }
            return obj.toString()
        }
    }
}
