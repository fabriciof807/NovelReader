package com.novelreader.domain.usecase

import android.content.Context
import com.novelreader.R
import com.novelreader.di.qualifiers.IoDispatcher
import com.novelreader.domain.usecase.webimport.ChapterCrawler
import com.novelreader.domain.usecase.webimport.ChapterFetcher
import com.novelreader.domain.usecase.webimport.CoverDownloader
import com.novelreader.domain.usecase.webimport.ImportedChapter
import com.novelreader.domain.usecase.webimport.NovelImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
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
    @IoDispatcher private val io: CoroutineDispatcher
) {
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
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        onError: ((url: String, error: String) -> Unit)? = null
    ): Result<Long> = withContext(io) {
        try {
            val (novelId, existingFileNames) = novelImporter.ensureNovel(novelTitle, sourceUrl)

            if (coverUrl != null && filesDir != null) {
                coverDownloader.downloadCover(novelId, coverUrl, filesDir)
            }

            val sorted = links.sortedBy { it.chapterNumber }
            var successCount = 0
            val importedChapters = mutableListOf<ImportedChapter>()

            for ((index, link) in sorted.withIndex()) {
                val fileName = novelImporter.fileNameFromUrl(link.url, link.chapterNumber)
                if (fileName in existingFileNames) {
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
                    onError?.invoke(link.url, e.message ?: "Erro desconhecido")
                }
                onProgress?.invoke(successCount, sorted.size)
            }

            novelImporter.insertChapters(novelId, importedChapters)
            Result.success(novelId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
