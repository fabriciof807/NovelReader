package com.novelreader.domain.usecase.importnovel

import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterInserter @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val failedChapterDao: FailedChapterDao
) {
    suspend fun ensureNovel(novelTitle: String): Pair<Long, List<ChapterEntity>> {
        var existingNovel = novelDao.getNovelByTitle(novelTitle)
        val novelId: Long
        val existingChapters: List<ChapterEntity>

        if (existingNovel != null) {
            novelId = existingNovel.id
            existingChapters = chapterDao.getChaptersByNovelSync(novelId)
        } else {
            val novelEntity = NovelEntity(
                title = novelTitle,
                sourceFolder = "",
                totalChapters = 0
            )
            novelId = novelDao.insert(novelEntity)
            existingChapters = emptyList()
        }

        return novelId to existingChapters
    }

    suspend fun insertEntries(
        novelId: Long,
        entries: List<ChapterEntry>
    ) {
        val inserts = mutableListOf<ChapterEntity>()
        for ((index, entry) in entries.withIndex()) {
            if (entry.existingId != null) {
                chapterDao.updateOrderIndex(entry.existingId, index)
            } else {
                inserts.add(
                    ChapterEntity(
                        novelId = novelId,
                        title = entry.chapterTitle,
                        fileName = entry.fileName,
                        orderIndex = index,
                        content = entry.content
                    )
                )
            }
        }

        if (inserts.isNotEmpty()) {
            chapterDao.insertAll(inserts)
        }
        novelDao.updateChapterCount(novelId, entries.size)

        for (entry in entries) {
            failedChapterDao.deleteByNovelAndFileName(novelId, entry.fileName)
        }
    }
}
