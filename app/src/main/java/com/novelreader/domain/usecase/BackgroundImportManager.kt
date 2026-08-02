package com.novelreader.domain.usecase

import android.util.Log
import com.novelreader.data.local.preferences.ImportPreferences
import com.novelreader.data.worker.ImportWorkScheduler
import com.novelreader.data.worker.ObserverCallbacks
import com.novelreader.data.worker.WorkCompletionObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BackgroundImportError(
    val url: String,
    val message: String
)

data class BackgroundImportState(
    val id: UUID? = null,
    val running: Boolean = false,
    val completed: Boolean = false,
    val novelTitle: String = "",
    val importedCount: Int = 0,
    val totalToImport: Int = 0,
    val errors: Int = 0,
    val errorDetails: List<BackgroundImportError> = emptyList(),
    val pendingInQueue: Int = 0,
    val queuedNovelTitles: List<String> = emptyList(),
    val currentChapterNum: Int = 0
)

@Singleton
class BackgroundImportManager @Inject constructor(
    private val scheduler: ImportWorkScheduler,
    private val completionObserver: WorkCompletionObserver,
    private val importPrefs: ImportPreferences
) {

    private val _state = MutableStateFlow(BackgroundImportState())
    val state: StateFlow<BackgroundImportState> = _state.asStateFlow()

    @Volatile
    private var importedCountBase: Int = 0
    @Volatile
    private var pendingSplitCount: Int = 0
    @Volatile
    private var handoffPending: Boolean = false

    private suspend fun refreshQueuedTitles() {
        val specs = importPrefs.pendingQueue.first()
        val currentTitle = _state.value.novelTitle
        val titles = specs.map { it.novelTitle }.distinct().filter { it != currentTitle }
        _state.update { it.copy(queuedNovelTitles = titles, pendingInQueue = titles.size) }
    }

    init {
        completionObserver.callbacks = object : ObserverCallbacks {
            override fun onProgress(id: UUID, processed: Int, total: Int, currentChapter: Int) {
                if (_state.value.id == id) {
                    handoffPending = false
                    val cumulative = importedCountBase + processed
                    _state.value = _state.value.copy(
                        running = true,
                        completed = false,
                        importedCount = cumulative,
                        totalToImport = total.coerceAtLeast(_state.value.totalToImport),
                        currentChapterNum = currentChapter
                    )
                }
            }

            override suspend fun onJobTerminal(id: UUID, success: Boolean) {
                if (_state.value.id != id) return
                handoffPending = false
                pendingSplitCount--
                if (pendingSplitCount <= 0) {
                    val remaining = importPrefs.pendingQueue.first()
                    if (remaining.isNotEmpty()) {
                        val nextTitle = remaining.first().novelTitle
                        val nextNovelSpecs = remaining.filter { it.novelTitle == nextTitle }
                        pendingSplitCount = nextNovelSpecs.size
                        importedCountBase = 0
                        _state.value = _state.value.copy(
                            id = nextNovelSpecs.first().id,
                            novelTitle = nextTitle,
                            totalToImport = nextNovelSpecs.sumOf { it.links.size },
                            importedCount = 0,
                            errors = 0,
                            errorDetails = emptyList()
                        )
                    } else {
                        _state.value = _state.value.copy(
                            running = false, completed = true
                        )
                        importedCountBase = 0
                    }
                    refreshQueuedTitles()
                } else {
                    importedCountBase = _state.value.importedCount
                }
            }

            override fun onAllIdle() {
                val snapshot = _state.value
                Log.w("ImportRetry", "onAllIdle running=${snapshot.running} completed=${snapshot.completed} pendingInQueue=${snapshot.pendingInQueue} novelTitle=${snapshot.novelTitle}")
                if (handoffPending || snapshot.id == null || pendingSplitCount > 0) return
                if (snapshot.pendingInQueue > 0) return
                if (snapshot.running || !snapshot.completed) {
                    val completedState = snapshot.copy(
                        running = false, completed = true,
                        pendingInQueue = 0, queuedNovelTitles = emptyList()
                    )
                    _state.compareAndSet(snapshot, completedState)
                }
            }
        }
        completionObserver.start()
    }

    suspend fun startImport(
        novelTitle: String,
        links: List<ChapterLink>,
        coverUrl: String? = null,
        sourceUrl: String = "",
        domain: String = "",
        targetNovelId: Long? = null,
        isFavorite: Boolean? = null
    ) {
        completionObserver.withSchedulingLock {
            val specs = ImportJobSpec.create(
                novelTitle = novelTitle,
                links = links,
                coverUrl = coverUrl,
                sourceUrl = sourceUrl,
                domain = domain,
                targetNovelId = targetNovelId,
                isFavorite = isFavorite
            )
            Log.w("ImportRetry", "startImport title=$novelTitle links=${links.size} batches=${specs.size} running=${_state.value.running} targetNovelId=$targetNovelId")

            if (_state.value.running) {
                specs.forEach { scheduler.schedule(it) }
                refreshQueuedTitles()
                return@withSchedulingLock
            }

            val first = specs.first()
            importedCountBase = 0
            pendingSplitCount = specs.size
            _state.value = BackgroundImportState(
                id = first.id,
                running = true,
                completed = false,
                novelTitle = novelTitle,
                totalToImport = links.size,
                importedCount = 0,
                errors = 0
            )
            specs.forEach { scheduler.schedule(it) }
        }
    }

    suspend fun cancel(id: UUID? = null) {
        completionObserver.withSchedulingLock {
            val current = _state.value
            val targetId = id ?: current.id

            if (current.novelTitle.isNotBlank()) {
                importPrefs.removeJobsByNovelTitle(current.novelTitle)
            }
            targetId?.let { scheduler.cancel(it) }

            importedCountBase = 0
            pendingSplitCount = 0

            val remaining = importPrefs.pendingQueue.first()
            if (remaining.isEmpty()) {
                handoffPending = false
                _state.value = BackgroundImportState()
                return@withSchedulingLock
            }

            val nextTitle = remaining.first().novelTitle
            val nextSpecs = remaining.filter { it.novelTitle == nextTitle }
            handoffPending = true
            pendingSplitCount = nextSpecs.size
            _state.value = BackgroundImportState(
                id = nextSpecs.first().id,
                running = true,
                novelTitle = nextTitle,
                totalToImport = nextSpecs.sumOf { it.links.size }
            )
            refreshQueuedTitles()
            completionObserver.tryScheduleNext()
        }
    }

    suspend fun cancelAll() {
        completionObserver.withSchedulingLock {
            scheduler.cancelAll()
            handoffPending = false
            _state.value = BackgroundImportState()
        }
    }

    fun clearCompleted() {
        handoffPending = false
        _state.value = BackgroundImportState()
    }
}
