package com.novelreader.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.ImportJobSpec
import com.novelreader.domain.usecase.WebImportUseCase
import com.novelreader.util.StringUtils
import com.novelreader.di.qualifiers.IoDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@HiltWorker
class ChapterUpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val webImportUseCase: WebImportUseCase,
    private val notificationHelper: UpdateNotificationHelper,
    private val importWorkScheduler: ImportWorkScheduler,
    @IoDispatcher private val io: CoroutineDispatcher
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(io) {
        try {
            val novels = novelDao.getAutoUpdateNovels()
            if (novels.isEmpty()) return@withContext Result.success()

            for (novel in novels) {
                val result = webImportUseCase.fetchChapterList(novel.sourceUrl)
                result.fold(
                    onSuccess = { fetchResult ->
                        val existingFileNames = chapterDao
                            .getChaptersByNovelSync(novel.id)
                            .map { it.fileName }
                            .toSet()

                        val newChapters = fetchResult.chapters.filter { link ->
                            fileNameFromLink(link) !in existingFileNames
                        }

                        if (newChapters.isNotEmpty()) {
                            notificationHelper.postNewChaptersNotification(
                                novelId = novel.id,
                                novelTitle = novel.title,
                                newChapterCount = newChapters.size
                            )

                            val specs = ImportJobSpec.create(
                                novelTitle = fetchResult.novelTitle ?: novel.title,
                                links = newChapters,
                                coverUrl = fetchResult.coverUrl,
                                sourceUrl = novel.sourceUrl
                            )
                            for (spec in specs) {
                                importWorkScheduler.schedule(spec)
                            }
                        }

                        novelDao.updateLastChecked(novel.id, System.currentTimeMillis())
                    },
                    onFailure = { }
                )
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun fileNameFromLink(link: ChapterLink): String {
        return StringUtils.fileNameFromUrl(link.url, "chapter_${link.chapterNumber}")
    }

    companion object {
        const val TAG_UPDATE_CHECK = "chapter_update_check"
        const val UNIQUE_PERIODIC = "chapter_update_periodic"
    }
}
