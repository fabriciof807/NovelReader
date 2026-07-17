package com.novelreader.ui.settings

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.ImportDataUseCase
import com.novelreader.domain.usecase.ImportPreview
import com.novelreader.domain.usecase.ImportPreviewEntry
import com.novelreader.domain.usecase.ImportResult
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.domain.usecase.ExportDataUseCase
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
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val exportDataUseCase: ExportDataUseCase = mockk(relaxed = true)
    private val importDataUseCase: ImportDataUseCase = mockk(relaxed = true)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { appPreferences.appTheme } returns flowOf("system")
        every { appPreferences.locale } returns flowOf("pt")
        every { appPreferences.dynamicColorEnabled } returns flowOf(true)
        viewModel = SettingsViewModel(appPreferences, exportDataUseCase, importDataUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `importSelected sets isImporting true then false and stores result`() = runTest {
        val uri: Uri = mockk(relaxed = true)
        val preview = ImportPreview(
            novels = listOf(ImportPreviewEntry("Novel 1", ""), ImportPreviewEntry("Novel 2", "")),
            bookmarksCount = 3,
            charactersCount = 1
        )
        val result = ImportResult(
            novelsQueued = listOf("Novel 1", "Novel 2"),
            novelsFailed = listOf("Failed Novel"),
            bookmarksPending = 3,
            charactersPending = 1
        )
        coEvery { importDataUseCase.previewImport(uri) } returns preview
        coEvery { importDataUseCase.execute(uri, setOf("Novel 1", "Novel 2")) } returns result

        viewModel.previewImport(uri)
        assertThat(viewModel.isImporting.value).isFalse()
        assertThat(viewModel.lastImportResult.value).isNull()

        viewModel.importSelected(setOf("Novel 1", "Novel 2"))

        assertThat(viewModel.isImporting.value).isFalse()
        assertThat(viewModel.lastImportResult.value).isEqualTo(result)
    }

    @Test
    fun `importSelected with no pending uri does nothing`() = runTest {
        viewModel.importSelected(setOf("Test"))
        assertThat(viewModel.isImporting.value).isFalse()
        assertThat(viewModel.lastImportResult.value).isNull()
    }

    @Test
    fun `clearImportResult clears the result`() = runTest {
        val uri: Uri = mockk(relaxed = true)
        val preview = ImportPreview(
            novels = listOf(ImportPreviewEntry("Novel 1", "")),
            bookmarksCount = 0,
            charactersCount = 0
        )
        val result = ImportResult(
            novelsQueued = listOf("Novel 1"),
            novelsFailed = emptyList(),
            bookmarksPending = 0,
            charactersPending = 0
        )
        coEvery { importDataUseCase.previewImport(uri) } returns preview
        coEvery { importDataUseCase.execute(any(), any()) } returns result
        viewModel.previewImport(uri)
        viewModel.importSelected(setOf("Novel 1"))

        viewModel.clearImportResult()

        assertThat(viewModel.lastImportResult.value).isNull()
    }
}
