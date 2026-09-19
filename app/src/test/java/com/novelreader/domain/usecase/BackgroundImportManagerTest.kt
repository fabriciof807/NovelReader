package com.novelreader.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.ImportPreferences
import com.novelreader.data.worker.ImportWorkScheduler
import com.novelreader.data.worker.ObserverCallbacks
import com.novelreader.data.worker.RunningImportJob
import com.novelreader.data.worker.WorkCompletionObserver
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundImportManagerTest {

    private val scheduler: ImportWorkScheduler = mockk(relaxed = true)
    private val completionObserver: WorkCompletionObserver = mockk(relaxed = true)
    private val importPrefs: ImportPreferences = mockk(relaxed = true)

    private val manager = BackgroundImportManager(scheduler, completionObserver, importPrefs)

    @Before
    fun setUp() {
        coEvery { completionObserver.withSchedulingLock(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        every { importPrefs.pendingQueue } returns flowOf(emptyList())
    }

    @Test
    fun `startImport while work is already running enqueues instead of claiming`() = runTest {
        // The process restarted mid-import, so the manager holds no state, but WorkManager is
        // still downloading. The new novel must be queued, not announced as the current one.
        coEvery { completionObserver.hasActiveWork() } returns true

        manager.startImport("Queued Novel", listOf(ChapterLink("q", "https://q/1", 1)))

        coVerify { scheduler.schedule(match { it.novelTitle == "Queued Novel" }) }
        assertThat(manager.state.value.novelTitle).isEmpty()
        assertThat(manager.state.value.running).isFalse()
    }

    @Test
    fun `startImport with earlier jobs still queued does not claim the new novel`() = runTest {
        val earlier = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Earlier Novel",
            links = listOf("https://earlier/1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            enqueuedAt = 1L
        )
        every { importPrefs.pendingQueue } returns flowOf(listOf(earlier))

        manager.startImport("Queued Novel", listOf(ChapterLink("q", "https://q/1", 1)))

        coVerify { scheduler.schedule(match { it.novelTitle == "Queued Novel" }) }
        assertThat(manager.state.value.novelTitle).isEmpty()
        assertThat(manager.state.value.running).isFalse()
    }

    @Test
    fun `a running job the manager never started is adopted`() = runTest {
        val queued = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Queued Novel",
            links = listOf("https://q/1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            enqueuedAt = 2L
        )
        every { importPrefs.pendingQueue } returns flowOf(listOf(queued))
        val runningId = UUID.randomUUID()
        val callbacks = slot<ObserverCallbacks>()
        verify { completionObserver.callbacks = capture(callbacks) }

        callbacks.captured.onJobAdopted(
            RunningImportJob(
                id = runningId,
                novelTitle = "Running Novel",
                batchLinks = 100,
                remainingSplits = 2
            )
        )

        assertThat(manager.state.value.id).isEqualTo(runningId)
        assertThat(manager.state.value.novelTitle).isEqualTo("Running Novel")
        assertThat(manager.state.value.running).isTrue()
        assertThat(manager.state.value.queuedNovelTitles).containsExactly("Queued Novel")

        // Progress for the adopted job now moves its own counter instead of being discarded.
        callbacks.captured.onProgress(runningId, processed = 40, total = 100, currentChapter = 40)
        assertThat(manager.state.value.importedCount).isEqualTo(40)

        // Two splits: the first terminal leaves it running, the second hands the queue over.
        callbacks.captured.onJobTerminal(runningId, success = true)
        assertThat(manager.state.value.running).isTrue()
        callbacks.captured.onJobTerminal(runningId, success = true)
        assertThat(manager.state.value.id).isEqualTo(queued.id)
        assertThat(manager.state.value.novelTitle).isEqualTo("Queued Novel")
        assertThat(manager.state.value.running).isTrue()
    }

    @Test
    fun `adoption is ignored when the manager already tracks a job`() = runTest {
        manager.startImport("Own Novel", listOf(ChapterLink("o", "https://o/1", 1)))
        val callbacks = slot<ObserverCallbacks>()
        verify { completionObserver.callbacks = capture(callbacks) }
        val ownId = manager.state.value.id

        callbacks.captured.onJobAdopted(
            RunningImportJob(UUID.randomUUID(), "Someone Else", batchLinks = 100, remainingSplits = 1)
        )

        assertThat(manager.state.value.id).isEqualTo(ownId)
        assertThat(manager.state.value.novelTitle).isEqualTo("Own Novel")
    }

    @Test
    fun `startImport delegates to scheduler with spec`() = runTest {
        manager.startImport(
            novelTitle = "Test Novel",
            links = listOf(ChapterLink("c1", "u1", 1), ChapterLink("c2", "u2", 2)),
            coverUrl = "https://example.com/cover.jpg"
        )

        val state = manager.state.value
        assertThat(state.running).isTrue()
        assertThat(state.novelTitle).isEqualTo("Test Novel")
        assertThat(state.totalToImport).isEqualTo(2)
        coVerify { scheduler.schedule(match { it.novelTitle == "Test Novel" && it.links.size == 2 }) }
    }

    @Test
    fun `startImport propagates favorite metadata to scheduled spec`() = runTest {
        manager.startImport(
            novelTitle = "Favorite Novel",
            links = listOf(ChapterLink("c1", "u1", 1)),
            isFavorite = false
        )

        coVerify { scheduler.schedule(match { it.isFavorite == false }) }
    }

    @Test
    fun `startImport with multiple batches creates multiple specs`() = runTest {
        val links = (1..250).map { ChapterLink("c$it", "u$it", it) }
        manager.startImport("Multi", links)

        assertThat(manager.state.value.running).isTrue()
        assertThat(manager.state.value.totalToImport).isEqualTo(250)
        coVerify(exactly = 3) { scheduler.schedule(any()) }
    }

    @Test
    fun `onAllIdle leaves import running while split jobs remain`() = runTest {
        val links = (1..250).map { ChapterLink("c$it", "u$it", it) }
        manager.startImport("Multi", links)
        val callbacks = slot<ObserverCallbacks>()
        verify { completionObserver.callbacks = capture(callbacks) }

        callbacks.captured.onAllIdle()

        assertThat(manager.state.value.running).isTrue()
        assertThat(manager.state.value.completed).isFalse()
    }

    @Test
    fun `onAllIdle cannot complete new import before pending splits are visible`() = runTest {
        val callbacks = slot<ObserverCallbacks>()
        verify { completionObserver.callbacks = capture(callbacks) }
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.state.drop(1).collect { state ->
                if (state.novelTitle == "New" && state.running) {
                    callbacks.captured.onAllIdle()
                }
            }
        }

        manager.startImport(
            "New",
            (1..250).map { ChapterLink("c$it", "u$it", it) }
        )
        observer.cancel()

        assertThat(manager.state.value.running).isTrue()
        assertThat(manager.state.value.completed).isFalse()
    }

    @Test
    fun `stale idle callback does not complete a new import`() = runTest {
        manager.startImport("Old", listOf(ChapterLink("old", "old", 1)))
        val callbacks = slot<ObserverCallbacks>()
        verify { completionObserver.callbacks = capture(callbacks) }

        // The first import finishes, so nothing is tracked when the next one starts.
        callbacks.captured.onJobTerminal(manager.state.value.id!!, success = true)
        manager.startImport("New", listOf(ChapterLink("new", "new", 1)))

        callbacks.captured.onAllIdle()

        assertThat(manager.state.value.novelTitle).isEqualTo("New")
        assertThat(manager.state.value.running).isTrue()
        assertThat(manager.state.value.completed).isFalse()
    }

    @Test
    fun `final terminal callback completes import`() = runTest {
        every { importPrefs.pendingQueue } returns flowOf(emptyList())
        manager.startImport("Only", listOf(ChapterLink("c", "u", 1)))
        val callbacks = slot<ObserverCallbacks>()
        verify { completionObserver.callbacks = capture(callbacks) }

        callbacks.captured.onJobTerminal(manager.state.value.id!!, success = true)

        assertThat(manager.state.value.running).isFalse()
        assertThat(manager.state.value.completed).isTrue()
    }

    @Test
    fun `cancel current novel preserves queue and adopts next novel`() = runTest {
        val nextId = UUID.randomUUID()
        val next = ImportJobSpec(
            id = nextId,
            novelTitle = "Novel B",
            links = listOf("https://b/1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            enqueuedAt = 2L
        )
        val queue = mutableListOf<ImportJobSpec>()
        every { importPrefs.pendingQueue } returns flow { emit(queue.toList()) }
        manager.startImport("Novel A", listOf(ChapterLink("A1", "https://a/1", 1)))
        val actualCurrentId = manager.state.value.id!!
        val callbacks = slot<ObserverCallbacks>()
        verify { completionObserver.callbacks = capture(callbacks) }
        // Novel B lands in the queue while Novel A is the one running.
        queue += next

        manager.cancel()

        coVerify { scheduler.cancel(actualCurrentId) }
        coVerify { importPrefs.removeJobsByNovelTitle("Novel A") }
        verify { completionObserver.tryScheduleNext() }
        assertThat(manager.state.value.id).isEqualTo(nextId)
        assertThat(manager.state.value.novelTitle).isEqualTo("Novel B")
        assertThat(manager.state.value.running).isTrue()

        callbacks.captured.onAllIdle()
        assertThat(manager.state.value.running).isTrue()
        assertThat(manager.state.value.completed).isFalse()

        callbacks.captured.onProgress(actualCurrentId, processed = 99, total = 99, currentChapter = 99)
        assertThat(manager.state.value.importedCount).isEqualTo(0)
        callbacks.captured.onProgress(nextId, processed = 1, total = 1, currentChapter = 1)
        assertThat(manager.state.value.importedCount).isEqualTo(1)
    }

    @Test
    fun `cancel last novel resets id and running state`() = runTest {
        every { importPrefs.pendingQueue } returns flowOf(emptyList())
        manager.startImport("Only", listOf(ChapterLink("C1", "https://c/1", 1)))

        manager.cancel()

        assertThat(manager.state.value.id).isNull()
        assertThat(manager.state.value.running).isFalse()
        assertThat(manager.state.value.completed).isFalse()
    }

    @Test
    fun `cancel waits for scheduling lock before handing off to the next novel`() = runTest {
        val next = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Novel B",
            links = listOf("https://b/1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            enqueuedAt = 2L
        )
        val queue = mutableListOf<ImportJobSpec>()
        every { importPrefs.pendingQueue } returns flow { emit(queue.toList()) }
        manager.startImport("Novel A", listOf(ChapterLink("A1", "https://a/1", 1)))
        queue += next

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { completionObserver.withSchedulingLock(any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            firstArg<suspend () -> Unit>().invoke()
        }

        val cancellation = backgroundScope.async { manager.cancel() }
        entered.await()
        coVerify(exactly = 0) { importPrefs.removeJobsByNovelTitle("Novel A") }

        release.complete(Unit)
        cancellation.await()

        coVerify { importPrefs.removeJobsByNovelTitle("Novel A") }
        verify { completionObserver.tryScheduleNext() }
        assertThat(manager.state.value.id).isEqualTo(next.id)
        assertThat(manager.state.value.novelTitle).isEqualTo("Novel B")
    }

    @Test
    fun `startImport waits for scheduling lock before enqueueing splits`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { completionObserver.withSchedulingLock(any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            firstArg<suspend () -> Unit>().invoke()
        }

        val start = backgroundScope.async {
            manager.startImport(
                "Novel A",
                (1..250).map { ChapterLink("A$it", "https://a/$it", it) }
            )
        }
        entered.await()
        coVerify(exactly = 0) { scheduler.schedule(any()) }

        release.complete(Unit)
        start.await()
        coVerify(exactly = 3) { scheduler.schedule(any()) }
    }

    @Test
    fun `cancel with no active title does nothing`() = runTest {
        every { importPrefs.pendingQueue } returns flowOf(emptyList())
        manager.cancel()
        coVerify(exactly = 0) { scheduler.cancel(any()) }
    }
}
