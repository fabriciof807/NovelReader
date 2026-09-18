package com.novelreader.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.domain.usecase.webimport.HttpClient
import com.novelreader.domain.usecase.webimport.HttpResponse
import com.novelreader.domain.usecase.webimport.RemoteRequestPolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MvlempyrCharacterImporterTest {

    private lateinit var context: Context
    private lateinit var httpClient: HttpClient
    private lateinit var characterDao: CharacterDao
    private lateinit var characterPhotoDao: CharacterPhotoDao
    private lateinit var importer: MvlempyrCharacterImporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        httpClient = mockk()
        characterDao = mockk(relaxed = true)
        characterPhotoDao = mockk(relaxed = true)
        importer = MvlempyrCharacterImporter(
            context,
            characterDao,
            characterPhotoDao,
            Dispatchers.Unconfined,
            httpClient
        )
        File(context.filesDir, "characters").deleteRecursively()
    }

    @Test
    fun `fetch characters paginates the api with bounded responses`() = runTest {
        coEvery { httpClient.get(SOURCE_URL, any(), any(), any(), any(), any()) } returns
            response(SOURCE_URL, """filter(e => "42" === e.BookId)""")
        coEvery { httpClient.get(match { API_BASE in it && pageOf(it) == 1 }, any(), any(), any(), any(), any()) } returns
            response("api-1", """[{"BookId":"42","Name":"A"}]""")
        coEvery { httpClient.get(match { API_BASE in it && pageOf(it) == 2 }, any(), any(), any(), any(), any()) } returns
            response("api-2", "[]")

        val result = importer.fetchCharacters(SOURCE_URL)

        assertThat(result.map { it.name }).containsExactly("A")
        coVerify(exactly = 1) {
            httpClient.get(
                url = match { "per_page=100" in it && pageOf(it) == 1 },
                policy = RemoteRequestPolicy.AnyPublicHttps,
                maxBodyBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES,
                maxDecompressedBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES
            )
        }
    }

    @Test
    fun `three consecutive api failures stop pagination`() = runTest {
        coEvery { httpClient.get(SOURCE_URL, any(), any(), any(), any(), any()) } returns
            response(SOURCE_URL, """filter(e => "42" === e.BookId)""")
        coEvery { httpClient.get(match { API_BASE in it }, any(), any(), any(), any(), any()) } answers {
            when (val page = pageOf(firstArg())) {
                1 -> response(firstArg(), """[{"BookId":"42","Name":"A"}]""")
                2 -> response(firstArg(), "server error", status = 500)
                3 -> response(firstArg(), "not-json")
                4 -> throw IOException("connection reset")
                else -> response(firstArg(), """[{"BookId":"42","Name":"E$page"}]""")
            }
        }

        val result = importer.fetchCharacters(SOURCE_URL)

        assertThat(result.map { it.name }).containsExactly("A")
        coVerify(exactly = 4) {
            httpClient.get(
                url = match { API_BASE in it },
                referrer = null,
                extraHeaders = emptyMap(),
                policy = RemoteRequestPolicy.AnyPublicHttps,
                maxBodyBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES,
                maxDecompressedBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES
            )
        }
    }

    @Test
    fun `fifty non empty pages stop at five thousand records`() = runTest {
        coEvery { httpClient.get(SOURCE_URL, any(), any(), any(), any(), any()) } returns
            response(SOURCE_URL, """filter(e => "42" === e.BookId)""")
        coEvery {
            httpClient.get(match { API_BASE in it }, any(), any(), any(), any(), any())
        } answers { response(firstArg(), pageOf(firstArg()).let(::pageJson)) }

        val result = importer.fetchCharacters(SOURCE_URL)

        assertThat(result).hasSize(MvlempyrCharacterImporter.MAX_API_ENTRIES)
        assertThat(result.first().name).isEqualTo("N1-0")
        assertThat(result.last().name).isEqualTo("N50-99")
        coVerify(exactly = MvlempyrCharacterImporter.MAX_API_PAGES) {
            httpClient.get(
                url = match { API_BASE in it },
                referrer = null,
                extraHeaders = emptyMap(),
                policy = RemoteRequestPolicy.AnyPublicHttps,
                maxBodyBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES,
                maxDecompressedBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES
            )
        }
    }

    @Test
    fun `pages larger than the entry cap stop at five thousand records`() = runTest {
        coEvery { httpClient.get(SOURCE_URL, any(), any(), any(), any(), any()) } returns
            response(SOURCE_URL, """filter(e => "42" === e.BookId)""")
        coEvery {
            httpClient.get(match { API_BASE in it }, any(), any(), any(), any(), any())
        } answers { response(firstArg(), pageJson(pageOf(firstArg()), LARGE_PAGE_ENTRIES)) }

        val result = importer.fetchCharacters(SOURCE_URL)

        assertThat(result).hasSize(MvlempyrCharacterImporter.MAX_API_ENTRIES)
        coVerify(exactly = MvlempyrCharacterImporter.MAX_API_ENTRIES / LARGE_PAGE_ENTRIES) {
            httpClient.get(
                url = match { API_BASE in it },
                referrer = null,
                extraHeaders = emptyMap(),
                policy = RemoteRequestPolicy.AnyPublicHttps,
                maxBodyBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES,
                maxDecompressedBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES
            )
        }
    }

    @Test
    fun `import characters downloads the design image with the public policy and image limit`() = runTest {
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
        coEvery { httpClient.get(SOURCE_URL, any(), any(), any(), any(), any()) } returns
            response(SOURCE_URL, """filter(e => "42" === e.BookId)""")
        stubApi(1 to characterJson("A", DESIGN_IMAGE_URL), 2 to "[]")
        coEvery { httpClient.get(DESIGN_IMAGE_URL, any(), any(), any(), any(), any()) } returns
            HttpResponse(200, "", emptyMap(), DESIGN_IMAGE_URL, imageBytes)

        val inserted = slot<CharacterEntity>()
        val count = importer.importCharacters(SOURCE_URL, NOVEL_ID)

        assertThat(count).isEqualTo(1)
        coVerify {
            httpClient.get(
                url = DESIGN_IMAGE_URL,
                referrer = null,
                extraHeaders = emptyMap(),
                policy = RemoteRequestPolicy.AnyPublicHttps,
                maxBodyBytes = MvlempyrCharacterImporter.MAX_IMAGE_BYTES,
                maxDecompressedBytes = MvlempyrCharacterImporter.MAX_IMAGE_BYTES
            )
        }
        coVerify { characterDao.insert(capture(inserted)) }
        assertThat(inserted.captured.photoPath).isEqualTo(designFile().absolutePath)
        assertThat(designFile().readBytes().contentEquals(imageBytes)).isTrue()
        assertThat(characterDir().listFiles()!!.map { it.name }).containsExactly("design.jpeg")
    }

    @Test
    fun `failed image download leaves no design or temporary file`() = runTest {
        coEvery { httpClient.get(SOURCE_URL, any(), any(), any(), any(), any()) } returns
            response(SOURCE_URL, """filter(e => "42" === e.BookId)""")
        stubApi(1 to characterJson("A", DESIGN_IMAGE_URL), 2 to "[]")
        coEvery { httpClient.get(DESIGN_IMAGE_URL, any(), any(), any(), any(), any()) } throws
            IOException("Response body exceeds limit")

        val count = importer.importCharacters(SOURCE_URL, NOVEL_ID)

        assertThat(count).isEqualTo(1)
        assertThat(designFile().exists()).isFalse()
        assertThat(characterDir().listFiles().orEmpty().map { it.name }).isEmpty()
    }

    @Test
    fun `api cancellation is rethrown instead of skipping the page`() = runTest {
        coEvery { httpClient.get(SOURCE_URL, any(), any(), any(), any(), any()) } returns
            response(SOURCE_URL, """filter(e => "42" === e.BookId)""")
        coEvery { httpClient.get(match { pageOf(it) == 1 }, any(), any(), any(), any(), any()) } returns
            response("api-1", """[{"BookId":"42","Name":"A"}]""")
        coEvery { httpClient.get(match { pageOf(it) >= 2 }, any(), any(), any(), any(), any()) } throws
            CancellationException("cancelled")

        val error = runCatching { importer.importCharacters(SOURCE_URL, NOVEL_ID) }.exceptionOrNull()

        assertThat(error).isInstanceOf(CancellationException::class.java)
        coVerify(exactly = 1) {
            httpClient.get(match { pageOf(it) == 2 }, any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) { characterDao.insert(any()) }
    }

    @Test
    fun `image cancellation is rethrown instead of being swallowed`() = runTest {
        coEvery { httpClient.get(SOURCE_URL, any(), any(), any(), any(), any()) } returns
            response(SOURCE_URL, """filter(e => "42" === e.BookId)""")
        stubApi(1 to characterJson("A", DESIGN_IMAGE_URL), 2 to "[]")
        coEvery { httpClient.get(DESIGN_IMAGE_URL, any(), any(), any(), any(), any()) } throws
            CancellationException("cancelled")

        val error = runCatching { importer.importCharacters(SOURCE_URL, NOVEL_ID) }.exceptionOrNull()

        assertThat(error).isInstanceOf(CancellationException::class.java)
        coVerify(exactly = 0) { characterDao.insert(any()) }
        assertThat(characterDir().listFiles().orEmpty().map { it.name }).isEmpty()
    }

    private fun stubApi(vararg pages: Pair<Int, String>) {
        for ((page, body) in pages) {
            coEvery {
                httpClient.get(match { pageOf(it) == page }, any(), any(), any(), any(), any())
            } returns response("api-$page", body)
        }
    }

    private fun characterJson(name: String, designImageUrl: String): String =
        """[{"BookId":"42","Name":"$name","DesignImage":"$designImageUrl"}]"""

    private fun response(url: String, body: String, status: Int = 200): HttpResponse =
        HttpResponse(status, body, emptyMap(), url, body.toByteArray())

    private fun pageOf(url: String): Int =
        Regex("""[?&]page=(\d+)""").find(url)?.groupValues?.get(1)?.toInt() ?: 0

    private fun pageJson(page: Int, size: Int = MvlempyrCharacterImporter.API_PAGE_SIZE): String =
        (0 until size)
            .joinToString(prefix = "[", postfix = "]", separator = ",") {
                """{"BookId":"42","Name":"N$page-$it"}"""
            }

    private fun characterDir(): File =
        File(context.filesDir, "characters/$NOVEL_ID/A")

    private fun designFile(): File = File(characterDir(), "design.jpeg")

    private companion object {
        const val SOURCE_URL = "https://www.mvlempyr.io/novel/build-a-bot"
        const val API_BASE = "mvl-characters"
        const val DESIGN_IMAGE_URL = "https://cdn.mvlempyr.io/design/a.jpg"
        const val NOVEL_ID = 7L
        const val LARGE_PAGE_ENTRIES = 200
    }
}
