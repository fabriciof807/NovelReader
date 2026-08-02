package com.novelreader.data.local.preferences

import android.util.Log
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

private const val RETRY_TAG = "ImportRetry"

@Singleton
class ImportPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PENDING_QUEUE = stringPreferencesKey("pending_queue")
    }

    val pendingQueue: Flow<List<ImportJobSpec>> = context.importDataStore.data.map { prefs ->
        val json = prefs[Keys.PENDING_QUEUE] ?: "[]"
        try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { decode(array.getString(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun enqueueJob(spec: ImportJobSpec) {
        context.importDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_QUEUE] ?: "[]"
            val array = try { JSONArray(current) } catch (_: Exception) { JSONArray() }
            array.put(encode(spec))
            prefs[Keys.PENDING_QUEUE] = array.toString()
            Log.w(RETRY_TAG, "enqueueJob id=${spec.id} title=${spec.novelTitle} queueSize=${array.length()}")
        }
    }

    suspend fun dequeueJob(): ImportJobSpec? {
        var dequeued: ImportJobSpec? = null
        context.importDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_QUEUE] ?: "[]"
            val array = try { JSONArray(current) } catch (_: Exception) { JSONArray() }
            if (array.length() == 0) {
                Log.w(RETRY_TAG, "dequeueJob queue=empty")
                return@edit
            }
            val firstJson = array.getString(0)
            val spec = decode(firstJson)
            if (spec != null) {
                dequeued = spec
                val newArray = JSONArray()
                for (i in 1 until array.length()) {
                    newArray.put(array.get(i))
                }
                prefs[Keys.PENDING_QUEUE] = newArray.toString()
                Log.w(RETRY_TAG, "dequeueJob id=${spec.id} title=${spec.novelTitle} remaining=${newArray.length()}")
            }
        }
        return dequeued
    }

    suspend fun removeJob(id: UUID) {
        context.importDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_QUEUE] ?: "[]"
            val array = try { JSONArray(current) } catch (_: Exception) { JSONArray() }
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val spec = decode(array.getString(i))
                if (spec?.id != id) {
                    newArray.put(array.get(i))
                }
            }
            prefs[Keys.PENDING_QUEUE] = newArray.toString()
        }
    }

    suspend fun getJobsByNovelTitle(title: String): List<ImportJobSpec> =
        pendingQueue.first().filter { it.novelTitle == title }

    suspend fun removeJobsByNovelTitle(title: String) {
        context.importDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_QUEUE] ?: "[]"
            val array = try { JSONArray(current) } catch (_: Exception) { JSONArray() }
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val spec = decode(array.getString(i))
                if (spec?.novelTitle != title) {
                    newArray.put(array.get(i))
                }
            }
            prefs[Keys.PENDING_QUEUE] = newArray.toString()
        }
    }

    suspend fun clearQueue() {
        context.importDataStore.edit { prefs ->
            prefs[Keys.PENDING_QUEUE] = "[]"
        }
    }

    suspend fun queueSize(): Int = pendingQueue.first().size

    companion object {
        internal fun encode(spec: ImportJobSpec): String {
            val obj = JSONObject()
            obj.put("id", spec.id.toString())
            obj.put("novelTitle", spec.novelTitle)
            obj.put("coverUrl", spec.coverUrl ?: JSONObject.NULL)
            obj.put("enqueuedAt", spec.enqueuedAt)
            obj.put("splitCount", spec.splitCount)
            obj.put("splitIndex", spec.splitIndex)
            obj.put("sourceUrl", spec.sourceUrl)
            obj.put("domain", spec.domain)
            if (spec.targetNovelId != null) obj.put("targetNovelId", spec.targetNovelId)
            if (spec.isFavorite != null) obj.put("isFavorite", spec.isFavorite)
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

        internal fun decode(json: String): ImportJobSpec? {
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
                val domain = obj.optString("domain", "")
                val targetNovelId = if (obj.has("targetNovelId")) obj.optLong("targetNovelId") else null
                val isFavorite = if (obj.has("isFavorite")) obj.getBoolean("isFavorite") else null
                ImportJobSpec(
                    id,
                    title,
                    links,
                    numbers,
                    cover,
                    enqueuedAt,
                    splitCount,
                    splitIndex,
                    sourceUrl,
                    domain,
                    targetNovelId,
                    isFavorite
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
