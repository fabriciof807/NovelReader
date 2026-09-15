package com.novelreader.ui.reader

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.R
import com.novelreader.data.local.db.FtsSearchService
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.preferences.ReaderConfig
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.local.preferences.ReaderPreferences
import com.novelreader.data.local.preferences.SavedTheme
import com.novelreader.data.storage.WallpaperStorage
import com.novelreader.domain.usecase.VisualThemeUseCase
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.domain.usecase.ReimportChapterContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderState(
    val novel: NovelEntity? = null,
    val chapter: ChapterEntity? = null,
    val prevChapterId: Long? = null,
    val nextChapterId: Long? = null,
    val allChapters: List<ChapterEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isEmpty: Boolean = false,
    val showBookmarkDialog: Boolean = false,
    val showSettings: Boolean = false,
    val config: ReaderConfig = ReaderConfig(),
    val themeSelection: String = ReaderTheme.DEFAULT,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ChapterEntity> = emptyList()
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val readerPreferences: ReaderPreferences,
    private val wallpaperStorage: WallpaperStorage,
    private val visualThemeUseCase: VisualThemeUseCase,
    private val appPreferences: AppPreferences,
    private val ftsSearchService: FtsSearchService,
    private val reimportChapterContentUseCase: ReimportChapterContentUseCase
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
    private var loadChapterJob: Job? = null
    private var loadRequestId: Long = 0

    private var retryAction: (() -> Unit)? = null

    private val _retryAvailable = MutableStateFlow(false)
    val retryAvailable: StateFlow<Boolean> = _retryAvailable

    fun retryLastFailedAction() {
        retryAction?.invoke()
    }

    companion object {
        private const val emptyChapterThreshold = 200
        private const val keyLoadedChapterId = "loadedChapterId"
    }

    init {
        viewModelScope.launch {
            combine(
                readerPreferences.config,
                appPreferences.appTheme,
                appPreferences.appPalette
            ) { config, appTheme, appPalette -> Triple(config, appTheme, appPalette) }
                .collect { (config, appTheme, appPalette) ->
                    val (palette, dark) = ReaderTheme.resolve(
                        storedTheme = config.theme,
                        appPalette = appPalette,
                        appDark = isAppDark(appTheme)
                    )
                    _state.value = _state.value.copy(
                        config = config.copy(theme = palette, themeDark = dark),
                        themeSelection = config.theme
                    )
                }
        }
        loadChapter(savedStateHandle.get<Long>(keyLoadedChapterId) ?: chapterId)
    }

    private fun isAppDark(appTheme: String): Boolean = when (appTheme) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark()
    }

    private fun isSystemDark(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun loadChapter(chapterId: Long, restorePosition: Boolean = true) {
        loadChapterJob?.cancel()
        val requestId = ++loadRequestId
        loadChapterJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val chapter = chapterDao.getChapterById(chapterId) ?: run {
                if (requestId == loadRequestId) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.reader_chapter_not_found)
                    )
                }
                return@launch
            }
            val novel = novelDao.getNovelById(chapter.novelId)
            val chaptersForNovel = if (
                allChapters.isEmpty() || allChapters.firstOrNull()?.novelId != chapter.novelId
            ) {
                chapterDao.getChaptersByNovelSync(chapter.novelId)
            } else {
                allChapters
            }
            if (requestId != loadRequestId) return@launch

            allChapters = chaptersForNovel
            currentChapter = chapter
            savedStateHandle[keyLoadedChapterId] = chapter.id
            lastKnownScrollPosition = if (restorePosition) chapter.lastScrollPosition else 0
            val currentIndex = allChapters.indexOfFirst { it.id == chapterId }
            val prevId = allChapters.getOrNull(currentIndex - 1)?.id
            val nextId = allChapters.getOrNull(currentIndex + 1)?.id
            val isEmpty = chapter.content.isBlank() || chapter.content.length < emptyChapterThreshold

            _state.value = _state.value.copy(
                novel = novel,
                chapter = chapter,
                prevChapterId = prevId,
                nextChapterId = nextId,
                isLoading = false,
                error = null,
                isEmpty = isEmpty,
                allChapters = allChapters
            )
            collectBookmarks(chapterId)
            novelDao.updateLastRead(chapter.novelId, chapterId)
            if (!chapter.isRead) {
                chapterDao.markAsRead(chapter.id, chapter.lastScrollPosition)
            }
        }
    }

    private fun collectBookmarks(chapterId: Long) {
        bookmarkCollectionJob?.cancel()
        bookmarkCollectionJob = viewModelScope.launch {
            bookmarkDao.getByChapter(chapterId).collect { list ->
                _state.value = _state.value.copy(bookmarks = list)
            }
        }
    }

    fun goToPrevChapter() = _state.value.prevChapterId?.let { loadChapter(it, true) }

    fun goToNextChapter() = _state.value.nextChapterId?.let { loadChapter(it, false) }

    private var lastKnownScrollPosition: Int = 0

    fun updateLiveScroll(ratio: Float) {
        lastKnownScrollPosition = (ratio * 1000).toInt()
    }

    fun saveScrollPosition(ratio: Float? = null) {
        val chapter = currentChapter ?: return
        val position = if (ratio != null) (ratio * 1000).toInt() else lastKnownScrollPosition
        if (ratio != null) lastKnownScrollPosition = position
        viewModelScope.launch {
            try {
                chapterDao.markAsRead(chapter.id, position)
            } catch (e: Exception) {
                retryAction = {
                    val pos = lastKnownScrollPosition
                    viewModelScope.launch {
                        try {
                            chapterDao.markAsRead(chapter.id, pos)
                            retryAction = null
                            _retryAvailable.value = false
                        } catch (e2: Exception) {
                            _errorEvents.tryEmit(context.getString(R.string.reader_action_failed, e2.message ?: "Erro"))
                        }
                    }
                }
                _retryAvailable.value = true
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun onReaderPaused() {
        saveScrollPosition()
    }

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
                bookmarkDao.insert(
                    BookmarkEntity(
                        chapterId = chapter.id,
                        title = finalTitle,
                        note = note.ifBlank { null },
                        scrollPosition = scrollPos
                    )
                )
                _state.value = _state.value.copy(showBookmarkDialog = false)
                retryAction = null
                _retryAvailable.value = false
            } catch (e: Exception) {
                retryAction = { viewModelScope.launch { addBookmark(title, note) } }
                _retryAvailable.value = true
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            try {
                bookmarkDao.deleteById(id)
                retryAction = null
                _retryAvailable.value = false
            } catch (e: Exception) {
                retryAction = { viewModelScope.launch { deleteBookmark(id) } }
                _retryAvailable.value = true
                _errorEvents.emit(e.message ?: e.toString())
            }
        }
    }

    fun getScrollRatio(): Float {
        if (currentChapter == null) return 0f
        return lastKnownScrollPosition.coerceIn(0, 1000) / 1000f
    }

    fun showSettings() {
        _state.value = _state.value.copy(showSettings = true)
    }

    fun hideSettings() {
        _state.value = _state.value.copy(showSettings = false)
    }

    fun updateTheme(theme: String) {
        viewModelScope.launch { readerPreferences.updateTheme(theme) }
    }

    fun updateAccentColor(color: String?) {
        viewModelScope.launch { readerPreferences.updateAccentColor(color) }
    }

    fun updateWallpaper(ref: String) {
        viewModelScope.launch { readerPreferences.updateWallpaper(ref) }
    }

    fun updateWallpaperBlur(value: Int) {
        viewModelScope.launch { readerPreferences.updateWallpaperBlur(value) }
    }

    fun updateVeil(value: Int) {
        viewModelScope.launch { readerPreferences.updateVeil(value) }
    }

    fun importWallpaper(uri: Uri) {
        viewModelScope.launch {
            wallpaperStorage.importFromUri(WallpaperStorage.SLOT_READER, uri)
                ?.let { readerPreferences.updateWallpaper(it) }
        }
    }

    val savedThemes: StateFlow<List<SavedTheme>> = appPreferences.savedThemes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveTheme(name: String) {
        viewModelScope.launch { visualThemeUseCase.saveCurrent(name) }
    }

    fun applyTheme(theme: SavedTheme) {
        viewModelScope.launch { visualThemeUseCase.apply(theme) }
    }

    fun deleteTheme(theme: SavedTheme) {
        viewModelScope.launch { visualThemeUseCase.delete(theme.name) }
    }

    fun resetAppearance() {
        viewModelScope.launch { visualThemeUseCase.resetToDefaults() }
    }

    fun removeWallpaper() {
        viewModelScope.launch {
            wallpaperStorage.clearSlot(WallpaperStorage.SLOT_READER)
            readerPreferences.updateWallpaper(PreferenceAllowlists.WALLPAPER_NONE)
        }
    }

    fun updateFontSize(size: Int) {
        viewModelScope.launch { readerPreferences.updateFontSize(size) }
    }

    fun updateLineHeight(height: Float) {
        viewModelScope.launch { readerPreferences.updateLineHeight(height) }
    }

    fun updateAutoScrollSpeed(speed: Float) {
        viewModelScope.launch { readerPreferences.updateAutoScrollSpeed(speed) }
    }

    fun updateKeepScreenOn(value: Boolean) {
        viewModelScope.launch { readerPreferences.updateKeepScreenOn(value) }
    }

    fun updateSwipeDirection(direction: String) {
        viewModelScope.launch { readerPreferences.updateSwipeDirection(direction) }
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
            val results = ftsSearchService.searchInNovel(novelId, query)
            _state.value = _state.value.copy(searchResults = results)
        }
    }

    fun importMhtForChapter(uri: Uri) {
        viewModelScope.launch {
            val chapter = _state.value.chapter ?: return@launch
            val result = reimportChapterContentUseCase.importFile(chapter.id, chapter.novelId, uri)
            if (result.isSuccess) {
                loadChapter(chapter.id)
            } else {
                val msg = context.getString(R.string.empty_chapter_import_failed, result.exceptionOrNull()?.message ?: "Erro")
                _errorEvents.emit(msg)
            }
        }
    }
}
