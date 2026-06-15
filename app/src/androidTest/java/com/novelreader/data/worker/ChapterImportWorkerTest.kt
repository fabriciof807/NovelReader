package com.novelreader.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ChapterImportWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun validInputData(
        jobId: UUID = UUID.randomUUID(),
        title: String = "Test Novel",
        links: List<String> = listOf("https://example.com/c1", "https://example.com/c2"),
        nums: IntArray = intArrayOf(1, 2)
    ) = workDataOf(
        ChapterImportWorker.KEY_JOB_ID to jobId.toString(),
        ChapterImportWorker.KEY_TITLE to title,
        ChapterImportWorker.KEY_LINKS to links.toTypedArray(),
        ChapterImportWorker.KEY_NUMS to nums,
        ChapterImportWorker.KEY_COVER to "",
        ChapterImportWorker.KEY_ENQUEUED_AT to System.currentTimeMillis()
    )

    private fun buildWorker(inputData: androidx.work.Data) =
        TestListenableWorkerBuilder<SimpleTestWorker>(context, inputData)
            .setWorkerFactory(SimpleWorkerFactory())
            .build()

    @Test
    fun doWork_returnsFailure_whenJobIdMissing() = runBlocking {
        val input = workDataOf(
            ChapterImportWorker.KEY_TITLE to "Title",
            ChapterImportWorker.KEY_LINKS to arrayOf("u1"),
            ChapterImportWorker.KEY_NUMS to intArrayOf(1)
        )
        val worker = buildWorker(input)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test
    fun doWork_returnsFailure_whenTitleMissing() = runBlocking {
        val input = workDataOf(
            ChapterImportWorker.KEY_JOB_ID to UUID.randomUUID().toString(),
            ChapterImportWorker.KEY_LINKS to arrayOf("u1"),
            ChapterImportWorker.KEY_NUMS to intArrayOf(1)
        )
        val worker = buildWorker(input)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test
    fun doWork_returnsSuccess_whenValidInput() = runBlocking {
        val worker = buildWorker(validInputData())
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
}

private class SimpleWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = SimpleTestWorker(appContext, workerParameters)
}

private class SimpleTestWorker(
    appContext: Context,
    params: WorkerParameters
) : androidx.work.CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val idStr = inputData.getString(ChapterImportWorker.KEY_JOB_ID) ?: return Result.failure()
        val title = inputData.getString(ChapterImportWorker.KEY_TITLE) ?: return Result.failure()
        val links = inputData.getStringArray(ChapterImportWorker.KEY_LINKS)?.toList() ?: return Result.failure()
        if (links.isEmpty()) return Result.failure()

        return Result.success()
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        return androidx.work.ForegroundInfo(1, android.app.Notification())
    }
}
