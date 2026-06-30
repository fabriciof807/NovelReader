package com.novelreader.domain.usecase

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

    private var importedCountBase: Int = 0
    private var pendingSplitCount: Int = 0

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
                if (_state.value.pendingInQueue > 0) return
                if (_state.value.running || !_state.value.completed) {
                    _state.value = _state.value.copy(
                        running = false, completed = true,
                        pendingInQueue = 0, queuedNovelTitles = emptyList()
                    )
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
        targetNovelId: Long? = null
    ) {
        val specs = ImportJobSpec.create(novelTitle, links, coverUrl, sourceUrl, domain, targetNovelId)

        if (_state.value.running) {
            specs.forEach { scheduler.schedule(it) }
            refreshQueuedTitles()
            return
        }

        val first = specs.first()
        _state.value = BackgroundImportState(
            id = first.id,
            running = true,
            completed = false,
            novelTitle = novelTitle,
            totalToImport = links.size,
            importedCount = 0,
            errors = 0
        )
        importedCountBase = 0
        pendingSplitCount = specs.size
        specs.forEach { scheduler.schedule(it) }
    }

    suspend fun cancel(id: UUID? = null) {
        val target = id ?: _state.value.id
        if (target != null) scheduler.cancel(target)
        _state.value = _state.value.copy(
            running = false, completed = true, pendingInQueue = 0, queuedNovelTitles = emptyList()
        )
    }

    suspend fun cancelAll() {
        scheduler.cancelAll()
        _state.value = BackgroundImportState()
    }

    fun clearCompleted() {
        _state.value = BackgroundImportState()
    }
}
