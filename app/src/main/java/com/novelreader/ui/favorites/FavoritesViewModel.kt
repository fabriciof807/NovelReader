package com.novelreader.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.repository.BookmarkRepository
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkDisplayItem(
    val bookmark: BookmarkEntity,
    val chapter: ChapterEntity?,
    val novel: NovelEntity?
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _displayItems = MutableStateFlow<List<BookmarkDisplayItem>>(emptyList())
    val displayItems: StateFlow<List<BookmarkDisplayItem>> = _displayItems

    init {
        viewModelScope.launch {
            bookmarkRepository.getAll().collect { bookmarks ->
                if (bookmarks.isEmpty()) {
                    _displayItems.value = emptyList()
                    return@collect
                }
                val chapterIds = bookmarks.map { it.chapterId }.distinct()
                val chapters = chapterRepository.getChaptersByIds(chapterIds)
                    .associateBy { it.id }
                val novelIds = chapters.values.map { it.novelId }.distinct()
                val novels = novelRepository.getNovelsByIds(novelIds)
                    .associateBy { it.id }
                val items = bookmarks.map { bookmark ->
                    val chapter = chapters[bookmark.chapterId]
                    val novel = chapter?.let { novels[it.novelId] }
                    BookmarkDisplayItem(bookmark, chapter, novel)
                }
                _displayItems.value = items
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.deleteById(id)
        }
    }
}
