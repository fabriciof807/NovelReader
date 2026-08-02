package com.novelreader.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.PendingImportPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportDataUseCaseTest {
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private val uri: Uri = mockk()
    private val webImportUseCase: WebImportUseCase = mockk()
    private val backgroundImportManager: BackgroundImportManager = mockk(relaxed = true)
    private val pendingImportPreferences: PendingImportPreferences = mockk(relaxed = true)

    private lateinit var useCase: ImportDataUseCase

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        coEvery {
            webImportUseCase.fetchChapterList("https://example.com/novel")
        } returns Result.success(
            FetchResult(
                chapters = listOf(ChapterLink("Chapter 1", "https://example.com/chapter-1", 1)),
                novelTitle = "Fetched Novel"
            )
        )
        useCase = ImportDataUseCase(
            context = context,
            webImportUseCase = webImportUseCase,
            backgroundImportManager = backgroundImportManager,
            pendingImportPreferences = pendingImportPreferences,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `execute forwards true favorite metadata`() = runTest {
        val result = execute("true")

        assertThat(result.novelsFailed).isEmpty()
        assertThat(result.novelsQueued).containsExactly("Fetched Novel")

        coVerify {
            backgroundImportManager.startImport(
                novelTitle = "Fetched Novel",
                links = any(),
                coverUrl = null,
                sourceUrl = "https://example.com/novel",
                isFavorite = true
            )
        }
    }

    @Test
    fun `execute forwards false favorite metadata`() = runTest {
        execute("false")

        coVerify {
            backgroundImportManager.startImport(
                novelTitle = "Fetched Novel",
                links = any(),
                coverUrl = null,
                sourceUrl = "https://example.com/novel",
                isFavorite = false
            )
        }
    }

    @Test
    fun `execute forwards null favorite metadata when importing a v1 backup`() = runTest {
        execute(null)

        coVerify {
            backgroundImportManager.startImport(
                novelTitle = "Fetched Novel",
                links = any(),
                coverUrl = null,
                sourceUrl = "https://example.com/novel",
                isFavorite = null
            )
        }
    }

    private suspend fun execute(favorite: String?): ImportResult {
        val favoriteJson = favorite?.let { ",\"isFavorite\":$it" } ?: ""
        val json = """
            {
              "novels": [{"title":"Backup Novel","sourceUrl":"https://example.com/novel"$favoriteJson}],
              "bookmarks": [],
              "characters": []
            }
        """.trimIndent()
        every { contentResolver.openInputStream(uri) } returns
            ByteArrayInputStream(json.toByteArray())
        return useCase.execute(uri)
    }
}
