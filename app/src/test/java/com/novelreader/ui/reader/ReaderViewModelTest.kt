package com.novelreader.ui.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.FtsSearchService
import com.novelreader.data.local.preferences.ReaderPreferences
import com.novelreader.domain.usecase.ReimportChapterContentUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
    private val ftsSearchService: FtsSearchService = mockk(relaxed = true)
    private val reimportChapterContentUseCase: ReimportChapterContentUseCase = mockk(relaxed = true)

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
        ftsSearchService = ftsSearchService,
        reimportChapterContentUseCase = reimportChapterContentUseCase
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

    @Test
    fun `updateLiveScroll does not write to chapterDao`() = runTest {
        val chapter = ChapterEntity(
            id = 10, novelId = 1, title = "Ch1",
            fileName = "ch1.html", orderIndex = 0, content = "<p>hi</p>",
            isRead = true
        )
        coEvery { chapterDao.getChapterById(10) } returns chapter
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
        coEvery { novelDao.getNovelById(1) } returns null
        coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
        coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

        viewModel = createViewModel()
        viewModel.updateLiveScroll(0.5f)

        coVerify(exactly = 0) { chapterDao.markAsRead(any(), any()) }
    }

    @Test
    fun `saveScrollPosition no-arg persists lastKnownScrollPosition via chapterDao`() = runTest {
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
        viewModel.updateLiveScroll(0.5f)
        viewModel.saveScrollPosition()

        coVerify { chapterDao.markAsRead(10, 500) }
    }

    @Test
    fun `loadChapter without restore starts at zero for the selected chapter`() = runTest {
        val chapterA = ChapterEntity(
            id = 10, novelId = 1, title = "Ch1",
            fileName = "ch1.html", orderIndex = 0, content = "<p>A</p>", isRead = true
        )
        val chapterB = ChapterEntity(
            id = 11, novelId = 1, title = "Ch2",
            fileName = "ch2.html", orderIndex = 1, content = "<p>B</p>",
            isRead = true, lastScrollPosition = 420
        )
        coEvery { chapterDao.getChapterById(10) } returns chapterA
        coEvery { chapterDao.getChapterById(11) } returns chapterB
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapterA, chapterB)
        coEvery { novelDao.getNovelById(1) } returns null
        coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
        coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

        viewModel = createViewModel()
        viewModel.updateLiveScroll(1f)
        viewModel.loadChapter(chapterB.id, restorePosition = false)
        assertThat(viewModel.getScrollRatio()).isEqualTo(0f)
        viewModel.saveScrollPosition()

        coVerify { chapterDao.markAsRead(chapterB.id, 0) }
    }

    @Test
    fun `goToPrevChapter restores the previous chapter position`() = runTest {
        savedState["chapterId"] = 12L
        val chapterB = ChapterEntity(
            id = 11, novelId = 1, title = "Ch2",
            fileName = "ch2.html", orderIndex = 0, content = "<p>B</p>",
            isRead = true, lastScrollPosition = 420
        )
        val chapterC = ChapterEntity(
            id = 12, novelId = 1, title = "Ch3",
            fileName = "ch3.html", orderIndex = 1, content = "<p>C</p>", isRead = true
        )
        coEvery { chapterDao.getChapterById(12) } returns chapterC
        coEvery { chapterDao.getChapterById(11) } returns chapterB
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapterB, chapterC)
        coEvery { novelDao.getNovelById(1) } returns null
        coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
        coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

        viewModel = createViewModel()
        viewModel.goToPrevChapter()

        assertThat(viewModel.getScrollRatio()).isEqualTo(0.42f)
    }

    @Test
    fun `loadChapter persists the loaded chapter id to savedStateHandle`() = runTest {
        val chapterA = ChapterEntity(
            id = 10, novelId = 1, title = "Ch1",
            fileName = "ch1.html", orderIndex = 0, content = "<p>A</p>", isRead = true
        )
        val chapterB = ChapterEntity(
            id = 11, novelId = 1, title = "Ch2",
            fileName = "ch2.html", orderIndex = 1, content = "<p>B</p>", isRead = true
        )
        coEvery { chapterDao.getChapterById(10) } returns chapterA
        coEvery { chapterDao.getChapterById(11) } returns chapterB
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapterA, chapterB)
        coEvery { novelDao.getNovelById(1) } returns null
        coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
        coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

        viewModel = createViewModel()
        viewModel.loadChapter(chapterB.id)

        assertThat(savedState.get<Long>("loadedChapterId")).isEqualTo(11L)
    }

    @Test
    fun `init loads the restored chapter id instead of the stale nav argument`() = runTest {
        savedState["chapterId"] = 39L
        savedState["loadedChapterId"] = 44L
        val staleChapter = ChapterEntity(
            id = 39, novelId = 1, title = "Ch39",
            fileName = "ch39.html", orderIndex = 0, content = "<p>39</p>", isRead = true
        )
        val restoredChapter = ChapterEntity(
            id = 44, novelId = 1, title = "Ch44",
            fileName = "ch44.html", orderIndex = 5, content = "<p>44</p>", isRead = true
        )
        coEvery { chapterDao.getChapterById(39) } returns staleChapter
        coEvery { chapterDao.getChapterById(44) } returns restoredChapter
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(staleChapter, restoredChapter)
        coEvery { novelDao.getNovelById(1) } returns null
        coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
        coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

        viewModel = createViewModel()

        assertThat(viewModel.state.value.chapter?.id).isEqualTo(44L)
        coVerify { novelDao.updateLastRead(1L, 44L, any()) }
        coVerify(exactly = 0) { novelDao.updateLastRead(1L, 39L, any()) }
    }

    @Test
    fun `latest load request wins when an older chapter response completes later`() = runTest {
        val chapterB = ChapterEntity(
            id = 11, novelId = 1, title = "Ch2",
            fileName = "ch2.html", orderIndex = 0, content = "<p>B</p>", isRead = true
        )
        val chapterC = ChapterEntity(
            id = 12, novelId = 1, title = "Ch3",
            fileName = "ch3.html", orderIndex = 1, content = "<p>C</p>", isRead = true
        )
        val chapterBResponse = CompletableDeferred<ChapterEntity?>()
        val chapterCResponse = CompletableDeferred<ChapterEntity?>()
        coEvery { chapterDao.getChapterById(10) } returns null
        coEvery { chapterDao.getChapterById(11) } coAnswers {
            withContext(NonCancellable) { chapterBResponse.await() }
        }
        coEvery { chapterDao.getChapterById(12) } coAnswers { chapterCResponse.await() }
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapterB, chapterC)
        coEvery { novelDao.getNovelById(1) } returns null
        coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
        coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

        viewModel = createViewModel()
        viewModel.loadChapter(chapterB.id)
        viewModel.loadChapter(chapterC.id)

        chapterCResponse.complete(chapterC)
        advanceUntilIdle()
        assertThat(viewModel.state.value.chapter).isEqualTo(chapterC)
        chapterBResponse.complete(chapterB)
        advanceUntilIdle()

        assertThat(viewModel.state.value.chapter).isEqualTo(chapterC)
    }

    @Test
    fun `updateTheme delegates to readerPreferences and does not bump state`() = runTest {
        viewModel = createViewModel()
        val before = viewModel.state.value
        viewModel.updateTheme("dark")
        val after = viewModel.state.value
        assertThat(after).isEqualTo(before)
        coVerify { readerPrefs.updateTheme("dark") }
    }

    @Test
    fun `updateFontSize delegates to readerPreferences and does not bump state`() = runTest {
        viewModel = createViewModel()
        val before = viewModel.state.value
        viewModel.updateFontSize(24)
        val after = viewModel.state.value
        assertThat(after).isEqualTo(before)
        coVerify { readerPrefs.updateFontSize(24) }
    }

    @Test
    fun `updateLineHeight delegates to readerPreferences and does not bump state`() = runTest {
        viewModel = createViewModel()
        val before = viewModel.state.value
        viewModel.updateLineHeight(2.0f)
        val after = viewModel.state.value
        assertThat(after).isEqualTo(before)
        coVerify { readerPrefs.updateLineHeight(2.0f) }
    }

    @Test
    fun `updateAutoScrollSpeed delegates to readerPreferences and does not bump state`() = runTest {
        viewModel = createViewModel()
        val before = viewModel.state.value
        viewModel.updateAutoScrollSpeed(1.5f)
        val after = viewModel.state.value
        assertThat(after).isEqualTo(before)
        coVerify { readerPrefs.updateAutoScrollSpeed(1.5f) }
    }

    @Test
    fun `updateSwipeDirection forwards value to readerPreferences`() = runTest {
        coEvery { chapterDao.getChapterById(10) } returns ChapterEntity(
            id = 10, novelId = 1L, title = "Ch1", fileName = "ch1.html",
            orderIndex = 1, content = "<p>x</p>"
        )
        coEvery { chapterDao.getChaptersByNovelSync(1L) } returns emptyList()
        viewModel = createViewModel()
        viewModel.updateSwipeDirection("horizontal")
        coVerify { readerPrefs.updateSwipeDirection("horizontal") }
    }

