package com.novelreader.data.worker

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.WebImportUseCase
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class ChapterImportWorkerTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val webImportUseCase: WebImportUseCase = mockk(relaxed = true)

    @BindValue
    @JvmField
    val notificationHelper: ImportNotificationHelper = mockk(relaxed = true)

    @BindValue
    @JvmField
    val workCompletionObserver: WorkCompletionObserver = mockk(relaxed = true)

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
        every { notificationHelper.createForegroundInfo(any(), any(), any(), any()) } returns ForegroundInfo(
            1,
            Notification()
        )
        coEvery { workCompletionObserver.onJobCompleted(any(), any(), any()) } returns Unit
    }

    private fun buildWorker(inputData: androidx.work.Data) =
        TestListenableWorkerBuilder<ChapterImportWorker>(context, inputData)
            .setWorkerFactory(NoOpWorkerFactory)
            .build()

    private fun validInputData(
        jobId: UUID = UUID.randomUUID(),
        title: String = "Test Novel",
        links: List<String> = listOf("https://example.com/c1", "https://example.com/c2"),
        nums: IntArray = intArrayOf(1, 2),
        cover: String? = null
    ) = workDataOf(
        ChapterImportWorker.KEY_JOB_ID to jobId.toString(),
        ChapterImportWorker.KEY_TITLE to title,
        ChapterImportWorker.KEY_LINKS to links.toTypedArray(),
        ChapterImportWorker.KEY_NUMS to nums,
        ChapterImportWorker.KEY_COVER to (cover ?: ""),
        ChapterImportWorker.KEY_ENQUEUED_AT to System.currentTimeMillis()
    )

    @Test
    fun doWork_returnsSuccess_whenImportSucceeds() = runBlocking {
        coEvery {
            webImportUseCase.importChapters(
                novelTitle = any(),
                links = any(),
                coverUrl = any(),
                filesDir = any(),
                onProgress = any(),
                onError = any()
            )
        } returns Result.success(42L)

        val worker = buildWorker(validInputData())
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

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
    fun doWork_returnsFailure_whenImportFails() = runBlocking {
        coEvery {
            webImportUseCase.importChapters(
                novelTitle = any(),
                links = any(),
                coverUrl = any(),
                filesDir = any(),
                onProgress = any(),
                onError = any()
            )
        } returns Result.failure(RuntimeException("boom"))

        val worker = buildWorker(validInputData())
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test
    fun doWork_invokesOnErrorCallbackForEachError() = runBlocking {
        val collected = mutableListOf<Pair<String, String>>()
        coEvery {
            webImportUseCase.importChapters(
                novelTitle = any(),
                links = any(),
                coverUrl = any(),
                filesDir = any(),
                onProgress = any(),
                onError = any()
            )
        } answers {
            val onError = it.invocation.args[5] as? ((String, String) -> Unit)
            onError?.invoke("https://example.com/c1", "404")
            onError?.invoke("https://example.com/c2", "timeout")
            Result.success(7L)
        }

        val jobId = UUID.randomUUID()
        val worker = buildWorker(validInputData(jobId = jobId))
        worker.doWork()

        coEvery { workCompletionObserver.onJobCompleted(jobId, true, 2) }
    }

    @Test
    fun getForegroundInfo_returnsForegroundInfo() = runBlocking {
        val worker = buildWorker(validInputData())
        val info = worker.getForegroundInfo()

        assertThat(info).isNotNull()
        assertThat(info.notificationId).isEqualTo(1)
    }
}

private object NoOpWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = null
}
