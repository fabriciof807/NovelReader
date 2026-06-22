package com.novelreader.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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

@HiltWorker
class ChapterImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val webImportUseCase: WebImportUseCase,
    private val notificationHelper: ImportNotificationHelper,
    private val workCompletionObserver: WorkCompletionObserver,
    private val chapterOrderNormalizer: ChapterOrderNormalizer
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val spec = readSpec() ?: return@withContext Result.failure()
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

        if (result.isSuccess) {
            result.getOrNull()?.let { novelId ->
                chapterOrderNormalizer.normalize(novelId)
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

    private fun readSpec(): ImportJobSpec? {
        val data = inputData
        val idStr = data.getString(KEY_JOB_ID) ?: return null
        val id = try { UUID.fromString(idStr) } catch (_: Exception) { return null }
        val title = data.getString(KEY_TITLE) ?: return null
        val links = data.getStringArray(KEY_LINKS)?.toList() ?: emptyList()
        val numbers = data.getIntArray(KEY_NUMS)?.toList() ?: emptyList()
        val cover = data.getString(KEY_COVER)?.takeIf { it.isNotBlank() }
        val enqueuedAt = data.getLong(KEY_ENQUEUED_AT, 0L).let { if (it == 0L) System.currentTimeMillis() else it }
        val splitCount = data.getInt(KEY_SPLIT_COUNT, 1)
        val splitIndex = data.getInt(KEY_SPLIT_INDEX, 0)
        val sourceUrl = data.getString(KEY_SOURCE_URL) ?: ""
        return ImportJobSpec(
            id = id,
            novelTitle = title,
            links = links,
            chapterNumbers = numbers,
            coverUrl = cover,
            enqueuedAt = enqueuedAt,
            splitCount = splitCount,
            splitIndex = splitIndex,
            sourceUrl = sourceUrl
        )
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_TITLE = "title"
        const val KEY_LINKS = "links"
        const val KEY_NUMS = "nums"
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
        const val UNIQUE_ACTIVE = "chapter_import_active"
        const val TAG_IMPORT = "chapter_import"
    }
}
