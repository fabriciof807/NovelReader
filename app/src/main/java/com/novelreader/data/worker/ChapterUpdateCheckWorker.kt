package com.novelreader.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.dao.NovelSourceDao
import com.novelreader.data.parser.ChapterNumberExtractor
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.ImportJobSpec
import com.novelreader.domain.usecase.WebImportUseCase
import com.novelreader.domain.usecase.webimport.CloudflareChallengeRequiredException
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
    private val novelSourceDao: NovelSourceDao,
    private val webImportUseCase: WebImportUseCase,
    private val notificationHelper: UpdateNotificationHelper,
    private val importWorkScheduler: ImportWorkScheduler,
    @IoDispatcher private val io: CoroutineDispatcher
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(io) {
        try {
            val sources = novelSourceDao.getAllForAutoUpdate()
            if (sources.isEmpty()) return@withContext Result.success()

            for (source in sources) {
                val novel = novelDao.getNovelById(source.novelId) ?: continue
                if (!novel.autoUpdate || !source.autoUpdate) continue
                val result = webImportUseCase.fetchChapterList(source.sourceUrl)
                result.fold(
                    onSuccess = { fetchResult ->
                        val existingChapters = chapterDao.getChaptersByNovelSync(novel.id)
                        val existingFileNames = existingChapters.map { it.fileName }.toSet()
                        val existingNumbers = existingChapters
                            .filter { it.content.isNotBlank() }
                            .mapNotNull { c -> ChapterNumberExtractor.extract(c.title, c.fileName).takeIf { it != Int.MAX_VALUE } }
                            .toSet()

                        val newChapters = fetchResult.chapters.filter { link ->
                            val fn = fileNameFromLink(link)
                            val n = link.chapterNumber
                            fn !in existingFileNames && !(n != Int.MAX_VALUE && n in existingNumbers)
                        }

                        if (newChapters.isNotEmpty()) {
                            novelDao.setHasUpdates(novel.id, true)
                            notificationHelper.postNewChaptersNotification(
                                novelId = novel.id,
                                novelTitle = novel.title,
                                newChapterCount = newChapters.size
                            )

                            val specs = ImportJobSpec.create(
                                novelTitle = fetchResult.novelTitle ?: novel.title,
                                links = newChapters,
                                coverUrl = null,
                                sourceUrl = source.sourceUrl,
                                domain = source.domain,
                                targetNovelId = novel.id
                            )
                            for (spec in specs) {
                                importWorkScheduler.schedule(spec)
                            }
                        }

                        novelSourceDao.updateLastChecked(source.id, System.currentTimeMillis())
                    },
                    onFailure = { e ->
                        if (e is CloudflareChallengeRequiredException) {
                            notificationHelper.postCloudflareReverifyNotification(
                                novelId = novel.id,
                                novelTitle = novel.title
                            )
                        }
                    }
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
