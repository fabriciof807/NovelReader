package com.novelreader.ui.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.novelreader.R
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.FolderDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterEntity
import com.novelreader.data.local.db.entity.FolderEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.data.remote.MvlempyrCharacterImporter
import com.novelreader.data.worker.UpdateCheckScheduler
import com.novelreader.di.qualifiers.IoDispatcher
import com.novelreader.domain.usecase.BackgroundImportManager
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.ScanMissingChaptersUseCase
import com.novelreader.domain.usecase.ScanResult
import com.novelreader.domain.usecase.importnovel.ChapterEntry
import com.novelreader.domain.usecase.importnovel.ChapterInserter
import com.novelreader.domain.usecase.importnovel.FileCharsetDetector
import com.novelreader.util.StringUtils
import com.novelreader.domain.usecase.CharacterManagementUseCase
import com.novelreader.domain.usecase.CoverManagementUseCase
import com.novelreader.domain.usecase.RetryChapterUseCase
import com.novelreader.domain.usecase.WebImportUseCase
import kotlinx.coroutines.CoroutineDispatcher
import com.novelreader.ui.library.mvi.LibraryState
import com.novelreader.ui.notifications.NotificationPermissionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

enum class SortOrder { TITLE, CREATED_AT, LAST_READ }
enum class ChapterSortOrder { ASCENDING, DESCENDING }
enum class ViewMode { GRID, LIST }
enum class NovelFilter { ALL, READING, COMPLETED, FAVORITES }

const val MAX_PINNED_FOLDERS = 3

data class LibraryStats(
    val totalNovels: Int = 0,
    val totalChapters: Int = 0,
    val totalBookmarks: Int = 0
)

