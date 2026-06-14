package com.novelreader.ui.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.R
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.preferences.ReaderConfig
import com.novelreader.data.local.preferences.ReaderPreferences
import com.novelreader.data.repository.BookmarkRepository
import com.novelreader.data.repository.CharacterRepository
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderState(
    val novel: NovelEntity? = null,
    val chapter: ChapterEntity? = null,
    val prevChapterId: Long? = null,
    val nextChapterId: Long? = null,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showBookmarkDialog: Boolean = false,
    val showSettings: Boolean = false,
    val config: ReaderConfig = ReaderConfig(),
    val reloadVersion: Int = 0,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ChapterEntity> = emptyList(),
    val selectedText: String = ""
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val readerPreferences: ReaderPreferences,
    private val characterRepository: CharacterRepository
) : ViewModel() {

    private val novelId: Long = savedStateHandle["novelId"] ?: 0L
    private val chapterId: Long = savedStateHandle["chapterId"] ?: 0L

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state

    private val _errorEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var currentChapter: ChapterEntity? = null
    private var allChapters: List<ChapterEntity> = emptyList()
    private var bookmarkCollectionJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            readerPreferences.config.collect { config ->
                _state.value = _state.value.copy(config = config)
            }
        }
        loadChapter(chapterId)
    }

    fun loadChapter(chapterId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val chapter = chapterRepository.getChapterById(chapterId)
            if (chapter == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = context.getString(R.string.reader_chapter_not_found)
                )
                return@launch
            }
            currentChapter = chapter
            val novel = novelRepository.getNovelById(chapter.novelId)

            if (allChapters.isEmpty() || allChapters.firstOrNull()?.novelId != chapter.novelId) {
                allChapters = chapterRepository.getChaptersByNovelSync(chapter.novelId)
            }

            val currentIndex = allChapters.indexOfFirst { it.id == chapterId }
            val prevId = allChapters.getOrNull(currentIndex - 1)?.id
            val nextId = allChapters.getOrNull(currentIndex + 1)?.id

            _state.value = _state.value.copy(
                novel = novel,
                chapter = chapter,
                prevChapterId = prevId,
                nextChapterId = nextId,
                isLoading = false
            )

            collectBookmarks(chapterId)

            novelRepository.updateLastRead(chapter.novelId, chapterId)

            if (!chapter.isRead) {
                chapterRepository.markAsRead(chapter.id, chapter.lastScrollPosition)
            }
        }
    }

    private fun collectBookmarks(chapterId: Long) {
        bookmarkCollectionJob?.cancel()
        bookmarkCollectionJob = viewModelScope.launch {
            bookmarkRepository.getByChapter(chapterId).collect { list ->
                _state.value = _state.value.copy(
                    bookmarks = list,
                    reloadVersion = _state.value.reloadVersion + 1
                )
            }
        }
    }

    fun goToPrevChapter() {
        _state.value.prevChapterId?.let { loadChapter(it) }
    }

    fun goToNextChapter() {
        _state.value.nextChapterId?.let { loadChapter(it) }
    }

    private var lastKnownScrollPosition: Int = 0

    fun getDefaultBookmarkTitle(): String {
        val chapter = currentChapter ?: return ""
        val content = chapter.content
        if (content.isBlank()) return ""
        val doc = org.jsoup.Jsoup.parseBodyFragment(content)
        val paragraphs = doc.select("p")
        if (paragraphs.isEmpty()) return ""
        val scrollPos = if (lastKnownScrollPosition > 0) lastKnownScrollPosition
            else chapter.lastScrollPosition
        if (scrollPos <= 0) return ""
        val index = ((scrollPos / 1000f) * paragraphs.size).toInt()
            .coerceIn(0, paragraphs.size - 1)
        return paragraphs[index].text().take(120)
    }

    fun saveScrollPosition(scrollRatio: Float) {
        val chapter = currentChapter ?: return
        val position = (scrollRatio * 1000).toInt()
        lastKnownScrollPosition = position
        viewModelScope.launch {
            try {
                chapterRepository.markAsRead(chapter.id, position)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun showBookmarkDialog() {
        _state.value = _state.value.copy(showBookmarkDialog = true)
    }

    fun hideBookmarkDialog() {
        _state.value = _state.value.copy(showBookmarkDialog = false)
    }

    fun addBookmark(title: String, note: String) {
        val chapter = currentChapter ?: return
        viewModelScope.launch {
            try {
                val scrollPos = if (lastKnownScrollPosition > 0) lastKnownScrollPosition
                    else chapter.lastScrollPosition
                val finalTitle = title.ifBlank { getDefaultBookmarkTitle() }
                    .ifBlank { "Bookmark #${_state.value.bookmarks.size + 1}" }
                bookmarkRepository.insert(
                    BookmarkEntity(
                        chapterId = chapter.id,
                        title = finalTitle,
                        note = note.ifBlank { null },
                        scrollPosition = scrollPos
                    )
                )
                _state.value = _state.value.copy(showBookmarkDialog = false)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            try {
                bookmarkRepository.deleteById(id)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun getScrollRatio(): Float {
        val chapter = currentChapter ?: return 0f
        return if (chapter.lastScrollPosition > 0) {
            chapter.lastScrollPosition / 1000f
        } else 0f
    }

    fun showSettings() {
        _state.value = _state.value.copy(showSettings = true)
    }

    fun hideSettings() {
        _state.value = _state.value.copy(showSettings = false)
    }

    fun updateTheme(theme: String) {
        viewModelScope.launch {
            readerPreferences.updateTheme(theme)
            _state.value = _state.value.copy(
                reloadVersion = _state.value.reloadVersion + 1
            )
        }
    }

    fun updateFontSize(size: Int) {
        viewModelScope.launch {
            readerPreferences.updateFontSize(size)
            _state.value = _state.value.copy(
                reloadVersion = _state.value.reloadVersion + 1
            )
        }
    }

    fun updateLineHeight(height: Float) {
        viewModelScope.launch {
            readerPreferences.updateLineHeight(height)
            _state.value = _state.value.copy(
                reloadVersion = _state.value.reloadVersion + 1
            )
        }
    }

    private var searchJob: Job? = null

    fun activateSearch() {
        _state.value = _state.value.copy(isSearchActive = true)
    }

    fun deactivateSearch() {
        searchJob?.cancel()
        _state.value = _state.value.copy(
            isSearchActive = false,
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.length < 3) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val results = chapterRepository.searchInNovel(novelId, query)
            _state.value = _state.value.copy(searchResults = results)
        }
    }

    fun onTextSelected(text: String) {
        _state.value = _state.value.copy(selectedText = text)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedText = "")
    }

    fun createCharacter(name: String, photoPath: String?) {
        viewModelScope.launch {
            try {
                characterRepository.insert(
                    CharacterEntity(
                        novelId = novelId,
                        name = name,
                        photoPath = photoPath
                    )
                )
                _state.value = _state.value.copy(selectedText = "")
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }
}
