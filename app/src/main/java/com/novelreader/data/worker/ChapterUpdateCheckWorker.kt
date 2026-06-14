package com.novelreader.data.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import com.novelreader.di.qualifiers.IoDispatcher
import com.novelreader.domain.usecase.WebImportUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@HiltWorker
class ChapterUpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val webImportUseCase: WebImportUseCase,
    private val notificationHelper: UpdateNotificationHelper,
    @IoDispatcher private val io: CoroutineDispatcher
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(io) {
        try {
            val novels = novelRepository.getAutoUpdateNovels()
            if (novels.isEmpty()) return@withContext Result.success()

            for (novel in novels) {
                val result = webImportUseCase.fetchChapterList(novel.sourceUrl)
                result.fold(
                    onSuccess = { fetchResult ->
                        val existingFileNames = chapterRepository
                            .getChaptersByNovelSync(novel.id)
                            .map { it.fileName }
                            .toSet()

                        val newChapters = fetchResult.chapters.filter { link ->
                            fileNameFromUrl(link.url) !in existingFileNames
                        }

                        if (newChapters.isNotEmpty()) {
                            notificationHelper.postNewChaptersNotification(
                                novelId = novel.id,
                                novelTitle = novel.title,
                                newChapterCount = newChapters.size
                            )
                        }

                        novelRepository.updateLastChecked(novel.id, System.currentTimeMillis())
                    },
                    onFailure = { }
                )
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun fileNameFromUrl(url: String): String {
        val segments = Uri.parse(url).pathSegments
        val last = segments.lastOrNull() ?: return "chapter"
        return last.removeSuffix(".html").removeSuffix(".htm").removeSuffix(".php")
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_")
            .replace(Regex("\\."), "_")
            .trim('_', ' ')
            .take(200)
            .ifBlank { "chapter" }
    }

    companion object {
        const val TAG_UPDATE_CHECK = "chapter_update_check"
        const val UNIQUE_PERIODIC = "chapter_update_periodic"
    }
}