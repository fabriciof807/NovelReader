package com.novelreader.domain.usecase.webimport

import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.domain.usecase.ChapterOrderNormalizer
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
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val chapterOrderNormalizer: ChapterOrderNormalizer
) {
    suspend fun ensureNovel(
        novelTitle: String,
        sourceUrl: String
    ): Pair<Long, MutableSet<String>> {
        var existingNovel = novelDao.getNovelByTitle(novelTitle)
        val novelId: Long
        val existingFileNames: MutableSet<String>

        if (existingNovel != null) {
            novelId = existingNovel.id
            existingFileNames = chapterDao.getChaptersByNovelSync(novelId)
                .map { it.fileName }.toMutableSet()
        } else {
            val novelEntity = NovelEntity(
                title = novelTitle,
                sourceFolder = "",
                totalChapters = 0
            )
            novelId = novelDao.insert(novelEntity)
            existingFileNames = mutableSetOf()
        }

        if (sourceUrl.isNotBlank()) {
            novelDao.updateSourceUrl(novelId, sourceUrl)
        }

        return novelId to existingFileNames
    }

    suspend fun insertChapters(
        novelId: Long,
        chapters: List<ImportedChapter>
    ) {
        if (chapters.isEmpty()) return

        chapterDao.insertAll(chapters.map { chapter ->
            ChapterEntity(
                novelId = novelId,
                title = chapter.title,
                fileName = chapter.fileName,
                orderIndex = chapter.orderIndex,
                content = chapter.content
            )
        })
        chapterOrderNormalizer.normalize(novelId)
        val totalChapters = chapterDao.getChaptersByNovelSync(novelId).size
        novelDao.updateChapterCount(novelId, totalChapters)
    }

    fun fileNameFromUrl(url: String, chapterNumber: Int): String {
        return StringUtils.fileNameFromUrl(url, "chapter_$chapterNumber")
    }
}