data class WhatsNewGroup(
    val novelId: Long,
    val novelTitle: String,
    val count: Int,
    val chapterTitles: List<String>
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: com.novelreader.data.local.db.dao.BookmarkDao,
    private val backgroundImportManager: BackgroundImportManager,
    private val libraryPreferences: LibraryPreferences,
    private val characterManagementUseCase: CharacterManagementUseCase,
    private val coverManagementUseCase: CoverManagementUseCase,
    private val characterPhotoDao: CharacterPhotoDao,
    private val mvlempyrCharacterImporter: MvlempyrCharacterImporter,
    private val updateCheckScheduler: UpdateCheckScheduler,
    private val notificationPermissionCoordinator: NotificationPermissionCoordinator,
    private val webImportUseCase: WebImportUseCase,
    private val failedChapterDao: FailedChapterDao,
    private val folderDao: FolderDao,
    private val retryChapterUseCase: RetryChapterUseCase,
    private val scanMissingChaptersUseCase: ScanMissingChaptersUseCase,
    private val chapterInserter: ChapterInserter,
    private val parserRegistry: ParserRegistry,
    private val mhtParser: MhtParser,
    private val fileCharsetDetector: FileCharsetDetector,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    private val _errorEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.LAST_READ)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode

    val novels: StateFlow<List<NovelEntity>> = combine(
        novelDao.getAllNovels(),
        _sortOrder
    ) { list, order ->
        when (order) {
            SortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
            SortOrder.CREATED_AT -> list.sortedByDescending { it.createdAt }
            SortOrder.LAST_READ -> list.sortedByDescending { it.lastReadAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<LibraryStats> = combine(
        novelDao.getAllNovels(),
        bookmarkDao.getAll()
    ) { novels, bookmarks ->
        LibraryStats(
            totalNovels = novels.size,
            totalChapters = novels.sumOf { it.totalChapters },
            totalBookmarks = bookmarks.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

    private val _selectedNovel = MutableStateFlow<NovelEntity?>(null)
    val selectedNovel: StateFlow<NovelEntity?> = _selectedNovel

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _chapterSortOrder = MutableStateFlow(ChapterSortOrder.ASCENDING)
    val chapterSortOrder: StateFlow<ChapterSortOrder> = _chapterSortOrder

    // Observed rather than read once: the reader marks a chapter read through the DAO while this
    // ViewModel is alive, and a one-shot read left the list drawing it as unread until the tab was
    // re-entered. The sort rides along so toggling it re-sorts without a re-query.
    val chapters: StateFlow<List<ChapterEntity>> = _selectedNovel
        .flatMapLatest { novel ->
            if (novel == null) flowOf(emptyList()) else chapterDao.observeChaptersByNovel(novel.id)
        }
        .combine(_chapterSortOrder) { chapters, order ->
            when (order) {
                ChapterSortOrder.ASCENDING -> chapters.sortedBy { it.orderIndex }
                ChapterSortOrder.DESCENDING -> chapters.sortedByDescending { it.orderIndex }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bookmarkCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val bookmarkCounts: StateFlow<Map<Long, Int>> = _bookmarkCounts

    private val _characters = MutableStateFlow<List<CharacterEntity>>(emptyList())
    val characters: StateFlow<List<CharacterEntity>> = _characters

    private val _characterPhotos = MutableStateFlow<Map<Long, List<CharacterPhotoEntity>>>(emptyMap())
    val characterPhotos: StateFlow<Map<Long, List<CharacterPhotoEntity>>> = _characterPhotos

    private val _showDeleteDialog = MutableStateFlow<NovelEntity?>(null)
    val showDeleteDialog: StateFlow<NovelEntity?> = _showDeleteDialog

    private val _showUrlDialog = MutableStateFlow<NovelEntity?>(null)
    val showUrlDialog: StateFlow<NovelEntity?> = _showUrlDialog

    private val _coverTargetNovel = MutableStateFlow<NovelEntity?>(null)
    val coverTargetNovel: StateFlow<NovelEntity?> = _coverTargetNovel

    private val _coverError = MutableStateFlow<String?>(null)
    val coverError: StateFlow<String?> = _coverError

    private val _isImportingCharacters = MutableStateFlow(false)
    val isImportingCharacters: StateFlow<Boolean> = _isImportingCharacters

    private val _characterImportResult = MutableStateFlow<String?>(null)
    val characterImportResult: StateFlow<String?> = _characterImportResult

    private val _failedChapters = MutableStateFlow<List<FailedChapterEntity>>(emptyList())
    val failedChapters: StateFlow<List<FailedChapterEntity>> = _failedChapters

    private val _scrollToFailedRequest = MutableStateFlow<Long?>(null)
    val scrollToFailedRequest: StateFlow<Long?> = _scrollToFailedRequest

    fun consumeScrollToFailed() {
        _scrollToFailedRequest.value = null
    }

    private val _updateCheckResult = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updateCheckResult: SharedFlow<String> = _updateCheckResult.asSharedFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates

    private val _showWhatsNew = MutableStateFlow(false)
    val showWhatsNew: StateFlow<Boolean> = _showWhatsNew

    val whatsNewGroups: StateFlow<List<WhatsNewGroup>> = chapterDao.getNewChaptersFlow()
        .map { items ->
            items.groupBy { it.novelId }.map { (novelId, chapters) ->
                WhatsNewGroup(
                    novelId = novelId,
                    novelTitle = chapters.first().novelTitle,
                    count = chapters.size,
                    chapterTitles = chapters.map { it.chapterTitle }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val newChapterCounts: StateFlow<Map<Long, Int>> = whatsNewGroups
        .map { groups -> groups.associate { it.novelId to it.count } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val folders: StateFlow<List<FolderEntity>> = folderDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folderCounts: StateFlow<Map<Long, Int>> = folderDao.getFolderCounts()
        .map { counts -> counts.associate { it.folderId to it.count } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _selectedFolder = MutableStateFlow<FolderEntity?>(null)
    val selectedFolder: StateFlow<FolderEntity?> = _selectedFolder

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val novelsInFolder: StateFlow<List<NovelEntity>> = _selectedFolder
        .flatMapLatest { folder -> folder?.let { folderDao.getNovelsInFolder(it.id) } ?: emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backgroundImportState: StateFlow<BackgroundImportState> = backgroundImportManager.state

    val readProgress: StateFlow<Map<Long, Float>> = novelDao.getAllNovels().map { novels ->
        val counts = chapterDao.getReadCountPerNovel().associate { it.novelId to it.readCount }
        novels.associate { novel ->
            val read = counts[novel.id] ?: 0
            val progress = if (novel.totalChapters > 0) read.toFloat() / novel.totalChapters else 0f
            novel.id to progress
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    data class ChaptersScrollState(
        val firstVisibleItemIndex: Int,
        val firstVisibleItemScrollOffset: Int
    )

    private val chaptersScrollKey = "chapters_scroll"

    val chaptersScrollByNovel: StateFlow<Map<Long, ChaptersScrollState>> =
        savedStateHandle.getStateFlow<String?>(chaptersScrollKey, null)
            .map { json -> decodeChaptersScroll(json) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, decodeChaptersScroll(savedStateHandle.get<String?>(chaptersScrollKey)))

    fun setChaptersScroll(novelId: Long, index: Int, offset: Int) {
        val current = chaptersScrollByNovel.value.toMutableMap()
        current[novelId] = ChaptersScrollState(index, offset)
        savedStateHandle[chaptersScrollKey] = encodeChaptersScroll(current)
    }

    fun getChaptersScroll(novelId: Long): ChaptersScrollState? =
        chaptersScrollByNovel.value[novelId]

    private fun encodeChaptersScroll(map: Map<Long, ChaptersScrollState>): String {
        val obj = JSONObject()
        for ((id, state) in map) {
            val arr = JSONArray()
            arr.put(state.firstVisibleItemIndex)
            arr.put(state.firstVisibleItemScrollOffset)
            obj.put(id.toString(), arr)
        }
        return obj.toString()
    }

    private fun decodeChaptersScroll(json: String?): Map<Long, ChaptersScrollState> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<Long, ChaptersScrollState>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val idStr = keys.next()
                val id = idStr.toLongOrNull() ?: continue
                val arr = obj.optJSONArray(idStr) ?: continue
                if (arr.length() == 2) {
                    result[id] = ChaptersScrollState(arr.getInt(0), arr.getInt(1))
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    init {
        viewModelScope.launch {
            val saved = libraryPreferences.sortOrder.first()
            val order = try { SortOrder.valueOf(saved) } catch (_: Exception) { SortOrder.LAST_READ }
            _sortOrder.value = order
            _state.value = _state.value.copy(sortOrder = order)
        }
        viewModelScope.launch {
            val saved = libraryPreferences.viewMode.first()
            val mode = try { ViewMode.valueOf(saved) } catch (_: Exception) { ViewMode.GRID }
            _viewMode.value = mode
            _state.value = _state.value.copy(viewMode = mode)
        }
        viewModelScope.launch {
            bookmarkDao.getAll().collect { bookmarks ->
                val counts = bookmarks.groupBy { it.chapterId }.mapValues { it.value.size }
                _bookmarkCounts.value = counts
            }
        }
        viewModelScope.launch {
            updateCheckScheduler.rescheduleIfNeeded()
        }
        viewModelScope.launch {
            if (chapterDao.countNewChapters() > 0) {
                _showWhatsNew.value = true
            }
        }
        viewModelScope.launch {
            val saved = libraryPreferences.chapterSortOrder.first()
            val order = try { ChapterSortOrder.valueOf(saved) } catch (_: Exception) { ChapterSortOrder.ASCENDING }
            _chapterSortOrder.value = order
        }
        val pendingNovelId = savedStateHandle.get<Long>(ARG_SELECTED_NOVEL_ID)?.takeIf { it > 0L }
        if (pendingNovelId != null) {
            viewModelScope.launch {
                val novel = novelDao.getNovelById(pendingNovelId)
                if (novel != null) {
                    selectNovel(novel)
                    if (savedStateHandle.get<Boolean>(ARG_SHOW_FAILED) == true) {
                        _scrollToFailedRequest.value = pendingNovelId
                    }
                }
                savedStateHandle.remove<Long>(ARG_SELECTED_NOVEL_ID)
                savedStateHandle.remove<Boolean>(ARG_SHOW_FAILED)
            }
        }
    }

    fun selectTab(index: Int) { _selectedTab.value = index }

    fun selectNovel(novel: NovelEntity) {
        _selectedNovel.value = novel
        _selectedFolder.value = null
        _selectedTab.value = 1
        viewModelScope.launch {
            novelDao.setHasUpdates(novel.id, false)
        }
        loadNovelDetail(novel.id)
    }

    fun deselectNovel() {
        _selectedNovel.value = null
        _selectedTab.value = 0
        _failedChapters.value = emptyList()
    }

    fun openFolder(folder: FolderEntity) {
        _selectedFolder.value = folder
    }

    fun closeFolder() {
        _selectedFolder.value = null
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(io) {
            folderDao.insert(FolderEntity(name = trimmed))
        }
    }

    fun renameFolder(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(io) {
            folderDao.rename(id, trimmed)
        }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch(io) {
            folderDao.delete(id)
            if (_selectedFolder.value?.id == id) _selectedFolder.value = null
        }
    }

    fun togglePin(folder: FolderEntity) {
        viewModelScope.launch(io) {
            if (!folder.isPinned && folderDao.countPinned() >= MAX_PINNED_FOLDERS) {
                _errorEvents.tryEmit(context.getString(R.string.pinned_collections_limit, MAX_PINNED_FOLDERS))
                return@launch
            }
            folderDao.setPinned(folder.id, !folder.isPinned)
        }
    }

    suspend fun getFolderIdsForNovel(novelId: Long): Set<Long> =
        folderDao.getFolderIdsForNovel(novelId).toSet()

    fun setNovelFolders(novelId: Long, folderIds: Set<Long>) {
        viewModelScope.launch(io) {
            folderDao.setNovelFolders(novelId, folderIds)
        }
    }

    fun addNovelsToFolder(folderId: Long, novelIds: List<Long>) {
        viewModelScope.launch(io) {
            folderDao.addNovelsToFolder(folderId, novelIds)
        }
    }

    fun removeNovelFromFolder(folderId: Long, novelId: Long) {
        viewModelScope.launch(io) {
            folderDao.removeNovelFromFolder(folderId, novelId)
        }
    }

    fun loadNovelDetail(novelId: Long) {
        viewModelScope.launch {
            _failedChapters.value = failedChapterDao.getByNovel(novelId)
            refreshCharacters(novelId)
        }
    }

    private fun refreshFailedChapters(novelId: Long) {
        viewModelScope.launch {
            _failedChapters.value = failedChapterDao.getByNovel(novelId)
        }
    }

    fun loadCharacters(novelId: Long) {
        viewModelScope.launch { refreshCharacters(novelId) }
    }

    fun toggleChapterSortOrder() {
        val newOrder = when (_chapterSortOrder.value) {
            ChapterSortOrder.ASCENDING -> ChapterSortOrder.DESCENDING
            ChapterSortOrder.DESCENDING -> ChapterSortOrder.ASCENDING
        }
        _chapterSortOrder.value = newOrder
        viewModelScope.launch { libraryPreferences.updateChapterSortOrder(newOrder.name) }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        viewModelScope.launch { libraryPreferences.updateSortOrder(order.name) }
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        viewModelScope.launch { libraryPreferences.updateViewMode(mode.name) }
    }

    fun requestDelete(novel: NovelEntity) { _showDeleteDialog.value = novel }

    fun requestDeleteById(novelId: Long) {
        val novel = novels.value.find { it.id == novelId } ?: return
        _showDeleteDialog.value = novel
    }

    fun cancelDelete() { _showDeleteDialog.value = null }

    fun confirmDelete() {
        val novel = _showDeleteDialog.value ?: return
        viewModelScope.launch {
            val result = coverManagementUseCase.deleteNovelCovers(novel.id, novel.coverPath)
            if (result.isFailure) {
                _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro ao deletar")
            } else if (_selectedNovel.value?.id == novel.id) {
                _selectedNovel.value = null
                _selectedTab.value = 0
            }
            _showDeleteDialog.value = null
        }
    }

    fun confirmDeleteById(novelId: Long) {
        val novel = _showDeleteDialog.value ?: return
        if (novel.id != novelId) return
        confirmDelete()
    }

    fun requestChangeCover(novel: NovelEntity) { _coverTargetNovel.value = novel }

    fun requestChangeCoverById(novelId: Long) {
        val novel = novels.value.find { it.id == novelId } ?: return
        _coverTargetNovel.value = novel
    }

    fun requestCoverByUrl(novel: NovelEntity) { _showUrlDialog.value = novel }

    fun requestCoverByUrlById(novelId: Long) {
        val novel = novels.value.find { it.id == novelId } ?: return
        _showUrlDialog.value = novel
    }

    fun cancelUrlDialog() {
        _showUrlDialog.value = null
        _coverError.value = null
    }

    fun saveCoverFromUrl(novelId: Long, url: String) {
        viewModelScope.launch {
            if (url.isBlank()) return@launch
            if (!url.startsWith("https://")) {
                _coverError.value = context.getString(R.string.cover_url_https_required)
                return@launch
            }
            val result = coverManagementUseCase.saveFromUrl(novelId, url)
            if (result.isSuccess) {
                _coverError.value = null
            } else {
                _coverError.value = context.getString(R.string.cover_download_error)
            }
            _showUrlDialog.value = null
        }
    }

    fun clearCoverRequest() { _coverTargetNovel.value = null }
    fun clearCoverError() { _coverError.value = null }

    fun saveCover(novelId: Long, uri: android.net.Uri) {
        viewModelScope.launch {
            val result = coverManagementUseCase.saveFromUri(novelId, uri)
            if (result.isSuccess) {
                _coverError.value = null
            } else {
                _coverError.value = context.getString(R.string.cover_save_error)
            }
            _coverTargetNovel.value = null
        }
    }

    fun addCharacter(novelId: Long, name: String, photoPath: String?) {
        viewModelScope.launch {
            val result = characterManagementUseCase.addCharacter(novelId, name, photoPath)
            if (result.isSuccess) refreshCharacters(novelId)
            else _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro")
        }
    }

    fun deleteCharacter(id: Long) {
        val novelId = _selectedNovel.value?.id ?: return
        viewModelScope.launch {
            val result = characterManagementUseCase.deleteCharacter(id, novelId)
            if (result.isSuccess) refreshCharacters(novelId)
            else _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro")
        }
    }

    fun updateCharacterPhoto(id: Long, path: String) {
        viewModelScope.launch {
            val result = characterManagementUseCase.updatePhoto(id, path)
            val novelId = _selectedNovel.value?.id ?: return@launch
            if (result.isSuccess) refreshCharacters(novelId)
            else _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro")
        }
    }

    fun batchAddCharacterPhotos(characterId: Long, photoPaths: List<String>) {
        viewModelScope.launch {
            val result = characterManagementUseCase.batchAddPhotos(characterId, photoPaths)
            val novelId = _selectedNovel.value?.id ?: return@launch
            if (result.isSuccess) refreshCharacters(novelId)
            else _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro")
        }
    }

    fun addCharacterPhoto(characterId: Long, photoPath: String) {
        viewModelScope.launch {
            val result = characterManagementUseCase.addPhoto(characterId, photoPath)
            val novelId = _selectedNovel.value?.id ?: return@launch
            if (result.isSuccess) refreshCharacters(novelId)
            else _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro")
        }
    }

    fun deleteCharacterPhoto(photoId: Long, characterId: Long) {
        viewModelScope.launch {
            val result = characterManagementUseCase.deletePhoto(photoId, characterId)
            val novelId = _selectedNovel.value?.id ?: return@launch
            if (result.isSuccess) refreshCharacters(novelId)
            else _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro")
        }
    }

    fun updateCharacterName(characterId: Long, name: String) {
        viewModelScope.launch {
            val result = characterManagementUseCase.updateName(characterId, name)
            val novelId = _selectedNovel.value?.id ?: return@launch
            if (result.isSuccess) refreshCharacters(novelId)
            else _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro")
        }
    }

    fun updateCharacterNotes(characterId: Long, notes: String?) {
        viewModelScope.launch {
            val result = characterManagementUseCase.updateNotes(characterId, notes)
            val novelId = _selectedNovel.value?.id ?: return@launch
            if (result.isSuccess) refreshCharacters(novelId)
            else _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro")
        }
    }

    fun toggleCharacterFavorite(characterId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            val result = characterManagementUseCase.toggleFavorite(characterId, isFavorite)
            val novelId = _selectedNovel.value?.id ?: return@launch
            if (result.isSuccess) refreshCharacters(novelId)
            else _errorEvents.emit(result.exceptionOrNull()?.message ?: "Erro")
        }
    }

    fun clearCharacterImportResult() { _characterImportResult.value = null }

    fun importCharactersFromUrl(url: String) {
        viewModelScope.launch {
            if (url.isBlank()) return@launch
            if (!url.startsWith("https://")) {
                _characterImportResult.value = context.getString(R.string.character_import_https_required)
                return@launch
            }
            _isImportingCharacters.value = true
            _characterImportResult.value = null
            try {
                val novelId = _selectedNovel.value?.id
                    ?: throw Exception(context.getString(R.string.character_import_no_novel_selected))
                val count = mvlempyrCharacterImporter.importCharacters(url = url, novelId = novelId)
                refreshCharacters(novelId)
                _characterImportResult.value = context.getString(R.string.import_characters_success, count)
            } catch (e: Exception) {
                _characterImportResult.value = e.message
                    ?: context.getString(R.string.import_characters_error)
            } finally {
                _isImportingCharacters.value = false
            }
        }
    }

    private suspend fun refreshCharacters(novelId: Long) {
        val chars = characterManagementUseCase.getCharacters(novelId)
        _characters.value = chars
        val allPhotos = characterPhotoDao.getByCharacterIds(chars.map { it.id })
        _characterPhotos.value = allPhotos.groupBy { it.characterId }
    }

    fun toggleAutoUpdate(novelId: Long) {
        viewModelScope.launch {
            val novel = novelDao.getNovelById(novelId) ?: return@launch
            novelDao.updateAutoUpdate(novelId, !novel.autoUpdate)
            updateCheckScheduler.rescheduleIfNeeded()
        }
    }

    fun toggleNovelFavorite(novelId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            novelDao.updateFavorite(novelId, isFavorite)
        }
    }

    fun checkForUpdates(novelId: Long) {
        viewModelScope.launch {
            val novel = novelDao.getNovelById(novelId) ?: return@launch
            if (novel.sourceUrl.isBlank()) {
                _updateCheckResult.emit(context.getString(R.string.update_check_no_source_url))
                return@launch
            }
            _isCheckingUpdates.value = true
            try {
                val result = webImportUseCase.fetchChapterList(novel.sourceUrl)
                result.fold(
                    onSuccess = { fetchResult ->
                        val existingFileNames = chapterDao
                            .getChaptersByNovelSync(novelId)
                            .map { it.fileName }
                            .toSet()

                        val newChapters = fetchResult.chapters.filter { link ->
                            StringUtils.fileNameFromUrl(link.url, "chapter_${link.chapterNumber}") !in existingFileNames
                        }

                        val emptyChapters = chapterDao
                            .getEmptyChapters(novelId)
                            .mapNotNull { ec ->
                                fetchResult.chapters.find { link ->
                                    StringUtils.fileNameFromUrl(link.url, "chapter_${link.chapterNumber}") == ec.fileName
                                }
                            }

                        val allMissing = (newChapters + emptyChapters).distinctBy { it.url }

                        if (allMissing.isEmpty()) {
                            _updateCheckResult.emit(context.getString(R.string.update_check_no_new_chapters))
                        } else {
                            val newCount = newChapters.size
                            val emptyCount = emptyChapters.size
                            notificationPermissionCoordinator.onUserInitiatedBackgroundImport()
                            backgroundImportManager.startImport(
                                novelTitle = fetchResult.novelTitle ?: novel.title,
                                links = allMissing,
                                coverUrl = fetchResult.coverUrl,
                                sourceUrl = novel.sourceUrl
                            )
                            if (emptyCount > 0) {
                                _updateCheckResult.emit(
                                    context.getString(R.string.update_check_with_repair, allMissing.size, emptyCount)
                                )
                            } else {
                                _updateCheckResult.emit(
                                    context.getString(R.string.update_check_found_new_chapters, allMissing.size)
                                )
                            }
                        }
                        novelDao.updateLastChecked(novelId, System.currentTimeMillis())
                    },
                    onFailure = { e ->
                        _updateCheckResult.emit(
                            context.getString(R.string.update_check_error, e.message ?: "Erro")
                        )
                    }
                )
            } catch (e: Exception) {
                _updateCheckResult.emit(
                    context.getString(R.string.update_check_error, e.message ?: "Erro")
                )
            } finally {
                _isCheckingUpdates.value = false
            }
        }
    }

    fun resyncChapters(novelId: Long) {
        viewModelScope.launch {
            val novel = novelDao.getNovelById(novelId) ?: return@launch
            if (novel.sourceUrl.isBlank()) {
                _updateCheckResult.emit(context.getString(R.string.resync_no_source_url))
                return@launch
            }
            _isCheckingUpdates.value = true
            try {
                val result = webImportUseCase.fetchChapterList(novel.sourceUrl)
                result.fold(
                    onSuccess = { fetchResult ->
                        if (fetchResult.chapters.isEmpty()) {
                            _updateCheckResult.emit(context.getString(R.string.update_check_no_new_chapters))
                            return@fold
                        }
                        chapterDao.deleteByNovelId(novelId)
                        failedChapterDao.deleteByNovelId(novelId)
                        refreshFailedChapters(novelId)
                        notificationPermissionCoordinator.onUserInitiatedBackgroundImport()
                        backgroundImportManager.startImport(
                            novelTitle = fetchResult.novelTitle ?: novel.title,
                            links = fetchResult.chapters,
                            coverUrl = fetchResult.coverUrl,
                            sourceUrl = novel.sourceUrl
                        )
                        novelDao.updateLastChecked(novelId, System.currentTimeMillis())
                        _updateCheckResult.emit(
                            context.getString(R.string.resync_started, fetchResult.chapters.size)
                        )
                    },
                    onFailure = { e ->
                        _updateCheckResult.emit(
                            context.getString(R.string.update_check_error, e.message ?: "Erro")
                        )
                    }
                )
            } catch (e: Exception) {
                _updateCheckResult.emit(
                    context.getString(R.string.update_check_error, e.message ?: "Erro")
                )
            } finally {
                _isCheckingUpdates.value = false
            }
        }
    }

    fun cancelBackgroundImport() {
        viewModelScope.launch { backgroundImportManager.cancel() }
    }

    fun retryFailedChapter(failedId: Long) {
        viewModelScope.launch {
            val failed = failedChapterDao.getById(failedId) ?: return@launch
            val result = retryChapterUseCase.retryByUrl(failedId)
            refreshFailedChapters(failed.novelId)
            if (result.isSuccess) {
                _errorEvents.emit(context.getString(R.string.failed_chapters_retry_success))
            } else {
                _errorEvents.emit(
                    context.getString(
                        R.string.failed_chapters_retry_failed,
                        result.exceptionOrNull()?.message ?: "Erro"
                    )
                )
            }
        }
    }

    fun retryFailedChapterManually(failedId: Long, uri: Uri) {
        viewModelScope.launch {
            val failed = failedChapterDao.getById(failedId) ?: return@launch
            try {
                val fileName = fileCharsetDetector.getFileName(uri, context)
                val raw = fileCharsetDetector.readContent(uri, context)
                val parsed = if (mhtParser.isMhtFile(fileName)) {
                    parserRegistry.parseRaw(raw, fileName)
                } else {
                    parserRegistry.parse(raw, fileName)
                }
                val entry = ChapterEntry(
                    novelTitle = "",
                    chapterTitle = parsed.chapterTitle,
                    content = parsed.content,
                    fileName = failed.fileName
                )
                chapterInserter.insertEntries(failed.novelId, listOf(entry))
                val stillThere = failedChapterDao.getById(failedId)
                if (stillThere != null) {
                    _errorEvents.emit(context.getString(R.string.failed_chapters_retry_no_match))
                } else {
                    _errorEvents.emit(context.getString(R.string.failed_chapters_retry_success))
                }
                refreshFailedChapters(failed.novelId)
            } catch (e: Exception) {
                _errorEvents.emit(
                    context.getString(R.string.failed_chapters_retry_failed, e.message ?: "Erro")
                )
            }
        }
    }

    fun dismissWhatsNew() {
        _showWhatsNew.value = false
        viewModelScope.launch {
            chapterDao.clearAllNewFlags()
        }
    }

    fun dismissFailedChapter(failedId: Long) {
        viewModelScope.launch {
            val failed = failedChapterDao.getById(failedId) ?: return@launch
            failedChapterDao.deleteById(failedId)
            refreshFailedChapters(failed.novelId)
        }
    }

    fun retryAllFailedChapters(novelId: Long) {
        viewModelScope.launch {
            val allFailed = failedChapterDao.getByNovel(novelId)
            val failed = allFailed.filter { !it.url.isNullOrBlank() }
            val skippedNoUrl = allFailed.size - failed.size
            Log.w("ImportRetry", "retryAllFailedChapters novelId=$novelId failed=${allFailed.size} retryable=${failed.size} skippedNoUrl=$skippedNoUrl")
            if (failed.isEmpty()) return@launch
            val novel = novelDao.getNovelById(novelId) ?: return@launch
            val links = failed.mapNotNull { f ->
                val url = f.url ?: return@mapNotNull null
                ChapterLink(
                    title = f.title,
                    url = url,
                    chapterNumber = f.chapterNumber.takeIf { it != Int.MAX_VALUE } ?: Int.MAX_VALUE
                )
            }
            if (links.isEmpty()) return@launch
            notificationPermissionCoordinator.onUserInitiatedBackgroundImport()
            backgroundImportManager.startImport(
                novelTitle = novel.title,
                links = links,
                coverUrl = null,
                sourceUrl = novel.sourceUrl,
                targetNovelId = novelId
            )
            _updateCheckResult.emit(context.getString(R.string.retry_all_failed_started, links.size))
        }
    }

    fun scanMissingChapters(novelId: Long) {
        viewModelScope.launch {
            val result = scanMissingChaptersUseCase.scanWeb(novelId)
            handleScanResult(novelId, result)
        }
    }

    fun scanMissingChaptersLocal(novelId: Long, from: Int, to: Int) {
        viewModelScope.launch {
            val result = scanMissingChaptersUseCase.scanLocal(novelId, from, to)
            handleScanResult(novelId, result)
        }
    }

    private suspend fun handleScanResult(novelId: Long, result: Result<ScanResult>) {
        refreshFailedChapters(novelId)
        result.fold(
            onSuccess = { scan ->
                _errorEvents.emit(context.getString(R.string.failed_chapters_scan_result, scan.total))
            },
            onFailure = { e ->
                _errorEvents.emit(e.message ?: context.getString(R.string.failed_chapters_scan_invalid_range))
            }
        )
    }

    companion object {
        const val ARG_SELECTED_NOVEL_ID = "selectedNovelId"
        const val ARG_SHOW_FAILED = "showFailed"
    }
}
