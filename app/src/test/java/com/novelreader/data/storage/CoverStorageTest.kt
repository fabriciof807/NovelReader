package com.novelreader.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.webimport.HttpClient
import com.novelreader.domain.usecase.webimport.HttpResponse
import com.novelreader.domain.usecase.webimport.RemoteRequestPolicy
import io.mockk.coEvery
import io.mockk.mockk
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
class CoverStorageTest {

    private lateinit var context: Context
    private lateinit var httpClient: HttpClient
    private lateinit var storage: CoverStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        httpClient = mockk()
        storage = CoverStorage(context, Dispatchers.Unconfined, httpClient)
        File(context.filesDir, "covers").deleteRecursively()
    }

    private fun coversDir(): File = File(context.filesDir, "covers")

    @Test
    fun `remote cover uses the public https policy and actual byte limit`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        coEvery {
            httpClient.get(
                url = COVER_URL,
                policy = RemoteRequestPolicy.AnyPublicHttps,
                maxBodyBytes = CoverStorage.MAX_REMOTE_COVER_BYTES,
                maxDecompressedBytes = CoverStorage.MAX_REMOTE_COVER_BYTES
            )
        } returns HttpResponse(200, "", emptyMap(), COVER_URL, bytes)

        val path = storage.saveFromUrl(7L, COVER_URL)

        assertThat(requireNotNull(path).let(::File).readBytes().contentEquals(bytes)).isTrue()
        assertThat(coversDir().listFiles()!!.map { it.name }).containsExactly("novel_7.jpg")
    }

    @Test
    fun `failed remote cover leaves no destination or temporary file`() = runTest {
        coEvery { httpClient.get(any(), any(), any(), any(), any(), any()) } throws
            IOException("Response body exceeds limit")

        assertThat(storage.saveFromUrl(7L, COVER_URL)).isNull()
        assertThat(coversDir().listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `non 2xx remote cover returns null and writes no file`() = runTest {
        coEvery {
            httpClient.get(
                url = COVER_URL,
                policy = RemoteRequestPolicy.AnyPublicHttps,
                maxBodyBytes = CoverStorage.MAX_REMOTE_COVER_BYTES,
                maxDecompressedBytes = CoverStorage.MAX_REMOTE_COVER_BYTES
            )
        } returns HttpResponse(404, "not found", emptyMap(), COVER_URL, byteArrayOf(9))

        assertThat(storage.saveFromUrl(7L, COVER_URL)).isNull()
        assertThat(coversDir().listFiles().orEmpty()).isEmpty()
    }

    private companion object {
        const val COVER_URL = "https://covers.example/cover.jpg"
    }
}
