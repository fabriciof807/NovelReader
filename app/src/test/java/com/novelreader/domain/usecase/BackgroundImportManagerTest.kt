package com.novelreader.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.worker.ImportWorkScheduler
import com.novelreader.data.worker.WorkCompletionObserver
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class BackgroundImportManagerTest {

    private val scheduler: ImportWorkScheduler = mockk(relaxed = true)
    private val completionObserver: WorkCompletionObserver = mockk(relaxed = true)

    private val manager = BackgroundImportManager(scheduler, completionObserver)

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
    fun `startImport with multiple batches creates multiple specs`() = runTest {
        val links = (1..250).map { ChapterLink("c$it", "u$it", it) }
        manager.startImport("Multi", links)

        assertThat(manager.state.value.running).isTrue()
        assertThat(manager.state.value.totalToImport).isEqualTo(250)
        coVerify(exactly = 3) { scheduler.schedule(any()) }
    }

    @Test
    fun `cancel without id uses current job id`() = runTest {
        manager.startImport("N", listOf(ChapterLink("c", "u", 1)))
        val id = manager.state.value.id!!

        manager.cancel()

        coVerify { scheduler.cancel(id) }
        assertThat(manager.state.value.running).isFalse()
    }

    @Test
    fun `cancel with specific id passes it through`() = runTest {
        val target = UUID.randomUUID()
        manager.cancel(id = target)
        coVerify { scheduler.cancel(target) }
    }

    @Test
    fun `cancelAll delegates to scheduler and resets state`() = runTest {
        manager.startImport("N", listOf(ChapterLink("c", "u", 1)))
        manager.cancelAll()

        coVerify { scheduler.cancelAll() }
        assertThat(manager.state.value.running).isFalse()
        assertThat(manager.state.value.completed).isFalse()
    }

    @Test
    fun `clearCompleted resets state to empty`() = runTest {
        manager.startImport("N", listOf(ChapterLink("c", "u", 1)))
        manager.clearCompleted()
        val state = manager.state.value
        assertThat(state.running).isFalse()
        assertThat(state.completed).isFalse()
        assertThat(state.novelTitle).isEmpty()
    }
}