@Test
fun `loadChapter with blank content sets isEmpty in state`() = runTest {
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch1",
        fileName = "ch1.html", orderIndex = 0, content = ""
    )
    coEvery { chapterDao.getChapterById(any()) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

    viewModel = createViewModel()

    viewModel.state.test {
        val loaded = awaitItem()
        assertThat(loaded.isEmpty).isTrue()
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun `loadChapter with short content less than 200 chars sets isEmpty`() = runTest {
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch2",
        fileName = "ch2.html", orderIndex = 1, content = "<p>short</p>"
    )
    coEvery { chapterDao.getChapterById(any()) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

    viewModel = createViewModel()

    viewModel.state.test {
        val loaded = awaitItem()
        assertThat(loaded.isEmpty).isTrue()
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun `importMhtForChapter success calls useCase and reloads chapter`() = runTest {
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch1",
        fileName = "ch1.html", orderIndex = 0, content = ""
    )
    coEvery { chapterDao.getChapterById(any()) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

    viewModel = createViewModel()
    val filledContent = "<p>" + "x".repeat(200) + "</p>"
    val filledChapter = chapter.copy(content = filledContent)
    coEvery { chapterDao.getChapterById(any()) } returns filledChapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(filledChapter)
    coEvery { reimportChapterContentUseCase.importFile(10, 1, any()) } returns Result.success(Unit)

    viewModel.importMhtForChapter(mockk<Uri>())

    viewModel.state.test {
        val after = awaitItem()
        assertThat(after.isEmpty).isFalse()
        assertThat(after.chapter?.content).isEqualTo(filledContent)
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun `loadChapter with content over threshold sets isEmpty false`() = runTest {
    val content = "<p>" + "x".repeat(200) + "</p>"
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch3",
        fileName = "ch3.html", orderIndex = 2, content = content
    )
    coEvery { chapterDao.getChapterById(any()) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

    viewModel = createViewModel()

    viewModel.state.test {
        val loaded = awaitItem()
        assertThat(loaded.isEmpty).isFalse()
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun `updateKeepScreenOn calls readerPreferences updateKeepScreenOn`() = runTest {
    viewModel = createViewModel()
    viewModel.updateKeepScreenOn(false)
    coVerify { readerPrefs.updateKeepScreenOn(false) }
}

@Test
fun `addBookmark on failure sets retryAvailable true`() = runTest {
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch1",
        fileName = "ch1.html", orderIndex = 0, content = "<p>hi</p>"
    )
    coEvery { chapterDao.getChapterById(10) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit
    coEvery { bookmarkDao.insert(any()) } throws RuntimeException("fail")

    viewModel = createViewModel()
    viewModel.showBookmarkDialog()
    viewModel.addBookmark("title", "note")

    assertThat(viewModel.retryAvailable.value).isTrue()
}

@Test
fun `retryLastFailedAction re-runs addBookmark and clears retryAvailable on success`() = runTest {
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch1",
        fileName = "ch1.html", orderIndex = 0, content = "<p>hi</p>"
    )
    coEvery { chapterDao.getChapterById(10) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit
    coEvery { bookmarkDao.insert(any()) } throws RuntimeException("fail") andThen 1L

    viewModel = createViewModel()
    viewModel.showBookmarkDialog()
    viewModel.addBookmark("title", "note")
    assertThat(viewModel.retryAvailable.value).isTrue()

    viewModel.retryLastFailedAction()

    assertThat(viewModel.retryAvailable.value).isFalse()
}

@Test
fun `successful action clears retryAvailable`() = runTest {
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch1",
        fileName = "ch1.html", orderIndex = 0, content = "<p>hi</p>"
    )
    coEvery { chapterDao.getChapterById(10) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit
    coEvery { bookmarkDao.insert(any()) } returns 1L

    viewModel = createViewModel()
    viewModel.showBookmarkDialog()
    viewModel.addBookmark("title", "note")

    assertThat(viewModel.retryAvailable.value).isFalse()
}
}
