package com.novelreader.ui.favorites

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.repository.BookmarkRepository
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val novelRepo: NovelRepository = mockk(relaxed = true)
    private val chapterRepo: ChapterRepository = mockk(relaxed = true)
    private val bookmarkRepo: BookmarkRepository = mockk(relaxed = true)

    private lateinit var viewModel: FavoritesViewModel

    private val novel = NovelEntity(id = 1, title = "The Lost Kingdom", totalChapters = 10)
    private val chapter = ChapterEntity(id = 10, novelId = 1, title = "The Beginning", fileName = "ch1.html", orderIndex = 0, content = "Story starts")
    private val bookmark = BookmarkEntity(id = 100, chapterId = 10, title = "Great quote", page = 5, note = "Amazing description")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsDisplayItems() = runTest {
        every { bookmarkRepo.getAll() } returns flowOf(listOf(bookmark))
        coEvery { chapterRepo.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
        coEvery { novelRepo.getNovelsByIds(listOf(1L)) } returns listOf(novel)

        viewModel = FavoritesViewModel(novelRepo, chapterRepo, bookmarkRepo)

        viewModel.displayItems.test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items[0].bookmark).isEqualTo(bookmark)
            assertThat(items[0].chapter).isEqualTo(chapter)
            assertThat(items[0].novel).isEqualTo(novel)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun deleteBookmark_updatesList() = runTest {
        val bookmarkFlow = MutableStateFlow(listOf(bookmark))
        every { bookmarkRepo.getAll() } returns bookmarkFlow
        coEvery { chapterRepo.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
        coEvery { novelRepo.getNovelsByIds(listOf(1L)) } returns listOf(novel)
        coEvery { bookmarkRepo.deleteById(100) } answers {
            bookmarkFlow.value = emptyList()
        }

        viewModel = FavoritesViewModel(novelRepo, chapterRepo, bookmarkRepo)

        viewModel.displayItems.test {
            awaitItem()
            viewModel.deleteBookmark(100)
            val updatedItems = awaitItem()
            assertThat(updatedItems).isEmpty()
            cancelAndConsumeRemainingEvents()
        }
        coVerify { bookmarkRepo.deleteById(100) }
    }

    @Test
    fun emptyBookmarks_showsEmptyList() = runTest {
        every { bookmarkRepo.getAll() } returns flowOf(emptyList())

        viewModel = FavoritesViewModel(novelRepo, chapterRepo, bookmarkRepo)

        viewModel.displayItems.test {
            val items = awaitItem()
            assertThat(items).isEmpty()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun joinsChapterAndNovel() = runTest {
        every { bookmarkRepo.getAll() } returns flowOf(listOf(bookmark))
        coEvery { chapterRepo.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
        coEvery { novelRepo.getNovelsByIds(listOf(1L)) } returns listOf(novel)

        viewModel = FavoritesViewModel(novelRepo, chapterRepo, bookmarkRepo)

        viewModel.displayItems.test {
            val item = awaitItem().first()
            assertThat(item.chapter!!.title).isEqualTo("The Beginning")
            assertThat(item.novel!!.title).isEqualTo("The Lost Kingdom")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun missingChapter_handledGracefully() = runTest {
        val orphanBookmark = BookmarkEntity(id = 200, chapterId = 9999, title = "Orphan bookmark", page = 1)
        every { bookmarkRepo.getAll() } returns flowOf(listOf(orphanBookmark))
        coEvery { chapterRepo.getChaptersByIds(listOf(9999L)) } returns emptyList()

        viewModel = FavoritesViewModel(novelRepo, chapterRepo, bookmarkRepo)

        viewModel.displayItems.test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items[0].chapter).isNull()
            assertThat(items[0].novel).isNull()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun multipleBookmarks_correctOrder() = runTest {
        val b1 = BookmarkEntity(id = 1, chapterId = 10, title = "First", page = 1, createdAt = 1000)
        val b2 = BookmarkEntity(id = 2, chapterId = 10, title = "Second", page = 2, createdAt = 2000)
        val b3 = BookmarkEntity(id = 3, chapterId = 10, title = "Third", page = 3, createdAt = 3000)

        every { bookmarkRepo.getAll() } returns flowOf(listOf(b1, b2, b3))
        coEvery { chapterRepo.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
        coEvery { novelRepo.getNovelsByIds(listOf(1L)) } returns listOf(novel)

        viewModel = FavoritesViewModel(novelRepo, chapterRepo, bookmarkRepo)

        viewModel.displayItems.test {
            val items = awaitItem()
            assertThat(items).hasSize(3)
            assertThat(items[0].bookmark.id).isEqualTo(1)
            assertThat(items[1].bookmark.id).isEqualTo(2)
            assertThat(items[2].bookmark.id).isEqualTo(3)
            cancelAndConsumeRemainingEvents()
        }
    }
}
