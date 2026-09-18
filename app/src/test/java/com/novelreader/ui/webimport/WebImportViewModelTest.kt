package com.novelreader.ui.webimport

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.domain.usecase.BackgroundImportManager
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.domain.usecase.WebImportUseCase
import com.novelreader.domain.usecase.webimport.CloudflareChallengeRequiredException
import com.novelreader.domain.usecase.webimport.CloudflareCookieStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class WebImportViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val webImportUseCase: WebImportUseCase = mockk(relaxed = true)
    private val backgroundImportManager: BackgroundImportManager = mockk(relaxed = true)
    private val cookieStore: CloudflareCookieStore = mockk(relaxed = true)
    private val novelDao: NovelDao = mockk(relaxed = true)

    private lateinit var viewModel: WebImportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { backgroundImportManager.state } returns MutableStateFlow(BackgroundImportState())
        viewModel = WebImportViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            webImportUseCase,
            backgroundImportManager,
            cookieStore,
            novelDao
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `challenge expectation comes from the typed source url`() = runTest {
        val sourceUrl = "https://freewebnovel.com/novel/one"
        val foreignChallenge = "https://evil.example/cdn-cgi/challenge"
        coEvery { webImportUseCase.fetchChapterList(sourceUrl) } returns
            Result.failure(CloudflareChallengeRequiredException(foreignChallenge, "challenge"))

        viewModel.updateUrl(sourceUrl)
        viewModel.fetchChapters()
        advanceUntilIdle()

        assertThat(viewModel.state.value.cloudflareChallenge).isEqualTo(
            CloudflareChallenge(
                url = foreignChallenge,
                expectedHost = "freewebnovel.com"
            )
        )
    }

    @Test
    fun `malformed source url leaves the expected host blank`() = runTest {
        val sourceUrl = "not a url"
        val foreignChallenge = "https://evil.example/cdn-cgi/challenge"
        coEvery { webImportUseCase.fetchChapterList(sourceUrl) } returns
            Result.failure(CloudflareChallengeRequiredException(foreignChallenge, "challenge"))

        viewModel.updateUrl(sourceUrl)
        viewModel.fetchChapters()
        advanceUntilIdle()

        assertThat(viewModel.state.value.cloudflareChallenge).isEqualTo(
            CloudflareChallenge(
                url = foreignChallenge,
                expectedHost = ""
            )
        )
    }
}
