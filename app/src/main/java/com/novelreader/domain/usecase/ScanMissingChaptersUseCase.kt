package com.novelreader.domain.usecase

import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.FailedChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterErrorType
import com.novelreader.data.parser.ChapterNumberExtractor
import com.novelreader.domain.usecase.webimport.NovelImporter
import javax.inject.Inject
import javax.inject.Singleton

data class ScanResult(val missing: Int, val empty: Int) {
    val total: Int get() = missing + empty
}

@Singleton
class ScanMissingChaptersUseCase @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val failedChapterDao: FailedChapterDao,
    private val webImportUseCase: WebImportUseCase,
    private val novelImporter: NovelImporter
) {
    suspend fun scanWeb(novelId: Long): Result<ScanResult> {
        val novel = novelDao.getNovelById(novelId)
            ?: return Result.failure(IllegalArgumentException("Novel não encontrada"))
        val sourceUrl = novel.sourceUrl
        if (sourceUrl.isBlank()) {
            return Result.failure(IllegalStateException("Novel sem sourceUrl"))
        }

        val crawl = webImportUseCase.fetchChapterList(sourceUrl).getOrElse {
            return Result.failure(it)
        }
        return Result.success(
            scanForMissing(novelId, crawl.chapters, "WEB", sourceUrl)
        )
    }

    suspend fun scanLocal(novelId: Long, from: Int, to: Int): Result<ScanResult> {
        if (from < 1 || to < from) {
            return Result.failure(IllegalArgumentException("Range inválido"))
        }
        val expected = (from..to).map { n ->
            ChapterLink(
                title = "Chapter $n",
                url = "",
                chapterNumber = n
            )
        }
        return Result.success(scanForMissing(novelId, expected, "LOCAL", null))
    }

    private suspend fun scanForMissing(
        novelId: Long,
        expected: List<ChapterLink>,
        sourceType: String,
        sourceUrl: String?
    ): ScanResult {
        val existing = chapterDao.getChaptersByNovelSync(novelId)
        val existingFileNames = existing.map { it.fileName }.toSet()
        val existingNumbers = existing
            .filter { it.content.isNotBlank() }
            .mapNotNull { c -> ChapterNumberExtractor.extract(c.title, c.fileName).takeIf { it != Int.MAX_VALUE } }
            .toSet()

        var missing = 0
        for (link in expected) {
            val fileName = if (sourceType == "WEB" && link.url.isNotBlank()) {
                novelImporter.fileNameFromUrl(link.url, link.chapterNumber)
            } else {
                "chapter_${link.chapterNumber}"
            }
            val chapterNum = link.chapterNumber
            val presentByFile = fileName in existingFileNames
            val presentByNumber = chapterNum != Int.MAX_VALUE && chapterNum in existingNumbers
            if (presentByFile || presentByNumber) continue
            failedChapterDao.deleteByNovelAndFileName(novelId, fileName)
            failedChapterDao.insert(
                FailedChapterEntity(
                    novelId = novelId,
                    title = link.title.ifBlank { "Chapter ${link.chapterNumber}" },
                    fileName = fileName,
                    url = link.url.takeIf { it.isNotBlank() },
                    sourceType = sourceType,
                    chapterNumber = chapterNum,
                    errorType = FailedChapterErrorType.MISSING_NUMBER,
                    errorMessage = "Capítulo ${link.chapterNumber} não encontrado no banco"
                )
            )
            missing++
        }

        var empty = 0
        for (chapter in existing) {
            val isEmpty = chapter.content.isBlank() || chapter.content.length < 200
            if (isEmpty) {
                val chapterNum = ChapterNumberExtractor.extract(chapter.title, chapter.fileName)
                failedChapterDao.deleteByNovelAndFileName(novelId, chapter.fileName)
                failedChapterDao.insert(
                    FailedChapterEntity(
                        novelId = novelId,
                        title = chapter.title,
                        fileName = chapter.fileName,
                        url = sourceUrl,
                        sourceType = sourceType,
                        chapterNumber = chapterNum,
                        errorType = FailedChapterErrorType.EMPTY_CONTENT,
                        errorMessage = "Capítulo sem conteúdo"
                    )
                )
                empty++
            }
        }

        return ScanResult(missing = missing, empty = empty)
    }
}
