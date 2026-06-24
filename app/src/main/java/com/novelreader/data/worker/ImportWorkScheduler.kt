package com.novelreader.data.worker

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.novelreader.data.local.preferences.ImportPreferences
import com.novelreader.domain.usecase.ImportJobSpec
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val importPrefs: ImportPreferences,
    private val completionObserver: WorkCompletionObserver,
    private val specFileStore: SpecFileStore
) {
    suspend fun schedule(spec: ImportJobSpec): UUID {
        importPrefs.enqueueJob(spec)
        completionObserver.tryScheduleNext()
        return spec.id
    }

    suspend fun cancel(id: UUID) {
        importPrefs.removeJob(id)
        specFileStore.delete(id)
        workManager.cancelWorkById(id)
    }

    suspend fun cancelAll() {
        importPrefs.clearQueue()
        specFileStore.deleteAll()
        workManager.cancelAllWorkByTag(ChapterImportWorker.TAG_IMPORT)
    }
}

object ImportWorkRequestFactory {
    fun build(spec: ImportJobSpec) = OneTimeWorkRequestBuilder<ChapterImportWorker>()
        .setInputData(
            workDataOf(
                ChapterImportWorker.KEY_JOB_ID to spec.id.toString(),
                ChapterImportWorker.KEY_TITLE to spec.novelTitle,
                ChapterImportWorker.KEY_COVER to (spec.coverUrl ?: ""),
                ChapterImportWorker.KEY_ENQUEUED_AT to spec.enqueuedAt,
                ChapterImportWorker.KEY_SPLIT_COUNT to spec.splitCount,
                ChapterImportWorker.KEY_SPLIT_INDEX to spec.splitIndex,
                ChapterImportWorker.KEY_SOURCE_URL to spec.sourceUrl
            )
        )
        .addTag(ChapterImportWorker.TAG_IMPORT)
        .addTag("job:${spec.id}")
        .build()
}
