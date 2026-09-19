package com.novelreader.data.worker

import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.novelreader.data.local.preferences.ImportPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A job that WorkManager is running but that the manager never started itself — the process was
 * restarted while it was downloading, or a worker started it. It carries what the manager needs to
 * rebuild its state from WorkManager instead of from memory, which does not survive a restart.
 */
data class RunningImportJob(
    val id: UUID,
    val novelTitle: String,
    val batchLinks: Int,
    val remainingSplits: Int
)

interface ObserverCallbacks {
    fun onProgress(id: UUID, processed: Int, total: Int, currentChapter: Int = 0)
    suspend fun onJobTerminal(id: UUID, success: Boolean)
    fun onAllIdle()

    /**
     * A job is running that this manager is not tracking. Without adopting it the banner names
     * whichever novel was started last and its counter never moves, while this one downloads.
     */
    suspend fun onJobAdopted(job: RunningImportJob) {}
}

@Singleton
class WorkCompletionObserver @Inject constructor(
    private val workManager: WorkManager,
    private val importPrefs: ImportPreferences,
    private val specFileStore: SpecFileStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val schedulingMutex = Mutex()
    private val processedWorkIds: MutableSet<UUID> = mutableSetOf()
    private val progressCache: MutableMap<UUID, Pair<Int, Int>> = mutableMapOf()

    /** The job already reported to [ObserverCallbacks.onJobAdopted], to announce each one once. */
    private var announcedJobId: UUID? = null

    var callbacks: ObserverCallbacks? = null

    suspend fun withSchedulingLock(block: suspend () -> Unit) {
        schedulingMutex.withLock { block() }
    }

    fun start() {
        scope.launch {
            workManager.getWorkInfosByTagFlow(ChapterImportWorker.TAG_IMPORT)
                .collect { workInfos ->
                    var hasTerminal = false
                    var hasActive = false

                    schedulingMutex.withLock {
                        for (info in workInfos) {
                            val state = info.state
                            val isTerminal = state == WorkInfo.State.SUCCEEDED ||
                                state == WorkInfo.State.FAILED ||
                                state == WorkInfo.State.CANCELLED

                            if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                                hasActive = true
                                val jobId = extractJobId(info)
                                if (jobId != null) {
                                    if (jobId != announcedJobId) {
                                        announcedJobId = jobId
                                        specFileStore.read(jobId)?.let { spec ->
                                            callbacks?.onJobAdopted(
                                                RunningImportJob(
                                                    id = jobId,
                                                    novelTitle = spec.novelTitle,
                                                    batchLinks = spec.links.size,
                                                    remainingSplits = (spec.splitCount - spec.splitIndex)
                                                        .coerceAtLeast(1)
                                                )
                                            )
                                        }
                                    }
                                    val processed = info.progress.getInt(ChapterImportWorker.KEY_PROGRESS, 0)
                                    val total = info.progress.getInt(ChapterImportWorker.KEY_TOTAL, 0)
                                    if (total > 0) {
                                        progressCache[info.id] = Pair(processed, total)
                                    }
                                    val currentChapter = info.progress.getInt(ChapterImportWorker.KEY_CURRENT_CHAPTER, 0)
                                    callbacks?.onProgress(jobId, processed, total, currentChapter)
                                }
                            } else if (isTerminal && processedWorkIds.add(info.id)) {
                                hasTerminal = true
                                val jobId = extractJobId(info)
                                if (jobId != null) {
                                    if (jobId == announcedJobId) announcedJobId = null
                                    val cached = progressCache.remove(info.id)
                                    val processed = cached?.first
                                        ?: info.progress.getInt(ChapterImportWorker.KEY_PROGRESS, 0)
                                    val total = cached?.second
                                        ?: info.progress.getInt(ChapterImportWorker.KEY_TOTAL, 0)
                                    if (total > 0) {
                                        callbacks?.onProgress(jobId, processed, total)
                                    }
                                    val success = state == WorkInfo.State.SUCCEEDED
                                    callbacks?.onJobTerminal(jobId, success)
                                }
                            }
                        }

                        if (!hasActive && workInfos.all { isTerminalState(it.state) || processedWorkIds.contains(it.id) }) {
                            callbacks?.onAllIdle()
                        }
                    }

                    if (hasTerminal) tryScheduleNext()
                }
        }
    }

    private fun isTerminalState(state: WorkInfo.State): Boolean =
        state == WorkInfo.State.SUCCEEDED ||
        state == WorkInfo.State.FAILED ||
        state == WorkInfo.State.CANCELLED

    suspend fun onJobCompleted(id: UUID, success: Boolean, errorCount: Int) {
        tryScheduleNext()
    }

    fun tryScheduleNext() {
        scope.launch { scheduleNextIfIdle() }
    }

    /** Whether an import is running or waiting to run right now. */
    internal suspend fun hasActiveWork(): Boolean = workManager
        .getWorkInfosByTagFlow(ChapterImportWorker.TAG_IMPORT)
        .first()
        .any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    internal suspend fun scheduleNextIfIdle() {
        schedulingMutex.withLock {
            val active = hasActiveWork()
            if (active) {
                Log.w("ImportRetry", "tryScheduleNext activeWork=yes → skip-active")
                return@withLock
            }

            val next = importPrefs.dequeueJob() ?: run {
                Log.w("ImportRetry", "tryScheduleNext activeWork=no queue=empty → skip-empty")
                return@withLock
            }
            specFileStore.write(next)
            val request = ImportWorkRequestFactory.build(next)
            workManager.enqueueUniqueWork(
                ChapterImportWorker.UNIQUE_ACTIVE,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
            Log.w("ImportRetry", "tryScheduleNext activeWork=no → enqueued id=${next.id} title=${next.novelTitle} links=${next.links.size}")
        }
    }

    private fun extractJobId(info: WorkInfo): UUID? {
        return info.tags.firstOrNull { it.startsWith("job:") }?.removePrefix("job:")
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
