package com.novelreader.domain.usecase.importnovel

import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterInserter @Inject constructor(
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository
) {
    suspend fun ensureNovel(novelTitle: String): Pair<Long, List<ChapterEntity>> {
        var existingNovel = novelRepository.getNovelByTitle(novelTitle)
        val novelId: Long
        val existingChapters: List<ChapterEntity>

        if (existingNovel != null) {
            novelId = existingNovel.id
            existingChapters = chapterRepository.getChaptersByNovelSync(novelId)
        } else {
            val novelEntity = NovelEntity(
                title = novelTitle,
                sourceFolder = "",
                totalChapters = 0
            )
            novelId = novelRepository.insert(novelEntity)
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
                chapterRepository.updateOrderIndex(entry.existingId, index)
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
            chapterRepository.insertAll(inserts)
        }
        novelRepository.updateChapterCount(novelId, entries.size)
    }
}
