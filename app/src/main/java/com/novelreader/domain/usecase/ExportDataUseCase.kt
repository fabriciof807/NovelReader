package com.novelreader.domain.usecase

import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.di.qualifiers.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportDataUseCase @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val characterDao: CharacterDao,
    private val characterPhotoDao: CharacterPhotoDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun execute(): String = withContext(ioDispatcher) {
        val root = JSONObject()

        root.put("version", 1)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

        val novels = novelDao.getAllNovels().first()
        root.put("novels", exportNovels(novels))

        root.put("bookmarks", exportBookmarks())

        val novelMap = novels.associateBy { it.id }
        root.put("characters", exportCharacters(novelMap))

        root.toString(2)
    }

    private fun exportNovels(novels: List<NovelEntity>): JSONArray {
        val arr = JSONArray()
        for (novel in novels) {
            arr.put(JSONObject().apply {
                put("title", novel.title)
                put("sourceUrl", novel.sourceUrl)
            })
        }
        return arr
    }

    private suspend fun exportBookmarks(): JSONArray {
        val arr = JSONArray()
        val bookmarks = bookmarkDao.getAllSync()
        for (bm in bookmarks) {
            val chapter = chapterDao.getChapterById(bm.chapterId)
            arr.put(JSONObject().apply {
                put("title", bm.title)
                put("note", bm.note ?: "")
                put("page", bm.page)
                put("scrollPosition", bm.scrollPosition)
                put("createdAt", bm.createdAt)
                put("chapter", JSONObject().apply {
                    put("title", chapter?.title ?: "")
                    put("fileName", chapter?.fileName ?: "")
                    put("orderIndex", chapter?.orderIndex ?: 0)
                })
            })
        }
        return arr
    }

    private suspend fun exportCharacters(novelMap: Map<Long, NovelEntity>): JSONArray {
        val arr = JSONArray()
        val characters = characterDao.getAllCharactersSync()
        val characterIds = characters.map { it.id }
        val photosMap = characterPhotoDao.getByCharacterIds(characterIds)
            .groupBy { it.characterId }
        for (char in characters) {
            arr.put(JSONObject().apply {
                put("name", char.name)
                put("notes", char.notes ?: "")
                put("isFavorite", char.isFavorite)
                put("photoPath", char.photoPath ?: "")
                put("createdAt", char.createdAt)
                put("novelTitle", novelMap[char.novelId]?.title ?: "")
                put("photos", JSONArray().apply {
                    val photos = photosMap[char.id]
                    if (photos != null) {
                        for (photo in photos) {
                            put(JSONObject().apply {
                                put("photoPath", photo.photoPath)
                                put("orderIndex", photo.orderIndex)
                            })
                        }
                    }
                })
            })
        }
        return arr
    }
}
