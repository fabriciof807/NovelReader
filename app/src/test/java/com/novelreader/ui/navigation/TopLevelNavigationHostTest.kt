package com.novelreader.ui.navigation

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.FolderDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.domain.usecase.BackgroundImportManager
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.ui.components.AppDestination
import com.novelreader.ui.library.LibraryViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TopLevelNavigationHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val novelDao: NovelDao = mockk(relaxed = true)
    private val chapterDao: ChapterDao = mockk(relaxed = true)
    private val bookmarkDao: BookmarkDao = mockk(relaxed = true)
    private val bgManager: BackgroundImportManager = mockk(relaxed = true)
    private val prefs: LibraryPreferences = mockk(relaxed = true)
    private val folderDao: FolderDao = mockk(relaxed = true)

    private lateinit var nav: NavHostController
    private var currentVm: LibraryViewModel? = null

    @Before
    fun setUp() {
        every { novelDao.getAllNovels() } returns flowOf(emptyList())
        every { bookmarkDao.getAll() } returns flowOf(emptyList())
        every { chapterDao.getNewChaptersFlow() } returns flowOf(emptyList())
        every { folderDao.getAll() } returns flowOf(emptyList())
        every { folderDao.getFolderCounts() } returns flowOf(emptyList())
        every { prefs.sortOrder } returns flowOf("LAST_READ")
        every { prefs.chapterSortOrder } returns flowOf("ASCENDING")
        every { prefs.viewMode } returns flowOf("GRID")
        every { bgManager.state } returns MutableStateFlow(BackgroundImportState())
    }

    private fun libraryViewModel(handle: SavedStateHandle) = LibraryViewModel(
        context = mockk<Context>(relaxed = true),
        savedStateHandle = handle,
        novelDao = novelDao,
        chapterDao = chapterDao,
        bookmarkDao = bookmarkDao,
        backgroundImportManager = bgManager,
        libraryPreferences = prefs,
        characterManagementUseCase = mockk(relaxed = true),
        coverManagementUseCase = mockk(relaxed = true),
        characterPhotoDao = mockk<CharacterPhotoDao>(relaxed = true),
        mvlempyrCharacterImporter = mockk(relaxed = true),
        updateCheckScheduler = mockk(relaxed = true),
        notificationPermissionCoordinator = mockk(relaxed = true),
        webImportUseCase = mockk(relaxed = true),
        failedChapterDao = mockk<FailedChapterDao>(relaxed = true),
        folderDao = folderDao,
        retryChapterUseCase = mockk(relaxed = true),
        scanMissingChaptersUseCase = mockk(relaxed = true),
        chapterInserter = mockk(relaxed = true),
        parserRegistry = mockk(relaxed = true),
        mhtParser = mockk(relaxed = true),
        fileCharsetDetector = mockk(relaxed = true),
        io = kotlinx.coroutines.Dispatchers.Unconfined
    )

    @Composable
    private fun LibraryProbe(entry: NavBackStackEntry) {
        val vm: LibraryViewModel = viewModel(
            factory = viewModelFactory { initializer { libraryViewModel(createSavedStateHandle()) } }
        )
        val tab by vm.selectedTab.collectAsState()
        LibraryTabRequests(entry, vm)
        Text("tab=$tab")
        currentVm = vm
    }

    private fun launch() {
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = Routes.LIBRARY) {
                composable(
                    route = Routes.LIBRARY_WITH_SELECTION,
                    arguments = listOf(
                        navArgument(LibraryViewModel.ARG_SELECTED_NOVEL_ID) {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                        navArgument(LibraryViewModel.ARG_SHOW_FAILED) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { entry -> LibraryProbe(entry) }
                composable(Routes.SETTINGS) { Text("settings") }
                composable(Routes.FAVORITES) { Text("favorites") }
            }
        }
        composeRule.waitForIdle()
    }

    private fun go(destination: AppDestination) {
        composeRule.runOnUiThread { nav.navigateToTopLevel(destination)
        }
        composeRule.waitForIdle()
    }

    private fun assertTab(tab: Int) {
        composeRule.onNodeWithText("tab=$tab").assertExists()
    }

    @Test
    fun `library to settings to library lands on the library tab`() {
        launch()
        go(AppDestination.SETTINGS)
        go(AppDestination.LIBRARY)
        assertTab(0)
    }

    @Test
    fun `collections to settings to library lands on the library tab`() {
        launch()
        composeRule.runOnUiThread { currentVm!!.selectTab(1) }
        composeRule.waitForIdle()
        assertTab(1)
        go(AppDestination.SETTINGS)
        go(AppDestination.LIBRARY)
        assertTab(0)
    }

    @Test
    fun `library to settings to collections lands on the collections tab`() {
        launch()
        go(AppDestination.SETTINGS)
        go(AppDestination.COLLECTIONS)
        assertTab(1)
    }

    @Test
    fun `collections to library lands on the library tab`() {
        launch()
        go(AppDestination.COLLECTIONS)
        go(AppDestination.LIBRARY)
        assertTab(0)
    }
}
