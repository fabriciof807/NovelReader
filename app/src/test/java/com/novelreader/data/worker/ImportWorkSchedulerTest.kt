package com.novelreader.data.worker

import androidx.work.WorkManager
import com.novelreader.data.local.preferences.ImportPreferences
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ImportWorkSchedulerTest {
    private val workManager: WorkManager = mockk(relaxed = true)
    private val importPrefs: ImportPreferences = mockk(relaxed = true)
    private val observer: WorkCompletionObserver = mockk(relaxed = true)
    private val specFileStore: SpecFileStore = mockk(relaxed = true)
    private val scheduler = ImportWorkScheduler(workManager, importPrefs, observer, specFileStore)

    @Test
    fun `cancel removes logical job and cancels all requests with job tag`() = runTest {
        val id = UUID.randomUUID()

        scheduler.cancel(id)

        coVerify(exactly = 1) { importPrefs.removeJob(id) }
        verify(exactly = 1) { specFileStore.delete(id) }
        verify(exactly = 1) { workManager.cancelAllWorkByTag("job:$id") }
        verify(exactly = 0) { workManager.cancelWorkById(any()) }
        verify(exactly = 0) { workManager.cancelAllWorkByTag(ChapterImportWorker.TAG_IMPORT) }
    }
}
