package com.novelreader.ui.customization

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.storage.WallpaperStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeWallpaperViewModelTest {

    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val storage: WallpaperStorage = mockk(relaxed = true)
    private lateinit var viewModel: HomeWallpaperViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { appPreferences.homeWallpaper } returns flowOf("builtin:noite")
        every { appPreferences.homeWallpaperBlur } returns flowOf(12)
        viewModel = HomeWallpaperViewModel(appPreferences, storage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes the stored wallpaper and blur`() = runTest {
        assertThat(viewModel.wallpaper.first()).isEqualTo("builtin:noite")
        assertThat(viewModel.blur.first()).isEqualTo(12)
    }

    @Test
    fun `select persists the reference`() = runTest {
        viewModel.select("builtin:aurora")

        coVerify { appPreferences.updateHomeWallpaper("builtin:aurora") }
    }

    @Test
    fun `updateBlur persists the value`() = runTest {
        viewModel.updateBlur(30)

        coVerify { appPreferences.updateHomeWallpaperBlur(30) }
    }

    @Test
    fun `importFromUri stores the reference returned by the storage`() = runTest {
        val uri: Uri = mockk()
        coEvery { storage.importFromUri(WallpaperStorage.SLOT_HOME, uri) } returns "file:home_9.jpg"

        viewModel.importFromUri(uri)

        coVerify { appPreferences.updateHomeWallpaper("file:home_9.jpg") }
    }

    @Test
    fun `importFromUri keeps the current wallpaper when the file is rejected`() = runTest {
        val uri: Uri = mockk()
        coEvery { storage.importFromUri(WallpaperStorage.SLOT_HOME, uri) } returns null

        viewModel.importFromUri(uri)

        coVerify(exactly = 0) { appPreferences.updateHomeWallpaper(any()) }
    }

    @Test
    fun `remove clears the slot and resets the preference`() = runTest {
        viewModel.remove()

        coVerify { storage.clearSlot(WallpaperStorage.SLOT_HOME) }
        coVerify { appPreferences.updateHomeWallpaper(PreferenceAllowlists.WALLPAPER_NONE) }
    }
}
