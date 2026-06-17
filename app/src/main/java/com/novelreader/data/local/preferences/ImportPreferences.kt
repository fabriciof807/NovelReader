package com.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novelreader.domain.usecase.ImportJobSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.importDataStore: DataStore<Preferences> by preferencesDataStore(name = "import_prefs")

@Singleton
class ImportPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PENDING_QUEUE = stringSetPreferencesKey("pending_queue")
    }

    val pendingQueue: Flow<List<ImportJobSpec>> = context.importDataStore.data.map { prefs ->
        prefs[Keys.PENDING_QUEUE]
            ?.mapNotNull { decode(it) }
            ?: emptyList()
    }

    suspend fun enqueueJob(spec: ImportJobSpec) {
        context.importDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_QUEUE] ?: emptySet()
            prefs[Keys.PENDING_QUEUE] = current + encode(spec)
        }
    }

    suspend fun dequeueJob(): ImportJobSpec? {
        var dequeued: ImportJobSpec? = null
        context.importDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_QUEUE] ?: emptySet()
            val firstJson = current.firstOrNull() ?: return@edit
            val spec = decode(firstJson)
            if (spec != null) {
                dequeued = spec
                prefs[Keys.PENDING_QUEUE] = current - firstJson
            }
        }
        return dequeued
    }

    suspend fun removeJob(id: UUID) {
        context.importDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_QUEUE] ?: emptySet()
            val filtered = current.filter { decode(it)?.id != id }.toSet()
            prefs[Keys.PENDING_QUEUE] = filtered
        }
    }

    suspend fun clearQueue() {
        context.importDataStore.edit { prefs ->
            prefs[Keys.PENDING_QUEUE] = emptySet()
        }
    }

    suspend fun queueSize(): Int = pendingQueue.first().size

    private fun encode(spec: ImportJobSpec): String {
        val obj = JSONObject()
        obj.put("id", spec.id.toString())
        obj.put("novelTitle", spec.novelTitle)
        obj.put("coverUrl", spec.coverUrl ?: JSONObject.NULL)
        obj.put("enqueuedAt", spec.enqueuedAt)
        obj.put("splitCount", spec.splitCount)
        obj.put("splitIndex", spec.splitIndex)
        obj.put("sourceUrl", spec.sourceUrl)
        val linksArr = JSONArray()
        val numsArr = JSONArray()
        spec.links.forEachIndexed { i, url ->
            linksArr.put(url)
            numsArr.put(spec.chapterNumbers.getOrElse(i) { Int.MAX_VALUE })
        }
        obj.put("links", linksArr)
        obj.put("chapterNumbers", numsArr)
        return obj.toString()
    }

    private fun decode(json: String): ImportJobSpec? {
        return try {
            val obj = JSONObject(json)
            val id = UUID.fromString(obj.optString("id"))
            val title = obj.optString("novelTitle")
            val cover = obj.optString("coverUrl").takeIf { it.isNotBlank() && it != "null" }
            val enqueuedAt = obj.optLong("enqueuedAt")
            val linksArr = obj.optJSONArray("links") ?: JSONArray()
            val numsArr = obj.optJSONArray("chapterNumbers") ?: JSONArray()
            val links = (0 until linksArr.length()).map { linksArr.getString(it) }
            val numbers = (0 until numsArr.length()).map { numsArr.getInt(it) }
            val splitCount = obj.optInt("splitCount", 1)
            val splitIndex = obj.optInt("splitIndex", 0)
            val sourceUrl = obj.optString("sourceUrl", "")
            ImportJobSpec(id, title, links, numbers, cover, enqueuedAt, splitCount, splitIndex, sourceUrl)
        } catch (_: Exception) {
            null
        }
    }
}
