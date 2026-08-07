package com.novelreader.ui.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.data.remote.MvlempyrCharacterImporter
import com.novelreader.data.worker.UpdateCheckScheduler
import com.novelreader.domain.usecase.BackgroundImportManager
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.CharacterManagementUseCase
import com.novelreader.domain.usecase.CoverManagementUseCase
import com.novelreader.domain.usecase.RetryChapterUseCase
import com.novelreader.domain.usecase.ScanMissingChaptersUseCase
import com.novelreader.domain.usecase.WebImportUseCase
import com.novelreader.domain.usecase.importnovel.ChapterInserter
import com.novelreader.domain.usecase.importnovel.FileCharsetDetector
import com.novelreader.ui.library.tabs.filterNovels
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
class LibraryViewModelTest {

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
            io = kotlinx.coroutines.Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectTab changes selectedTab state`() {
        viewModel.selectTab(2)
        assertThat(viewModel.selectedTab.value).isEqualTo(2)
    }

    @Test
    fun `requestDelete stores novel in showDeleteDialog`() {
        val novel = NovelEntity(id = 1, title = "Test")
        viewModel.requestDelete(novel)
        assertThat(viewModel.showDeleteDialog.value).isEqualTo(novel)
    }

    @Test
    fun `cancelDelete clears showDeleteDialog`() {
        val novel = NovelEntity(id = 1, title = "Test")
        viewModel.requestDelete(novel)
        viewModel.cancelDelete()
        assertThat(viewModel.showDeleteDialog.value).isNull()
    }

    @Test
    fun `setSortOrder updates state and persists to prefs`() = runTest {
        viewModel.setSortOrder(SortOrder.TITLE)
        assertThat(viewModel.sortOrder.value).isEqualTo(SortOrder.TITLE)
    }

    @Test
    fun `ToggleNovelFavorite intent updates novel favorite`() = runTest {
        viewModel.toggleNovelFavorite(42L, true)

        coVerify { novelDao.updateFavorite(42L, true) }
    }

    @Test
    fun `favorite filter keeps favorites while applying search`() {
        val novels = listOf(
            NovelEntity(id = 1, title = "Favorite Reader", isFavorite = true),
            NovelEntity(id = 2, title = "Other Reader", isFavorite = false),
            NovelEntity(id = 3, title = "Favorite Other", isFavorite = true)
        )

        val filtered = filterNovels(
            novels = novels,
            searchQuery = "reader",
            filterChip = NovelFilter.FAVORITES,
            readProgress = emptyMap()
        )

        assertThat(filtered).containsExactly(novels[0])
    }

    @Test
    fun `selectNovel switches to chapters tab and loads chapters`() = runTest {
        val novel = NovelEntity(id = 5, title = "Selected")
        coEvery { chapterDao.getChaptersByNovelSync(5) } returns listOf(
            ChapterEntity(id = 1, novelId = 5, title = "Ch1", fileName = "ch1.html", orderIndex = 0, content = "")
        )
        coEvery { charManagement.getCharacters(5) } returns emptyList()
        coEvery { charPhotoDao.getByCharacterIds(any()) } returns emptyList()

        viewModel.selectNovel(novel)

        assertThat(viewModel.selectedNovel.value).isEqualTo(novel)
        assertThat(viewModel.selectedTab.value).isEqualTo(1)
        assertThat(viewModel.chapters.value).hasSize(1)
    }

    @Test
    fun `DeselectNovel intent clears selectedNovel and resets tab`() = runTest {
        val novel = NovelEntity(id = 5, title = "Selected")
        coEvery { chapterDao.getChaptersByNovelSync(5) } returns emptyList()
        coEvery { charManagement.getCharacters(5) } returns emptyList()
        coEvery { charPhotoDao.getByCharacterIds(any()) } returns emptyList()

        viewModel.selectNovel(novel)
        assertThat(viewModel.selectedNovel.value).isEqualTo(novel)

        viewModel.deselectNovel()

        assertThat(viewModel.selectedNovel.value).isNull()
        assertThat(viewModel.selectedTab.value).isEqualTo(0)
    }

    @Test
    fun `confirmDelete with use case error emits error event`() = runTest {
        val novel = NovelEntity(id = 1, title = "Fail", coverPath = null)
        viewModel.requestDelete(novel)
        coEvery { coverManagement.deleteNovelCovers(any(), any()) } returns Result.failure(Exception("Delete failed"))

        viewModel.errorEvents.test {
            viewModel.confirmDelete()
            val message = awaitItem()
            assertThat(message).contains("Delete failed")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `confirmDelete clears showDeleteDialog in finally block`() = runTest {
        val novel = NovelEntity(id = 1, title = "X", coverPath = null)
        viewModel.requestDelete(novel)
        coEvery { coverManagement.deleteNovelCovers(any(), any()) } returns Result.success(Unit)

        viewModel.confirmDelete()
        assertThat(viewModel.showDeleteDialog.value).isNull()
    }

    @Test
    fun `RequestDelete intent sets showDeleteDialog when novels flow contains the id`() = runTest {
        val novel = NovelEntity(id = 7, title = "In Flow")
        val novelsFlow = MutableStateFlow(listOf(novel))
        every { novelDao.getAllNovels() } returns novelsFlow
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

        viewModel.novels.test {
            awaitItem()
            viewModel.requestDeleteById(7)
            assertThat(viewModel.showDeleteDialog.value).isEqualTo(novel)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `confirmDelete after RequestDelete intent calls cover use case and clears dialog`() = runTest {
        val novel = NovelEntity(id = 7, title = "In Flow", coverPath = null)
        val novelsFlow = MutableStateFlow(listOf(novel))
        every { novelDao.getAllNovels() } returns novelsFlow
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
        coEvery { coverManagement.deleteNovelCovers(7, null) } returns Result.success(Unit)

        viewModel.novels.test {
            awaitItem()
            viewModel.requestDeleteById(7)
            viewModel.confirmDelete()
            assertThat(viewModel.showDeleteDialog.value).isNull()
            coVerify { coverManagement.deleteNovelCovers(7, null) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `retryAllFailedChapters enqueues all failed with urls via startImport`() = runTest {
        val novel = NovelEntity(id = 11, title = "Stuck Novel", sourceUrl = "https://example.com/stuck")
        coEvery { novelDao.getNovelById(11) } returns novel
        coEvery { failedChapterDao.getByNovel(11) } returns listOf(
            FailedChapterEntity(
                id = 1,
                novelId = 11,
                title = "Ch 5",
                fileName = "ch5",
                url = "https://example.com/ch5.html",
                sourceType = "WEB",
                chapterNumber = 5,
                errorType = "network",
                errorMessage = "timeout"
            ),
            FailedChapterEntity(
                id = 2,
                novelId = 11,
                title = "Ch 6",
                fileName = "ch6",
                url = "https://example.com/ch6.html",
                sourceType = "WEB",
                chapterNumber = 6,
                errorType = "network",
                errorMessage = "timeout"
            )
        )

        viewModel.retryAllFailedChapters(11)

        coVerify {
            bgManager.startImport(
                novelTitle = "Stuck Novel",
                links = match { links ->
                    links.size == 2 &&
                        links[0].url == "https://example.com/ch5.html" &&
                        links[0].chapterNumber == 5 &&
                        links[1].url == "https://example.com/ch6.html" &&
                        links[1].chapterNumber == 6
                },
                coverUrl = null,
                sourceUrl = "https://example.com/stuck",
                targetNovelId = 11
            )
        }
    }

    @Test
    fun `retryAllFailedChapters skips failed with no url`() = runTest {
        val novel = NovelEntity(id = 12, title = "Mixed Failed", sourceUrl = "https://example.com/mixed")
        coEvery { novelDao.getNovelById(12) } returns novel
        coEvery { failedChapterDao.getByNovel(12) } returns listOf(
            FailedChapterEntity(
                id = 1,
                novelId = 12,
                title = "Ch 1",
                fileName = "ch1",
                url = "https://example.com/ch1.html",
                sourceType = "WEB",
                chapterNumber = 1,
                errorType = "network",
                errorMessage = "timeout"
            ),
            FailedChapterEntity(
                id = 2,
                novelId = 12,
                title = "Ch 2",
                fileName = "ch2",
                url = null,
                sourceType = "WEB",
                chapterNumber = 2,
                errorType = "parse",
                errorMessage = "manual"
            )
        )

        viewModel.retryAllFailedChapters(12)

        coVerify {
            bgManager.startImport(
                novelTitle = "Mixed Failed",
                links = match { it.size == 1 && it[0].url == "https://example.com/ch1.html" },
                coverUrl = null,
                sourceUrl = "https://example.com/mixed",
                targetNovelId = 12
            )
        }
    }

    @Test
    fun `cover URL with non-https does not close dialog`() = runTest {
        val novel = NovelEntity(id = 1, title = "Test")
        viewModel.requestCoverByUrl(novel)
        assertThat(viewModel.showUrlDialog.value).isEqualTo(novel)

        viewModel.saveCoverFromUrl(novel.id, "http://example.com/img.jpg")
        assertThat(viewModel.showUrlDialog.value).isEqualTo(novel)
    }

    @Test
    fun `clearCoverRequest clears coverTargetNovel`() = runTest {
        val novel = NovelEntity(id = 1, title = "Test")
        viewModel.requestChangeCover(novel)
        assertThat(viewModel.coverTargetNovel.value).isEqualTo(novel)

        viewModel.clearCoverRequest()
        assertThat(viewModel.coverTargetNovel.value).isNull()
    }
}
