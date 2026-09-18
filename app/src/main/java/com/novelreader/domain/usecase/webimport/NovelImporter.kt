package com.novelreader.domain.usecase.webimport

import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.dao.NovelSourceDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.db.entity.NovelSourceEntity
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
    private val chapterOrderNormalizer: ChapterOrderNormalizer,
    private val novelSourceDao: NovelSourceDao
) {
    suspend fun ensureNovel(
        novelTitle: String,
        sourceUrl: String,
        domain: String = "",
        targetNovelId: Long? = null
    ): Pair<Long, MutableSet<String>> {
        val existingNovel: NovelEntity? = if (targetNovelId != null) {
            novelDao.getNovelById(targetNovelId)
        } else {
            novelDao.getNovelByTitleIgnoreCase(novelTitle)
        }
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
            val existing = novelSourceDao.findByNovelAndUrl(novelId, sourceUrl)
            if (existing == null) {
                val isPrimary = novelSourceDao.getByNovel(novelId).isEmpty()
                novelSourceDao.insert(
                    NovelSourceEntity(
                        novelId = novelId,
                        sourceUrl = sourceUrl,
                        domain = domain,
                        isPrimary = isPrimary,
                        addedAt = System.currentTimeMillis()
                    )
                )
                if (isPrimary && (novelDao.getNovelById(novelId)?.sourceUrl ?: "").isEmpty()) {
                    novelDao.updateSourceUrl(novelId, sourceUrl)
                }
            } else if (existing.domain != domain) {
                novelSourceDao.updateUrl(existing.id, sourceUrl, domain)
            }
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
                content = chapter.content,
                isNew = true
            )
        })
        novelDao.updateChapterCount(novelId, chapterDao.countByNovel(novelId))
    }

    suspend fun finalizeChapterOrder(novelId: Long) {
        chapterOrderNormalizer.normalize(novelId)
        novelDao.updateChapterCount(novelId, chapterDao.countByNovel(novelId))
    }

    fun fileNameFromUrl(url: String, chapterNumber: Int): String {
        return StringUtils.fileNameFromUrl(url, "chapter_$chapterNumber")
    }
}
