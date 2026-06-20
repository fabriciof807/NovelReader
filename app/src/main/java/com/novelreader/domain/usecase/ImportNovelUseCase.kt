package com.novelreader.domain.usecase

import android.content.Context
import android.net.Uri
import com.novelreader.R
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
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun importFromFiles(
        uris: List<Uri>,
        context: Context,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        onFileError: ((fileName: String, error: String) -> Unit)? = null
    ): Map<String, Long> = withContext(io) {
        val results = mutableMapOf<String, Long>()

        val groups = novelGrouper.parseAndGroup(uris, context, onFileError)

        for ((novelTitle, parsedFiles) in groups) {
            val (novelId, existingChapters) = chapterInserter.ensureNovel(novelTitle)
            val existingFileNames = existingChapters.map { it.fileName }.toSet()

            val sorted = chapterSorter.buildSortedEntries(
                novelTitle, parsedFiles, existingChapters, existingFileNames
            )

            chapterInserter.insertEntries(novelId, sorted)
            results[novelTitle] = novelId
        }

        results
    }
}
