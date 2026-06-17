package com.novelreader.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.R
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.remote.MvlempyrCharacterImporter
import com.novelreader.data.repository.BookmarkRepository
import com.novelreader.data.repository.CharacterPhotoRepository
import com.novelreader.data.repository.CharacterRepository
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.storage.CoverStorage
import com.novelreader.domain.usecase.BackgroundImportManager
import com.novelreader.domain.usecase.BackgroundImportState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { TITLE, CREATED_AT, LAST_READ }
enum class ChapterSortOrder { ASCENDING, DESCENDING }
enum class ViewMode { GRID, LIST }
enum class NovelFilter { ALL, READING, COMPLETED }

data class LibraryStats(
    val totalNovels: Int = 0,
    val totalChapters: Int = 0,
    val totalBookmarks: Int = 0
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val backgroundImportManager: BackgroundImportManager,
    private val libraryPreferences: LibraryPreferences,
    private val characterRepository: CharacterRepository,
    private val characterPhotoRepository: CharacterPhotoRepository,
    private val mvlempyrCharacterImporter: MvlempyrCharacterImporter,
    private val coverStorage: CoverStorage
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SortOrder.LAST_READ)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode

    val novels: StateFlow<List<NovelEntity>> = combine(
        novelRepository.getAllNovels(),
        _sortOrder
    ) { list, order ->
        when (order) {
            SortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
            SortOrder.CREATED_AT -> list.sortedByDescending { it.createdAt }
            SortOrder.LAST_READ -> list.sortedByDescending { it.lastReadAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<LibraryStats> = combine(
        novelRepository.getAllNovels(),
        bookmarkRepository.getAll()
    ) { novels, bookmarks ->
        LibraryStats(
            totalNovels = novels.size,
            totalChapters = novels.sumOf { it.totalChapters },
            totalBookmarks = bookmarks.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

    private val _showDeleteDialog = MutableStateFlow<NovelEntity?>(null)
    val showDeleteDialog: StateFlow<NovelEntity?> = _showDeleteDialog

    private val _showUrlDialog = MutableStateFlow<NovelEntity?>(null)
    val showUrlDialog: StateFlow<NovelEntity?> = _showUrlDialog

    private val _coverTargetNovel = MutableStateFlow<NovelEntity?>(null)
    val coverTargetNovel: StateFlow<NovelEntity?> = _coverTargetNovel

    private val _selectedNovel = MutableStateFlow<NovelEntity?>(null)
    val selectedNovel: StateFlow<NovelEntity?> = _selectedNovel

    private val _chapterSortOrder = MutableStateFlow(ChapterSortOrder.ASCENDING)
    val chapterSortOrder: StateFlow<ChapterSortOrder> = _chapterSortOrder

    private val _chapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val chapters: StateFlow<List<ChapterEntity>> = _chapters

    private val _bookmarkCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val bookmarkCounts: StateFlow<Map<Long, Int>> = _bookmarkCounts

    private val _characters = MutableStateFlow<List<CharacterEntity>>(emptyList())
    val characters: StateFlow<List<CharacterEntity>> = _characters

    private val _characterPhotos = MutableStateFlow<Map<Long, List<CharacterPhotoEntity>>>(emptyMap())
    val characterPhotos: StateFlow<Map<Long, List<CharacterPhotoEntity>>> = _characterPhotos

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _coverError = MutableStateFlow<String?>(null)
    val coverError: StateFlow<String?> = _coverError

    private val _isImportingCharacters = MutableStateFlow(false)
    val isImportingCharacters: StateFlow<Boolean> = _isImportingCharacters

    private val _characterImportResult = MutableStateFlow<String?>(null)
    val characterImportResult: StateFlow<String?> = _characterImportResult

    private val _errorEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    val backgroundImportState: StateFlow<BackgroundImportState> = backgroundImportManager.state

    val readProgress: StateFlow<Map<Long, Float>> = novelRepository.getAllNovels().map { novels ->
        val counts = chapterRepository.getReadCountPerNovel().associate { it.novelId to it.readCount }
        novels.associate { novel ->
            val read = counts[novel.id] ?: 0
            val progress = if (novel.totalChapters > 0) read.toFloat() / novel.totalChapters else 0f
            novel.id to progress
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            val saved = libraryPreferences.sortOrder.first()
            _sortOrder.value = try { SortOrder.valueOf(saved) } catch (_: Exception) { SortOrder.LAST_READ }
        }
        viewModelScope.launch {
            val saved = libraryPreferences.viewMode.first()
            _viewMode.value = try { ViewMode.valueOf(saved) } catch (_: Exception) { ViewMode.GRID }
        }
        viewModelScope.launch {
            bookmarkRepository.getAll().collect { bookmarks ->
                _bookmarkCounts.value = bookmarks.groupBy { it.chapterId }
                    .mapValues { it.value.size }
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        viewModelScope.launch {
            libraryPreferences.updateSortOrder(order.name)
        }
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        viewModelScope.launch {
            libraryPreferences.updateViewMode(mode.name)
        }
    }

    fun selectNovel(novel: NovelEntity) {
        _selectedNovel.value = novel
        _selectedTab.value = 1
        loadChapters(novel.id)
    }

    fun toggleChapterSortOrder() {
        _chapterSortOrder.value = when (_chapterSortOrder.value) {
            ChapterSortOrder.ASCENDING -> ChapterSortOrder.DESCENDING
            ChapterSortOrder.DESCENDING -> ChapterSortOrder.ASCENDING
        }
        _selectedNovel.value?.let { loadChapters(it.id) }
    }

    private fun loadChapters(novelId: Long) {
        viewModelScope.launch {
            val raw = chapterRepository.getChaptersByNovelSync(novelId)
            _chapters.value = when (_chapterSortOrder.value) {
                ChapterSortOrder.ASCENDING -> raw.sortedBy { it.orderIndex }
                ChapterSortOrder.DESCENDING -> raw.sortedByDescending { it.orderIndex }
            }
            val chars = characterRepository.getByNovelSync(novelId)
            _characters.value = chars
            val allPhotos = characterPhotoRepository.getByCharacterIds(chars.map { it.id })
            _characterPhotos.value = allPhotos.groupBy { it.characterId }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun requestDelete(novel: NovelEntity) {
        _showDeleteDialog.value = novel
    }

    fun cancelDelete() {
        _showDeleteDialog.value = null
    }

    fun confirmDelete() {
        val novel = _showDeleteDialog.value ?: return
        viewModelScope.launch {
            try {
                coverStorage.deleteCoverIfOwnedByApp(novel.id, novel.coverPath)
                coverStorage.deleteCharacterFolder(novel.id)
                novelRepository.deleteById(novel.id)
                if (_selectedNovel.value?.id == novel.id) {
                    _selectedNovel.value = null
                    _selectedTab.value = 0
                }
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            } finally {
                _showDeleteDialog.value = null
            }
        }
    }

    fun requestChangeCover(novel: NovelEntity) {
        _coverTargetNovel.value = novel
    }

    fun requestCoverByUrl(novel: NovelEntity) {
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
                _showUrlDialog.value = null
                return@launch
            }
            val path = coverStorage.saveFromUrl(novelId, url)
            if (path != null) {
                novelRepository.updateCoverPath(novelId, path)
                _coverError.value = null
            } else {
                _coverError.value = context.getString(R.string.cover_download_error)
            }
            _showUrlDialog.value = null
        }
    }

    fun clearCoverRequest() {
        _coverTargetNovel.value = null
    }

    fun clearCoverError() {
        _coverError.value = null
    }

    fun saveCover(novelId: Long, uri: android.net.Uri) {
        viewModelScope.launch {
            val path = coverStorage.saveFromUri(novelId, uri)
            if (path != null) {
                novelRepository.updateCoverPath(novelId, path)
                _coverError.value = null
            } else {
                _coverError.value = context.getString(R.string.cover_save_error)
            }
            _coverTargetNovel.value = null
        }
    }

    fun addCharacter(novelId: Long, name: String, photoPath: String?) {
        viewModelScope.launch {
            try {
                val id = characterRepository.insert(
                    CharacterEntity(
                        novelId = novelId,
                        name = name,
                        photoPath = photoPath
                    )
                )
                if (photoPath != null) {
                    characterPhotoRepository.insert(
                        CharacterPhotoEntity(
                            characterId = id,
                            photoPath = photoPath,
                            orderIndex = 0
                        )
                    )
                }
                refreshCharacters(novelId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun deleteCharacter(id: Long) {
        val novelId = _selectedNovel.value?.id ?: return
        viewModelScope.launch {
            try {
                characterRepository.deleteById(id)
                refreshCharacters(novelId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun updateCharacterPhoto(id: Long, path: String) {
        viewModelScope.launch {
            try {
                characterRepository.updatePhoto(id, path)
                val novelId = _selectedNovel.value?.id ?: return@launch
                refreshCharacters(novelId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun batchAddCharacterPhotos(characterId: Long, photoPaths: List<String>) {
        viewModelScope.launch {
            try {
                val existing = characterPhotoRepository.getByCharacterSync(characterId)
                var order = existing.size
                for (path in photoPaths) {
                    characterPhotoRepository.insert(
                        CharacterPhotoEntity(
                            characterId = characterId,
                            photoPath = path,
                            orderIndex = order
                        )
                    )
                    order++
                }
                if (existing.isEmpty() && photoPaths.isNotEmpty()) {
                    characterRepository.updatePhoto(characterId, photoPaths.first())
                }
                val novelId = _selectedNovel.value?.id ?: return@launch
                refreshCharacters(novelId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun addCharacterPhoto(characterId: Long, photoPath: String) {
        viewModelScope.launch {
            try {
                val existing = characterPhotoRepository.getByCharacterSync(characterId)
                characterPhotoRepository.insert(
                    CharacterPhotoEntity(
                        characterId = characterId,
                        photoPath = photoPath,
                        orderIndex = existing.size
                    )
                )
                if (existing.isEmpty()) {
                    characterRepository.updatePhoto(characterId, photoPath)
                }
                val novelId = _selectedNovel.value?.id ?: return@launch
                refreshCharacters(novelId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun deleteCharacterPhoto(photoId: Long, characterId: Long) {
        viewModelScope.launch {
            try {
                characterPhotoRepository.deleteById(photoId)
                val novelId = _selectedNovel.value?.id ?: return@launch
                val remaining = characterPhotoRepository.getByCharacterSync(characterId)
                if (remaining.isNotEmpty()) {
                    characterRepository.updatePhoto(characterId, remaining.first().photoPath)
                } else {
                    characterRepository.updatePhoto(characterId, "")
                }
                refreshCharacters(novelId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun updateCharacterName(characterId: Long, name: String) {
        viewModelScope.launch {
            try {
                characterRepository.updateName(characterId, name)
                val novelId = _selectedNovel.value?.id ?: return@launch
                refreshCharacters(novelId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun updateCharacterNotes(characterId: Long, notes: String?) {
        viewModelScope.launch {
            try {
                characterRepository.updateNotes(characterId, notes)
                val novelId = _selectedNovel.value?.id ?: return@launch
                refreshCharacters(novelId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun toggleCharacterFavorite(characterId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                characterRepository.toggleFavorite(characterId, isFavorite)
                val novelId = _selectedNovel.value?.id ?: return@launch
                refreshCharacters(novelId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun clearCharacterImportResult() {
        _characterImportResult.value = null
    }

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
        val chars = characterRepository.getByNovelSync(novelId)
        _characters.value = chars
        val allPhotos = characterPhotoRepository.getByCharacterIds(chars.map { it.id })
        _characterPhotos.value = allPhotos.groupBy { it.characterId }
    }

    fun toggleAutoUpdate(novelId: Long) {
        viewModelScope.launch {
            val novel = novelRepository.getNovelById(novelId) ?: return@launch
            novelRepository.updateAutoUpdate(novelId, !novel.autoUpdate)
        }
    }

    fun cancelBackgroundImport() {
        viewModelScope.launch { backgroundImportManager.cancel() }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { backgroundImportManager.cancel() }
    }
}
