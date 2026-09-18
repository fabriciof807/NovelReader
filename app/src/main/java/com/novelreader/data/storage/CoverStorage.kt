package com.novelreader.data.storage

import android.content.Context
import android.net.Uri
import com.novelreader.di.qualifiers.IoDispatcher
import com.novelreader.domain.usecase.webimport.HttpClient
import com.novelreader.domain.usecase.webimport.RemoteRequestPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val httpClient: HttpClient
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
        val temp = tempCoverFile(novelId)
        try {
            val response = httpClient.get(
                url = url,
                policy = RemoteRequestPolicy.AnyPublicHttps,
                maxBodyBytes = MAX_REMOTE_COVER_BYTES,
                maxDecompressedBytes = MAX_REMOTE_COVER_BYTES
            )
            if (response.statusCode !in 200..299) return@withContext null
            val bytes = response.bodyBytes ?: return@withContext null
            temp.writeBytes(bytes)
            val dest = coverFile(novelId)
            if (!temp.renameTo(dest)) return@withContext null
            dest.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            temp.delete()
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

    private fun coverFile(novelId: Long): File = File(coverDir(), "novel_$novelId.jpg")

    private fun tempCoverFile(novelId: Long): File = File(coverDir(), "novel_$novelId.jpg.tmp")

    private fun coverDir(): File = File(context.filesDir, "covers").apply { mkdirs() }

    companion object {
        internal const val MAX_REMOTE_COVER_BYTES = 10 * 1024 * 1024
    }
}
