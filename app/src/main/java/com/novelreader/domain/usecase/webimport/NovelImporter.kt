package com.novelreader.domain.usecase.webimport

import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import com.novelreader.util.StringUtils
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedChapter(
    val title: String,
    val fileName: String,
    val orderIndex: Int,
    val content: String
)

@Singleton
class NovelImporter @Inject constructor(
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository
) {
    suspend fun ensureNovel(
        novelTitle: String,
        sourceUrl: String
    ): Pair<Long, MutableSet<String>> {
        var existingNovel = novelRepository.getNovelByTitle(novelTitle)
        val novelId: Long
        val existingFileNames: MutableSet<String>

        if (existingNovel != null) {
            novelId = existingNovel.id
            existingFileNames = chapterRepository.getChaptersByNovelSync(novelId)
                .map { it.fileName }.toMutableSet()
        } else {
            val novelEntity = NovelEntity(
                title = novelTitle,
                sourceFolder = "",
                totalChapters = 0
            )
            novelId = novelRepository.insert(novelEntity)
            existingFileNames = mutableSetOf()
        }

        if (sourceUrl.isNotBlank()) {
            novelRepository.updateSourceUrl(novelId, sourceUrl)
        }

        return novelId to existingFileNames
    }

    suspend fun insertChapters(
        novelId: Long,
        chapters: List<ImportedChapter>
    ) {
        if (chapters.isEmpty()) return

        chapterRepository.insertAll(chapters.map { chapter ->
            ChapterEntity(
                novelId = novelId,
                title = chapter.title,
                fileName = chapter.fileName,
                orderIndex = chapter.orderIndex,
                content = chapter.content
            )
        })
        chapterRepository.reNormalizeOrderIndices(novelId)
        val totalChapters = chapterRepository.getChaptersByNovelSync(novelId).size
        novelRepository.updateChapterCount(novelId, totalChapters)
    }

    fun fileNameFromUrl(url: String, chapterNumber: Int): String {
        return StringUtils.fileNameFromUrl(url, "chapter_$chapterNumber")
    }
}
