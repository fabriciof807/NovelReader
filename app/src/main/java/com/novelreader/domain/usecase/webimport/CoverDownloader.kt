package com.novelreader.domain.usecase.webimport

import com.novelreader.data.local.db.dao.NovelDao
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverDownloader @Inject constructor(
    private val novelDao: NovelDao
) {
    suspend fun downloadCover(novelId: Long, coverUrl: String, filesDir: File) {
        try {
            if (!coverUrl.startsWith("https://")) return
            val dir = File(filesDir, "covers")
            dir.mkdirs()
            val dest = File(dir, "novel_$novelId.jpg")
            val connection = URL(coverUrl).openConnection()
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.getInputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (dest.exists()) {
                novelDao.updateCoverPath(novelId, dest.absolutePath)
            }
        } catch (_: Exception) { }
    }
}
