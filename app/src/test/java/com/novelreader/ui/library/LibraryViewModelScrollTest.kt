package com.novelreader.ui.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.data.remote.MvlempyrCharacterImporter
import com.novelreader.data.worker.UpdateCheckScheduler
import com.novelreader.domain.usecase.BackgroundImportManager
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.domain.usecase.CharacterManagementUseCase
import com.novelreader.domain.usecase.CoverManagementUseCase
import com.novelreader.domain.usecase.RetryChapterUseCase
import com.novelreader.domain.usecase.ScanMissingChaptersUseCase
import com.novelreader.domain.usecase.WebImportUseCase
import com.novelreader.domain.usecase.importnovel.ChapterInserter
import com.novelreader.domain.usecase.importnovel.FileCharsetDetector
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryViewModelScrollTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val savedState = SavedStateHandle()
    private val novelDao: NovelDao = mockk(relaxed = true)
    private val chapterDao: ChapterDao = mockk(relaxed = true)
    private val bookmarkDao: BookmarkDao = mockk(relaxed = true)
    private val bgManager: BackgroundImportManager = mockk(relaxed = true)
    private val prefs: LibraryPreferences = mockk(relaxed = true)
    private val charPhotoDao: CharacterPhotoDao = mockk(relaxed = true)
    private val charManagement: CharacterManagementUseCase = mockk(relaxed = true)
    private val coverManagement: CoverManagementUseCase = mockk(relaxed = true)
    private val importer: MvlempyrCharacterImporter = mockk(relaxed = true)
    private val updateCheckScheduler: UpdateCheckScheduler = mockk(relaxed = true)
    private val webImportUseCase: WebImportUseCase = mockk(relaxed = true)
    private val failedChapterDao: FailedChapterDao = mockk(relaxed = true)
    private val retryChapterUseCase: RetryChapterUseCase = mockk(relaxed = true)
    private val scanMissingChaptersUseCase: ScanMissingChaptersUseCase = mockk(relaxed = true)
    private val chapterInserter: ChapterInserter = mockk(relaxed = true)
    private val parserRegistry: ParserRegistry = mockk(relaxed = true)
    private val mhtParser: MhtParser = mockk(relaxed = true)
    private val fileCharsetDetector: FileCharsetDetector = mockk(relaxed = true)

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { novelDao.getAllNovels() } returns flowOf(emptyList())
        every { bookmarkDao.getAll() } returns flowOf(emptyList())
        every { prefs.sortOrder } returns flowOf("LAST_READ")
        every { prefs.chapterSortOrder } returns flowOf("ASCENDING")
        every { prefs.viewMode } returns flowOf("GRID")
        every { bgManager.state } returns MutableStateFlow(BackgroundImportState())
        viewModel = LibraryViewModel(
            context = context,
            savedStateHandle = savedState,
            novelDao = novelDao,
            chapterDao = chapterDao,
            bookmarkDao = bookmarkDao,
            backgroundImportManager = bgManager,
            libraryPreferences = prefs,
            characterManagementUseCase = charManagement,
            coverManagementUseCase = coverManagement,
            characterPhotoDao = charPhotoDao,
            mvlempyrCharacterImporter = importer,
            updateCheckScheduler = updateCheckScheduler,
            webImportUseCase = webImportUseCase,
            failedChapterDao = failedChapterDao,
            retryChapterUseCase = retryChapterUseCase,
            scanMissingChaptersUseCase = scanMissingChaptersUseCase,
            chapterInserter = chapterInserter,
            parserRegistry = parserRegistry,
            mhtParser = mhtParser,
            fileCharsetDetector = fileCharsetDetector,
            io = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getChaptersScroll returns null for novel that was never set`() = runTest {
        assertThat(viewModel.getChaptersScroll(1L)).isNull()
    }

    @Test
    fun `setChaptersScroll then getChaptersScroll returns saved values`() = runTest {
        viewModel.setChaptersScroll(novelId = 1L, index = 12, offset = 40)

        val saved = viewModel.getChaptersScroll(1L)
        assertThat(saved).isNotNull()
        assertThat(saved!!.firstVisibleItemIndex).isEqualTo(12)
        assertThat(saved.firstVisibleItemScrollOffset).isEqualTo(40)
    }

    @Test
    fun `getChaptersScroll returns null for a different novel id`() = runTest {
        viewModel.setChaptersScroll(novelId = 1L, index = 12, offset = 40)

        assertThat(viewModel.getChaptersScroll(2L)).isNull()
    }

    @Test
    fun `setChaptersScroll overwrites previous value for same novel`() = runTest {
        viewModel.setChaptersScroll(novelId = 1L, index = 5, offset = 10)
        viewModel.setChaptersScroll(novelId = 1L, index = 8, offset = 20)

        val saved = viewModel.getChaptersScroll(1L)
        assertThat(saved).isNotNull()
        assertThat(saved!!.firstVisibleItemIndex).isEqualTo(8)
        assertThat(saved.firstVisibleItemScrollOffset).isEqualTo(20)
    }

    @Test
    fun `chaptersScrollByNovel reflects all set novels`() = runTest {
        viewModel.setChaptersScroll(novelId = 1L, index = 12, offset = 40)
        viewModel.setChaptersScroll(novelId = 2L, index = 7, offset = 0)
        viewModel.setChaptersScroll(novelId = 3L, index = 0, offset = 50)

        val map = viewModel.chaptersScrollByNovel.value
        assertThat(map).hasSize(3)
        assertThat(map[1L]?.firstVisibleItemIndex).isEqualTo(12)
        assertThat(map[1L]?.firstVisibleItemScrollOffset).isEqualTo(40)
        assertThat(map[2L]?.firstVisibleItemIndex).isEqualTo(7)
        assertThat(map[3L]?.firstVisibleItemScrollOffset).isEqualTo(50)
    }

    @Test
    fun `scrollToFailedRequest is set when showFailed flag is true`() = runTest {
        val novel = NovelEntity(id = 1L, title = "Test", sourceUrl = "", coverPath = null)
        coEvery { novelDao.getNovelById(1L) } returns novel

        savedState[LibraryViewModel.ARG_SELECTED_NOVEL_ID] = 1L
        savedState[LibraryViewModel.ARG_SHOW_FAILED] = true
        val vm = LibraryViewModel(
            context = context,
            savedStateHandle = savedState,
            novelDao = novelDao,
            chapterDao = chapterDao,
            bookmarkDao = bookmarkDao,
            backgroundImportManager = bgManager,
            libraryPreferences = prefs,
            characterManagementUseCase = charManagement,
            coverManagementUseCase = coverManagement,
            characterPhotoDao = charPhotoDao,
            mvlempyrCharacterImporter = importer,
            updateCheckScheduler = updateCheckScheduler,
            webImportUseCase = webImportUseCase,
            failedChapterDao = failedChapterDao,
            retryChapterUseCase = retryChapterUseCase,
            scanMissingChaptersUseCase = scanMissingChaptersUseCase,
            chapterInserter = chapterInserter,
            parserRegistry = parserRegistry,
            mhtParser = mhtParser,
            fileCharsetDetector = fileCharsetDetector,
            io = Dispatchers.Unconfined
        )

        assertThat(vm.scrollToFailedRequest.value).isEqualTo(1L)
    }

    @Test
    fun `consumeScrollToFailed resets scrollToFailedRequest to null`() = runTest {
        val novel = NovelEntity(id = 2L, title = "Test 2", sourceUrl = "", coverPath = null)
        coEvery { novelDao.getNovelById(2L) } returns novel

        savedState[LibraryViewModel.ARG_SELECTED_NOVEL_ID] = 2L
        savedState[LibraryViewModel.ARG_SHOW_FAILED] = true
        val vm = LibraryViewModel(
            context = context,
            savedStateHandle = savedState,
            novelDao = novelDao,
            chapterDao = chapterDao,
            bookmarkDao = bookmarkDao,
            backgroundImportManager = bgManager,
            libraryPreferences = prefs,
            characterManagementUseCase = charManagement,
            coverManagementUseCase = coverManagement,
            characterPhotoDao = charPhotoDao,
            mvlempyrCharacterImporter = importer,
            updateCheckScheduler = updateCheckScheduler,
            webImportUseCase = webImportUseCase,
            failedChapterDao = failedChapterDao,
            retryChapterUseCase = retryChapterUseCase,
            scanMissingChaptersUseCase = scanMissingChaptersUseCase,
            chapterInserter = chapterInserter,
            parserRegistry = parserRegistry,
            mhtParser = mhtParser,
            fileCharsetDetector = fileCharsetDetector,
            io = Dispatchers.Unconfined
        )

        vm.consumeScrollToFailed()
        assertThat(vm.scrollToFailedRequest.value).isNull()
    }
}
