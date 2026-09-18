package com.novelreader.ui.customization

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.storage.WallpaperCrop
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
        every { appPreferences.wallpaperBehindBars } returns flowOf(true)
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
    fun `picking an image opens the crop step instead of copying it`() = runTest {
        val uri: Uri = mockk()

        viewModel.startCrop(uri)

        assertThat(viewModel.pendingCrop.value).isEqualTo(uri)
        coVerify(exactly = 0) { storage.importFromUri(any(), any()) }
    }

    @Test
    fun `applying the crop saves it at the screen size and clears the step`() = runTest {
        val uri: Uri = mockk()
        coEvery {
            storage.saveCropped(
                slot = WallpaperStorage.SLOT_HOME,
                uri = uri,
                crop = any(),
                targetWidth = 1080,
                targetHeight = 2400
            )
        } returns "file:home_cropped.jpg"
        viewModel.startCrop(uri)

        viewModel.applyCrop(WallpaperCrop(zoom = 2f, panX = 0.5f), 1080, 2400)

        coVerify {
            storage.saveCropped(
                slot = WallpaperStorage.SLOT_HOME,
                uri = uri,
                crop = WallpaperCrop(zoom = 2f, panX = 0.5f),
                targetWidth = 1080,
                targetHeight = 2400
            )
        }
        coVerify { appPreferences.updateHomeWallpaper("file:home_cropped.jpg") }
        assertThat(viewModel.pendingCrop.value).isNull()
    }

    @Test
    fun `cancelling the crop keeps the current wallpaper`() = runTest {
        viewModel.startCrop(mockk())

        viewModel.cancelCrop()

        assertThat(viewModel.pendingCrop.value).isNull()
        coVerify(exactly = 0) { appPreferences.updateHomeWallpaper(any()) }
    }

    @Test
    fun `exposes and updates the behind the bars option`() = runTest {
        assertThat(viewModel.behindBars.first()).isTrue()

        viewModel.updateBehindBars(false)

        coVerify { appPreferences.updateWallpaperBehindBars(false) }
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

    private fun viewModelFor(ref: String): HomeWallpaperViewModel {
        every { appPreferences.homeWallpaper } returns flowOf(ref)
        return HomeWallpaperViewModel(appPreferences, storage)
    }

    @Test
    fun `a light builtin wallpaper asks for the light containers`() = runTest {
        assertThat(viewModelFor("builtin:areia").wallpaperIsLight.first()).isTrue()

        coVerify(exactly = 0) { storage.wallpaperLuminance(any()) }
    }

    @Test
    fun `a dark builtin wallpaper keeps the palette containers`() = runTest {
        assertThat(viewModelFor("builtin:noite").wallpaperIsLight.first()).isFalse()
    }

    @Test
    fun `no wallpaper leaves the containers to the palette`() = runTest {
        assertThat(viewModelFor(PreferenceAllowlists.WALLPAPER_NONE).wallpaperIsLight.first())
            .isNull()
    }

    @Test
    fun `an image wallpaper is sampled`() = runTest {
        coEvery { storage.wallpaperLuminance("file:home_1.png") } returns 0.8f
        coEvery { storage.wallpaperLuminance("file:home_2.png") } returns 0.1f

        assertThat(viewModelFor("file:home_1.png").wallpaperIsLight.first()).isTrue()
        assertThat(viewModelFor("file:home_2.png").wallpaperIsLight.first()).isFalse()
    }

    @Test
    fun `an image that cannot be sampled keeps the palette containers`() = runTest {
        coEvery { storage.wallpaperLuminance("file:home_1.png") } returns null

        assertThat(viewModelFor("file:home_1.png").wallpaperIsLight.first()).isNull()
    }

    @Test
    fun `a reference that is not a wallpaper is never sampled`() = runTest {
        assertThat(viewModelFor("builtin:inventado").wallpaperIsLight.first()).isNull()
        assertThat(viewModelFor("file:../../app_prefs.xml").wallpaperIsLight.first()).isNull()
        assertThat(viewModelFor("home").wallpaperIsLight.first()).isNull()

        coVerify(exactly = 0) { storage.wallpaperLuminance(any()) }
    }

    // The crop screen simulates the library frame, so it has to know the tone of the image being
    // cropped before it is copied into the slot — otherwise the preview shows a contrast the applied
    // result will not have.
    @Test
    fun `picking an image samples its tone for the crop preview`() = runTest {
        val uri: Uri = mockk()
        coEvery { storage.uriLuminance(uri) } returns 0.9f

        viewModel.startCrop(uri)

        assertThat(viewModel.cropTone.value).isTrue()
        assertThat(viewModel.pendingCrop.value).isEqualTo(uri)
    }

    @Test
    fun `the crop preview has no tone when the image cannot be sampled`() = runTest {
        val uri: Uri = mockk()
        coEvery { storage.uriLuminance(uri) } returns null

        viewModel.startCrop(uri)

        assertThat(viewModel.cropTone.value).isNull()
    }

    @Test
    fun `leaving the crop step drops the preview tone`() = runTest {
        val uri: Uri = mockk()
        coEvery { storage.uriLuminance(uri) } returns 0.9f
        coEvery {
            storage.saveCropped(
                slot = WallpaperStorage.SLOT_HOME,
                uri = uri,
                crop = any(),
                targetWidth = any(),
                targetHeight = any()
            )
        } returns "file:home_cropped.jpg"
        viewModel.startCrop(uri)

        viewModel.cancelCrop()
        assertThat(viewModel.cropTone.value).isNull()

        viewModel.startCrop(uri)
        viewModel.applyCrop(WallpaperCrop(), 1080, 2400)
        assertThat(viewModel.cropTone.value).isNull()
    }
}
