package com.novelreader.domain.usecase.webimport

import com.novelreader.data.local.db.dao.NovelDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_COVER_BYTES = 10 * 1024 * 1024

@Singleton
class CoverDownloader @Inject constructor(
    private val novelDao: NovelDao,
    private val httpClient: HttpClient
) {
    suspend fun downloadCover(novelId: Long, coverUrl: String, filesDir: File) = withContext(Dispatchers.IO) {
        try {
            if (!coverUrl.startsWith("https://")) return@withContext
            val host = runCatching { URI(coverUrl).host }.getOrNull() ?: return@withContext
            val response = httpClient.get(
                url = coverUrl,
                policy = RemoteRequestPolicy.SameNovelDomain(host),
                maxBodyBytes = MAX_COVER_BYTES,
                maxDecompressedBytes = MAX_COVER_BYTES
            )
            if (response.statusCode !in 200..299) return@withContext
            val dir = File(filesDir, "covers")
            dir.mkdirs()
            val dest = File(dir, "novel_$novelId.jpg")
            dest.outputStream().use { output ->
                output.write(response.bodyBytes ?: response.body.toByteArray(Charsets.UTF_8))
            }
            if (dest.exists()) {
                novelDao.updateCoverPath(novelId, dest.absolutePath)
            }
        } catch (_: Exception) { }
    }
}
