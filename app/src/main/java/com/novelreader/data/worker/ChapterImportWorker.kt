package com.novelreader.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.domain.usecase.BackgroundImportError
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.ChapterOrderNormalizer
import com.novelreader.domain.usecase.ImportJobSpec
import com.novelreader.domain.usecase.WebImportUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltWorker
class ChapterImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val webImportUseCase: WebImportUseCase,
    private val notificationHelper: ImportNotificationHelper,
    private val workCompletionObserver: WorkCompletionObserver,
    private val chapterOrderNormalizer: ChapterOrderNormalizer,
    private val specFileStore: SpecFileStore,
    private val novelDao: NovelDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val spec = readSpec()
        if (spec == null) {
            Log.w("ImportRetry", "doWork specRead=fail → failure")
            return@withContext Result.failure()
        }
        Log.w("ImportRetry", "doWork id=${spec.id} title=${spec.novelTitle} links=${spec.links.size} split=${spec.splitIndex+1}/${spec.splitCount}")
        val total = spec.links.size
        setForegroundAsync(notificationHelper.createForegroundInfo(spec, 0, total))

        val errors = mutableListOf<BackgroundImportError>()
        val orderIndexOffset = spec.splitIndex * ImportJobSpec.BATCH_SIZE
        val result = webImportUseCase.importChapters(
            novelTitle = spec.novelTitle,
            links = spec.links.mapIndexed { i, url ->
                ChapterLink(
                    title = "",
                    url = url,
                    chapterNumber = spec.chapterNumbers.getOrElse(i) { Int.MAX_VALUE }
                )
            },
            coverUrl = spec.coverUrl,
            filesDir = applicationContext.filesDir,
            orderIndexOffset = orderIndexOffset,
            sourceUrl = spec.sourceUrl,
            domain = spec.domain,
            targetNovelId = spec.targetNovelId,
            onProgress = { processed, _ ->
                val chapterNum = spec.chapterNumbers.getOrElse(processed.coerceAtMost(spec.chapterNumbers.lastIndex)) { 0 }
                setProgressAsync(workDataOf(
                    KEY_PROGRESS to processed,
                    KEY_TOTAL to total,
                    KEY_CURRENT_CHAPTER to chapterNum
                ))
                setForegroundAsync(notificationHelper.createForegroundInfo(spec, processed, total))
            },
            onError = { url, msg ->
                errors.add(BackgroundImportError(url, msg))
            }
        )

        workCompletionObserver.onJobCompleted(spec.id, result.isSuccess, errors.size)
        specFileStore.delete(spec.id)

        if (result.isSuccess) {
            result.getOrNull()?.let { novelId ->
                chapterOrderNormalizer.normalize(novelId)
                if (spec.splitIndex == spec.splitCount - 1) {
                    spec.isFavorite?.let { novelDao.updateFavorite(novelId, it) }
                }
            }
            val importedCount = total - errors.size
            notificationHelper.postCompletionNotification(spec, importedCount, total, errors.size)
            Result.success()
        } else {
            notificationHelper.postFailureNotification(spec)
            val errorType = if (errors.any { it.message.contains("timeout", ignoreCase = true) || it.message.contains("network", ignoreCase = true) || it.message.contains("connect", ignoreCase = true) })
                "network" else "parse"
            val outputData = workDataOf(
                KEY_ERROR_TYPE to errorType,
                KEY_ERROR_MSG to (errors.firstOrNull()?.message ?: "Unknown error")
            )
            Result.failure(outputData)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val spec = readSpec() ?: throw IllegalStateException("Missing job spec")
        return notificationHelper.createForegroundInfo(spec, 0, spec.links.size)
    }

    internal fun readSpec(): ImportJobSpec? {
        return SpecReader.readFromData(inputData, specFileStore)
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_TITLE = "title"
        const val KEY_COVER = "cover"
        const val KEY_ENQUEUED_AT = "enqueued_at"
        const val KEY_SPLIT_COUNT = "split_count"
        const val KEY_SPLIT_INDEX = "split_index"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
        const val KEY_CURRENT_CHAPTER = "current_chapter"
        const val KEY_ERROR_TYPE = "error_type"
        const val KEY_ERROR_MSG = "error_message"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_DOMAIN = "import_domain"
        const val KEY_TARGET_NOVEL_ID = "import_target_novel_id"
        const val KEY_IS_FAVORITE_PRESENT = "import_is_favorite_present"
        const val KEY_IS_FAVORITE = "import_is_favorite"
        const val UNIQUE_ACTIVE = "chapter_import_active"
        const val TAG_IMPORT = "chapter_import"
    }
}
