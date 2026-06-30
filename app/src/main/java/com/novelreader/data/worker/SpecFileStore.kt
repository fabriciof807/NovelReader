package com.novelreader.data.worker

import com.novelreader.domain.usecase.ImportJobSpec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpecFileStore @Inject constructor(
    private val baseDir: File
) {
    init {
        if (!baseDir.exists()) baseDir.mkdirs()
    }

    fun write(spec: ImportJobSpec) {
        val file = fileFor(spec.id)
        file.writeText(encode(spec))
    }

    fun read(id: UUID): ImportJobSpec? {
        val file = fileFor(id)
        if (!file.exists()) return null
        return try {
            decode(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun delete(id: UUID) {
        val file = fileFor(id)
        if (file.exists()) file.delete()
    }

    fun deleteAll() {
        val files = baseDir.listFiles() ?: return
        files.filter { it.isFile && it.name.endsWith(SUFFIX) }.forEach { it.delete() }
    }

    private fun fileFor(id: UUID): File = File(baseDir, "$id$SUFFIX")

    companion object {
        private const val SUFFIX = ".json"

        @Throws(IOException::class)
        fun encode(spec: ImportJobSpec): String {
            val obj = JSONObject()
            obj.put("id", spec.id.toString())
            obj.put("novelTitle", spec.novelTitle)
            val cover = spec.coverUrl
            if (cover != null) obj.put("coverUrl", cover)
            obj.put("enqueuedAt", spec.enqueuedAt)
            obj.put("splitCount", spec.splitCount)
            obj.put("splitIndex", spec.splitIndex)
            obj.put("sourceUrl", spec.sourceUrl)
            obj.put("domain", spec.domain)
            if (spec.targetNovelId != null) obj.put("targetNovelId", spec.targetNovelId)
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

        fun decode(json: String): ImportJobSpec? {
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
                ImportJobSpec(id, title, links, numbers, cover, enqueuedAt, splitCount, splitIndex, sourceUrl, domain, targetNovelId)
            } catch (_: Exception) {
                null
            }
        }
    }
}
