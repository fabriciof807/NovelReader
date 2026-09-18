package com.novelreader.domain.usecase.webimport

import com.novelreader.data.local.db.dao.NovelDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_COVER_BYTES = 10 * 1024 * 1024

@Singleton
class CoverDownloader @Inject constructor(
    private val novelDao: NovelDao,
    private val httpClient: HttpClient
) {
    suspend fun downloadCover(
        novelId: Long,
        coverUrl: String,
        expectedHost: String,
        filesDir: File
    ) = withContext(Dispatchers.IO) {
        try {
            if (!coverUrl.startsWith("https://")) return@withContext
            val response = httpClient.get(
                url = coverUrl,
                policy = RemoteRequestPolicy.SameNovelDomain(expectedHost),
                maxBodyBytes = MAX_COVER_BYTES,
                maxDecompressedBytes = MAX_COVER_BYTES
            )
            if (response.statusCode !in 200..299) return@withContext
            val dir = File(filesDir, "covers")
            dir.mkdirs()
            val temp = File(dir, "novel_$novelId.jpg.tmp")
            try {
                temp.writeBytes(response.bodyBytes ?: response.body.toByteArray(Charsets.UTF_8))
                val dest = File(dir, "novel_$novelId.jpg")
                if (temp.renameTo(dest)) {
                    novelDao.updateCoverPath(novelId, dest.absolutePath)
                }
            } finally {
                temp.delete()
            }
        } catch (_: Exception) { }
    }
}
