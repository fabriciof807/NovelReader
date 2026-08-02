package com.novelreader.data.worker

import androidx.work.WorkManager
import com.novelreader.data.local.preferences.ImportPreferences
import com.novelreader.domain.usecase.ImportJobSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class WorkCompletionObserverTest {
    private val workManager: WorkManager = mockk(relaxed = true)
    private val importPrefs: ImportPreferences = mockk(relaxed = true)
    private val specFileStore: SpecFileStore = mockk(relaxed = true)

    @Test
    fun `schedule next waits for the shared scheduling lock`() = runTest {
        val next = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Next",
            links = listOf("https://example.com/1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            enqueuedAt = 1L
        )
        every { workManager.getWorkInfosByTagFlow(ChapterImportWorker.TAG_IMPORT) } returns flowOf(emptyList())
        coEvery { importPrefs.dequeueJob() } returns next
        val observer = WorkCompletionObserver(workManager, importPrefs, specFileStore)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val lockHolder = launch {
            observer.withSchedulingLock {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()

        val scheduling = launch { observer.scheduleNextIfIdle() }
        runCurrent()
        coVerify(exactly = 0) { importPrefs.dequeueJob() }

        release.complete(Unit)
        lockHolder.join()
        scheduling.join()
        coVerify(exactly = 1) { importPrefs.dequeueJob() }
    }
}
