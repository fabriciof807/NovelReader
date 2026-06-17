package com.novelreader.data.repository

import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelReadCount
import com.novelreader.data.parser.ChapterNumberExtractor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterRepository @Inject constructor(
    private val chapterDao: ChapterDao
) {
    suspend fun getChapterById(id: Long): ChapterEntity? =
        chapterDao.getChapterById(id)

    suspend fun getChaptersByNovelSync(novelId: Long): List<ChapterEntity> =
        chapterDao.getChaptersByNovelSync(novelId)

    suspend fun getChaptersByIds(ids: List<Long>): List<ChapterEntity> =
        chapterDao.getChaptersByIds(ids)

    suspend fun markAsRead(id: Long, position: Int) =
        chapterDao.markAsRead(id, position)

    suspend fun insertAll(chapters: List<ChapterEntity>) =
        chapterDao.insertAll(chapters)

    suspend fun updateOrderIndex(id: Long, orderIndex: Int) =
        chapterDao.updateOrderIndex(id, orderIndex)

    suspend fun reNormalizeOrderIndices(novelId: Long) {
        val chapters = chapterDao.getChaptersByNovelSync(novelId)
        val sorted = chapters.sortedBy {
            ChapterNumberExtractor.extract(it.title, it.fileName)
        }
        sorted.forEachIndexed { index, chapter ->
            if (chapter.orderIndex != index) {
                chapterDao.updateOrderIndex(chapter.id, index)
            }
        }
    }

    suspend fun searchInNovel(novelId: Long, query: String): List<ChapterEntity> {
        val ftsQuery = query.trim()
            .replace(Regex("[\"()^+\\-~]"), " ")
            .replace(Regex("\\s+"), " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { "\"$it\"*" }
        if (ftsQuery.isBlank()) return emptyList()
        return chapterDao.searchInNovel(novelId, ftsQuery)
    }

    suspend fun getReadCountPerNovel(): List<NovelReadCount> =
        chapterDao.getReadCountPerNovel()
}
