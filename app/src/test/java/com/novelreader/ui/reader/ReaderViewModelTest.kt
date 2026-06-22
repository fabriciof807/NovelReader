package com.novelreader.ui.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.FtsSearchService
import com.novelreader.data.local.preferences.ReaderPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val savedState = SavedStateHandle(mapOf("novelId" to 1L, "chapterId" to 10L))
    private val novelDao: NovelDao = mockk(relaxed = true)
    private val chapterDao: ChapterDao = mockk(relaxed = true)
    private val bookmarkDao: BookmarkDao = mockk(relaxed = true)
    private val readerPrefs: ReaderPreferences = mockk(relaxed = true)
    private val charDao: CharacterDao = mockk(relaxed = true)
    private val ftsSearchService: FtsSearchService = mockk(relaxed = true)

    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { readerPrefs.config } returns flowOf(com.novelreader.data.local.preferences.ReaderConfig())
        every { bookmarkDao.getByChapter(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ReaderViewModel(
        context = context,
        savedStateHandle = savedState,
        novelDao = novelDao,
        chapterDao = chapterDao,
        bookmarkDao = bookmarkDao,
        readerPreferences = readerPrefs,
        characterDao = charDao,
        ftsSearchService = ftsSearchService
    )

    @Test
    fun `loadChapter with missing chapter sets error in state`() = runTest {
        coEvery { chapterDao.getChapterById(10) } returns null
        viewModel = createViewModel()

        viewModel.state.test {
            val loaded = awaitItem()
            assertThat(loaded.error).isNotNull()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `loadChapter with valid chapter populates state`() = runTest {
        val chapter = ChapterEntity(
            id = 10, novelId = 1, title = "Ch1",
            fileName = "ch1.html", orderIndex = 0, content = "<p>hi</p>"
        )
        coEvery { chapterDao.getChapterById(10) } returns chapter
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
        coEvery { novelDao.getNovelById(1) } returns null
        coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
        coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

        viewModel = createViewModel()

        viewModel.state.test {
            val loaded = awaitItem()
            assertThat(loaded.chapter).isEqualTo(chapter)
            assertThat(loaded.isLoading).isFalse()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `addBookmark with dao error emits error event`() = runTest {
        val chapter = ChapterEntity(
            id = 10, novelId = 1, title = "Ch1",
            fileName = "ch1.html", orderIndex = 0, content = "<p>x</p>"
        )
        coEvery { chapterDao.getChapterById(10) } returns chapter
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
        coEvery { novelDao.getNovelById(1) } returns null
        coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
        coEvery { chapterDao.markAsRead(any(), any()) } returns Unit
        coEvery { bookmarkDao.insert(any()) } throws RuntimeException("insert failed")

        viewModel = createViewModel()
        viewModel.showBookmarkDialog()

        viewModel.errorEvents.test {
            viewModel.addBookmark("title", "note")
            val msg = awaitItem()
            assertThat(msg).contains("insert failed")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onSearchQueryChange with short query clears results`() = runTest {
        viewModel = createViewModel()
        viewModel.onSearchQueryChange("ab")
        assertThat(viewModel.state.value.searchResults).isEmpty()
    }
}
