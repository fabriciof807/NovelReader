package com.novelreader.domain.usecase

import android.content.Context
import com.novelreader.R
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.FailedChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterErrorType
import com.novelreader.data.parser.ChapterNumberExtractor
import com.novelreader.di.qualifiers.IoDispatcher
import com.novelreader.domain.usecase.webimport.ChapterCrawler
import com.novelreader.domain.usecase.webimport.ChapterFetcher
import com.novelreader.domain.usecase.webimport.CoverDownloader
import com.novelreader.domain.usecase.webimport.ImportedChapter
import com.novelreader.domain.usecase.webimport.NovelImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebImportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterCrawler: ChapterCrawler,
    private val chapterFetcher: ChapterFetcher,
    private val coverDownloader: CoverDownloader,
    private val novelImporter: NovelImporter,
    private val failedChapterDao: FailedChapterDao,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    companion object {
        private const val CHAPTER_FETCH_PACING_MS = 2_000L
    }

    suspend fun fetchChapterList(homeUrl: String): Result<FetchResult> = withContext(io) {
        try {
            val crawl = chapterCrawler.crawlChapterList(homeUrl)

            if (crawl.links.isEmpty()) {
                return@withContext Result.failure(Exception(context.getString(R.string.web_import_no_chapters_found)))
            }
            Result.success(FetchResult(
                chapters = crawl.links,
                coverUrl = crawl.coverUrl,
                novelTitle = crawl.novelTitle
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importChapters(
        novelTitle: String,
        links: List<ChapterLink>,
        coverUrl: String? = null,
        filesDir: File? = null,
        orderIndexOffset: Int = 0,
        sourceUrl: String = "",
        domain: String = "",
        targetNovelId: Long? = null,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        onError: ((url: String, error: String) -> Unit)? = null
    ): Result<Long> = withContext(io) {
        try {
            val (novelId, existingFileNames) = novelImporter.ensureNovel(novelTitle, sourceUrl, domain, targetNovelId)

            val existingChapters = chapterDao.getChaptersByNovelSync(novelId)
            val existingNumbers: Set<Int> = existingChapters
                .filter { it.content.isNotBlank() }
                .mapNotNull { c -> ChapterNumberExtractor.extract(c.title, c.fileName).takeIf { it != Int.MAX_VALUE } }
                .toSet()

            if (coverUrl != null && filesDir != null &&
                (novelDao.getNovelById(novelId)?.coverPath.isNullOrEmpty())
            ) {
                coverDownloader.downloadCover(novelId, coverUrl, filesDir)
            }

            val sorted = links.sortedBy { it.chapterNumber }
            var successCount = 0
            val importedChapters = mutableListOf<ImportedChapter>()

            for ((index, link) in sorted.withIndex()) {
                if (index > 0) delay(CHAPTER_FETCH_PACING_MS)
                val fileName = novelImporter.fileNameFromUrl(link.url, link.chapterNumber)
                val chapterNumber = link.chapterNumber

                if (fileName in existingFileNames) {
                    successCount++
                    onProgress?.invoke(successCount, sorted.size)
                    continue
                }
                if (chapterNumber != Int.MAX_VALUE && chapterNumber in existingNumbers) {
                    successCount++
                    onProgress?.invoke(successCount, sorted.size)
                    continue
                }

                try {
                    val fetched = chapterFetcher.fetch(link.url, fileName, link.title)
                    existingFileNames.add(fileName)
                    importedChapters.add(
                        ImportedChapter(
                            title = fetched.title,
                            fileName = fetched.fileName,
                            orderIndex = orderIndexOffset + index,
                            content = fetched.content
                        )
                    )
                    successCount++
                } catch (e: Exception) {
                    val errorType = FailedChapterErrorType.classify(e)
                    val errorMessage = e.message ?: "Erro desconhecido"
                    onError?.invoke(link.url, errorMessage)
                    failedChapterDao.deleteByNovelAndFileName(novelId, fileName)
                    failedChapterDao.insert(
                        FailedChapterEntity(
                            novelId = novelId,
                            title = link.title.ifBlank { fileName },
                            fileName = fileName,
                            url = link.url,
                            sourceType = "WEB",
                            chapterNumber = chapterNumber,
                            errorType = errorType,
                            errorMessage = errorMessage
                        )
                    )
                }
                onProgress?.invoke(successCount, sorted.size)
            }

            if (importedChapters.isNotEmpty()) {
                novelImporter.insertChapters(novelId, importedChapters)
                val wasExisting = existingChapters.isNotEmpty() || targetNovelId != null
                if (wasExisting) {
                    novelDao.setHasUpdates(novelId, true)
                }
            }
            Result.success(novelId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
