package com.novelreader.data.storage

import android.content.Context
import android.net.Uri
import com.novelreader.di.qualifiers.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    suspend fun saveFromUri(novelId: Long, uri: Uri): String? = withContext(io) {
        try {
            val dest = coverFile(novelId)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveFromUrl(novelId: Long, url: String): String? = withContext(io) {
        try {
            if (!url.startsWith("https://")) return@withContext null
            val connection = URL(url).openConnection()
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val contentLength = connection.contentLength
            if (contentLength > 10 * 1024 * 1024) return@withContext null
            val dest = coverFile(novelId)
            connection.getInputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteCoverIfOwnedByApp(novelId: Long, coverPath: String?): Boolean = withContext(io) {
        val path = coverPath ?: return@withContext false
        val file = File(path)
        val appRoot = context.filesDir.canonicalPath
        if (file.canonicalPath.startsWith(appRoot) && file.exists()) {
            file.delete()
        } else false
    }

    suspend fun deleteCharacterFolder(novelId: Long): Boolean = withContext(io) {
        val dir = File(context.filesDir, "characters/$novelId")
        if (dir.exists()) dir.deleteRecursively() else false
    }

    private fun coverFile(novelId: Long): File {
        val dir = File(context.filesDir, "covers")
        dir.mkdirs()
        return File(dir, "novel_$novelId.jpg")
    }
}
