package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.NovelDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CoverDownloaderTest {

    @Test
    fun downloadCover_writesBinaryPayloadAndUpdatesCoverPath() = runBlocking {
        val bytes = byteArrayOf(0x00, 0x13, 0x37, 0x7f, 0xff.toByte(), 0x42)
        val novelDao = mockk<NovelDao>(relaxed = true)
        val httpClient = mockk<HttpClient>()
        val coverUrl = "https://covers.example/cover.jpg"
        coEvery { httpClient.get(coverUrl) } returns HttpResponse(
            statusCode = 200,
            body = "",
            headers = emptyMap(),
            finalUrl = coverUrl,
            bodyBytes = bytes
        )
        val filesDir = Files.createTempDirectory("cover-download-test").toFile()

        try {
            CoverDownloader(novelDao, httpClient).downloadCover(42L, coverUrl, filesDir)

            val destination = File(filesDir, "covers/novel_42.jpg")
            assertThat(destination.readBytes().contentEquals(bytes)).isTrue()
            coVerify { novelDao.updateCoverPath(42L, destination.absolutePath) }
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
