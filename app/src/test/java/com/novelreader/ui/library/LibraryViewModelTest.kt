package com.novelreader.ui.library

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.data.remote.MvlempyrCharacterImporter
import com.novelreader.data.repository.BookmarkRepository
import com.novelreader.data.repository.CharacterPhotoRepository
import com.novelreader.data.repository.CharacterRepository
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.storage.CoverStorage
import com.novelreader.domain.usecase.BackgroundImportManager
import com.novelreader.domain.usecase.BackgroundImportState
import io.mockk.coEvery
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
    private val novelRepo: NovelRepository = mockk(relaxed = true)
    private val chapterRepo: ChapterRepository = mockk(relaxed = true)
    private val bookmarkRepo: BookmarkRepository = mockk(relaxed = true)
    private val bgManager: BackgroundImportManager = mockk(relaxed = true)
    private val prefs: LibraryPreferences = mockk(relaxed = true)
    private val charRepo: CharacterRepository = mockk(relaxed = true)
    private val charPhotoRepo: CharacterPhotoRepository = mockk(relaxed = true)
    private val importer: MvlempyrCharacterImporter = mockk(relaxed = true)
    private val coverStorage: CoverStorage = mockk(relaxed = true)

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { novelRepo.getAllNovels() } returns flowOf(emptyList())
        every { bookmarkRepo.getAll() } returns flowOf(emptyList())
        every { prefs.sortOrder } returns flowOf("LAST_READ")
        every { prefs.viewMode } returns flowOf("GRID")
        every { bgManager.state } returns MutableStateFlow(BackgroundImportState())
        viewModel = LibraryViewModel(
            context = context,
            novelRepository = novelRepo,
            chapterRepository = chapterRepo,
            bookmarkRepository = bookmarkRepo,
            backgroundImportManager = bgManager,
            libraryPreferences = prefs,
            characterRepository = charRepo,
            characterPhotoRepository = charPhotoRepo,
            mvlempyrCharacterImporter = importer,
            coverStorage = coverStorage
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
    fun `selectNovel switches to chapters tab and loads chapters`() = runTest {
        val novel = NovelEntity(id = 5, title = "Selected")
        coEvery { chapterRepo.getChaptersByNovelSync(5) } returns listOf(
            ChapterEntity(id = 1, novelId = 5, title = "Ch1", fileName = "ch1.html", orderIndex = 0, content = "")
        )
        coEvery { charRepo.getByNovelSync(5) } returns emptyList()
        coEvery { charPhotoRepo.getByCharacterIds(any()) } returns emptyList()

        viewModel.selectNovel(novel)

        assertThat(viewModel.selectedNovel.value).isEqualTo(novel)
        assertThat(viewModel.selectedTab.value).isEqualTo(1)
        assertThat(viewModel.chapters.value).hasSize(1)
    }

    @Test
    fun `confirmDelete with repository error emits error event`() = runTest {
        val novel = NovelEntity(id = 1, title = "Fail", coverPath = null)
        viewModel.requestDelete(novel)
        coEvery { coverStorage.deleteCoverIfOwnedByApp(any(), any()) } returns true
        coEvery { coverStorage.deleteCharacterFolder(any()) } returns true
        coEvery { novelRepo.deleteById(1) } throws RuntimeException("DB locked")

        viewModel.errorEvents.test {
            viewModel.confirmDelete()
            val message = awaitItem()
            assertThat(message).contains("DB locked")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `confirmDelete clears showDeleteDialog in finally block`() = runTest {
        val novel = NovelEntity(id = 1, title = "X", coverPath = null)
        viewModel.requestDelete(novel)
        coEvery { coverStorage.deleteCoverIfOwnedByApp(any(), any()) } returns true
        coEvery { coverStorage.deleteCharacterFolder(any()) } returns true
        coEvery { novelRepo.deleteById(1) } returns Unit

        viewModel.confirmDelete()
        assertThat(viewModel.showDeleteDialog.value).isNull()
    }
}
