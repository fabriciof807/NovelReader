package com.novelreader.domain.usecase

import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.parser.ChapterNumberExtractor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterOrderNormalizer @Inject constructor(
    private val chapterDao: ChapterDao
) {
    suspend fun normalize(novelId: Long) {
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
}
