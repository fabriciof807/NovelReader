package com.novelreader.domain.usecase

import android.content.Context
import android.net.Uri
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.entity.FailedChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterErrorType
import com.novelreader.di.qualifiers.IoDispatcher
import com.novelreader.domain.usecase.importnovel.ChapterInserter
import com.novelreader.domain.usecase.importnovel.ChapterSorter
import com.novelreader.domain.usecase.importnovel.FileCharsetDetector
import com.novelreader.domain.usecase.importnovel.NovelGrouper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportNovelUseCase @Inject constructor(
    private val novelGrouper: NovelGrouper,
    private val chapterSorter: ChapterSorter,
    private val chapterInserter: ChapterInserter,
    private val fileCharsetDetector: FileCharsetDetector,
    private val failedChapterDao: FailedChapterDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun importFromFiles(
        uris: List<Uri>,
        context: Context,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        onFileError: ((fileName: String, error: String) -> Unit)? = null
    ): Map<String, Long> = withContext(io) {
        val results = mutableMapOf<String, Long>()
        val fileFailures = mutableListOf<Pair<String, String>>()

        val groups = novelGrouper.parseAndGroup(uris, context) { fileName, error ->
            fileFailures.add(fileName to error)
            onFileError?.invoke(fileName, error)
        }

        for ((novelTitle, parsedFiles) in groups) {
            val (novelId, existingChapters) = chapterInserter.ensureNovel(novelTitle)
            val existingFileNames = existingChapters.map { it.fileName }.toSet()

            val sorted = chapterSorter.buildSortedEntries(
                novelTitle, parsedFiles, existingChapters, existingFileNames
            )

            chapterInserter.insertEntries(novelId, sorted)
            results[novelTitle] = novelId
        }

        if (fileFailures.isNotEmpty() && results.isNotEmpty()) {
            val firstNovelId = results.values.first()
            for ((fileName, errorMessage) in fileFailures) {
                failedChapterDao.deleteByNovelAndFileName(firstNovelId, fileName)
                failedChapterDao.insert(
                    FailedChapterEntity(
                        novelId = firstNovelId,
                        title = fileName,
                        fileName = fileName,
                        url = null,
                        sourceType = "LOCAL",
                        errorType = FailedChapterErrorType.IO,
                        errorMessage = errorMessage
                    )
                )
            }
        }

        results
    }
}
