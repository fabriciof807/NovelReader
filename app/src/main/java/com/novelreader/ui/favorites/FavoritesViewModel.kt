package com.novelreader.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkDisplayItem(
    val bookmark: BookmarkEntity,
    val chapter: ChapterEntity?,
    val novel: NovelEntity?
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val displayItems: StateFlow<List<BookmarkDisplayItem>> = combine(
        bookmarkDao.getAll(),
        _searchQuery
    ) { bookmarks, query ->
        buildDisplayItems(bookmarks, query)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            try {
                bookmarkDao.deleteById(id)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun buildDisplayItems(
        bookmarks: List<BookmarkEntity>,
        query: String
    ): List<BookmarkDisplayItem> {
        if (bookmarks.isEmpty()) return emptyList()
        val chapterIds = bookmarks.map { it.chapterId }.distinct()
        val chapters = chapterDao.getChaptersByIds(chapterIds).associateBy { it.id }
        val novelIds = chapters.values.map { it.novelId }.distinct()
        val novels = novelDao.getNovelsByIds(novelIds).associateBy { it.id }
        val needle = query.trim()
        return bookmarks
            .map { bookmark ->
                val chapter = chapters[bookmark.chapterId]
                val novel = chapter?.let { novels[it.novelId] }
                BookmarkDisplayItem(bookmark, chapter, novel)
            }
            .filter { item ->
                if (needle.isEmpty()) return@filter true
                val bTitle = item.bookmark.title
                val cTitle = item.chapter?.title ?: ""
                val bNote = item.bookmark.note ?: ""
                bTitle.contains(needle, ignoreCase = true) ||
                    cTitle.contains(needle, ignoreCase = true) ||
                    bNote.contains(needle, ignoreCase = true)
            }
    }
}
