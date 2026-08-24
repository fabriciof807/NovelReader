package com.novelreader.data.storage

import android.content.Context
import com.novelreader.di.qualifiers.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PendingBookmark(
    val novelTitle: String,
    val title: String,
    val note: String?,
    val page: Int,
    val scrollPosition: Int,
    val createdAt: Long,
    val chapterFileName: String,
    val chapterOrderIndex: Int
)

data class PendingPhoto(
    val photoPath: String,
    val orderIndex: Int
)

data class PendingCharacter(
    val novelTitle: String,
    val name: String,
    val notes: String?,
    val isFavorite: Boolean,
    val photoPath: String?,
    val createdAt: Long,
    val photos: List<PendingPhoto>
)

data class PendingCollectionLink(
    val folderName: String,
    val novelTitle: String
)

data class PendingNovel(
    val title: String,
    val author: String?,
    val autoUpdate: Boolean,
    val lastReadAt: Long,
    val lastChapterFileName: String,
    val lastChapterOrderIndex: Int
)

data class PendingRestore(
    val bookmarks: List<PendingBookmark> = emptyList(),
    val characters: List<PendingCharacter> = emptyList(),
    val collectionLinks: List<PendingCollectionLink> = emptyList(),
    val novels: List<PendingNovel> = emptyList()
) {
    fun isEmpty(): Boolean = bookmarks.isEmpty() && characters.isEmpty() &&
        collectionLinks.isEmpty() && novels.isEmpty()
}

@Singleton
class PendingRestoreStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val mutex = Mutex()

    private val file: File get() = File(context.filesDir, FILE_NAME)

    suspend fun load(): PendingRestore = withContext(ioDispatcher) {
        mutex.withLock { loadLocked() }
    }

    suspend fun update(transform: (PendingRestore) -> PendingRestore) = withContext(ioDispatcher) {
        mutex.withLock {
            val updated = transform(loadLocked())
            saveLocked(updated)
        }
    }

    private fun loadLocked(): PendingRestore {
        if (!file.exists()) return PendingRestore()
        return try {
            decode(JSONObject(file.readText()))
        } catch (_: Exception) {
            PendingRestore()
        }
    }

    private fun saveLocked(pending: PendingRestore) {
        try {
            if (pending.isEmpty()) {
                file.delete()
            } else {
                file.writeText(encode(pending).toString())
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val FILE_NAME = "pending_restore.json"

        fun encode(p: PendingRestore): JSONObject = JSONObject().apply {
            put("bookmarks", JSONArray().apply {
                p.bookmarks.forEach { bm ->
                    put(JSONObject().apply {
                        put("novelTitle", bm.novelTitle)
                        put("title", bm.title)
                        put("note", bm.note ?: "")
                        put("page", bm.page)
                        put("scrollPosition", bm.scrollPosition)
                        put("createdAt", bm.createdAt)
                        put("chapterFileName", bm.chapterFileName)
                        put("chapterOrderIndex", bm.chapterOrderIndex)
                    })
                }
            })
            put("characters", JSONArray().apply {
                p.characters.forEach { c ->
                    put(JSONObject().apply {
                        put("novelTitle", c.novelTitle)
                        put("name", c.name)
                        put("notes", c.notes ?: "")
                        put("isFavorite", c.isFavorite)
                        put("photoPath", c.photoPath ?: "")
                        put("createdAt", c.createdAt)
                        put("photos", JSONArray().apply {
                            c.photos.forEach { ph ->
                                put(JSONObject().apply {
                                    put("photoPath", ph.photoPath)
                                    put("orderIndex", ph.orderIndex)
                                })
                            }
                        })
                    })
                }
            })
            put("collectionLinks", JSONArray().apply {
                p.collectionLinks.forEach { l ->
                    put(JSONObject().apply {
                        put("folderName", l.folderName)
                        put("novelTitle", l.novelTitle)
                    })
                }
            })
            put("novels", JSONArray().apply {
                p.novels.forEach { n ->
                    put(JSONObject().apply {
                        put("title", n.title)
                        put("author", n.author ?: "")
                        put("autoUpdate", n.autoUpdate)
                        put("lastReadAt", n.lastReadAt)
                        put("lastChapterFileName", n.lastChapterFileName)
                        put("lastChapterOrderIndex", n.lastChapterOrderIndex)
                    })
                }
            })
        }

        fun decode(root: JSONObject): PendingRestore = PendingRestore(
            bookmarks = root.optJSONArray("bookmarks").toObjects { arr, i ->
                val o = arr.getJSONObject(i)
                PendingBookmark(
                    novelTitle = o.optString("novelTitle"),
                    title = o.optString("title"),
                    note = o.optString("note").takeIf { it.isNotBlank() },
                    page = o.optInt("page"),
                    scrollPosition = o.optInt("scrollPosition"),
                    createdAt = o.optLong("createdAt"),
                    chapterFileName = o.optString("chapterFileName"),
                    chapterOrderIndex = o.optInt("chapterOrderIndex")
                )
            },
            characters = root.optJSONArray("characters").toObjects { arr, i ->
                val o = arr.getJSONObject(i)
                PendingCharacter(
                    novelTitle = o.optString("novelTitle"),
                    name = o.optString("name"),
                    notes = o.optString("notes").takeIf { it.isNotBlank() },
                    isFavorite = o.optBoolean("isFavorite"),
                    photoPath = o.optString("photoPath").takeIf { it.isNotBlank() },
                    createdAt = o.optLong("createdAt"),
                    photos = o.optJSONArray("photos").toObjects { pArr, j ->
                        val po = pArr.getJSONObject(j)
                        PendingPhoto(po.optString("photoPath"), po.optInt("orderIndex"))
                    }
                )
            },
            collectionLinks = root.optJSONArray("collectionLinks").toObjects { arr, i ->
                val o = arr.getJSONObject(i)
                PendingCollectionLink(o.optString("folderName"), o.optString("novelTitle"))
            },
            novels = root.optJSONArray("novels").toObjects { arr, i ->
                val o = arr.getJSONObject(i)
                PendingNovel(
                    title = o.optString("title"),
                    author = o.optString("author").takeIf { it.isNotBlank() },
                    autoUpdate = o.optBoolean("autoUpdate"),
                    lastReadAt = o.optLong("lastReadAt"),
                    lastChapterFileName = o.optString("lastChapterFileName"),
                    lastChapterOrderIndex = o.optInt("lastChapterOrderIndex")
                )
            }
        )

        private inline fun <T> JSONArray?.toObjects(create: (JSONArray, Int) -> T): List<T> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { i ->
                try {
                    create(this, i)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
