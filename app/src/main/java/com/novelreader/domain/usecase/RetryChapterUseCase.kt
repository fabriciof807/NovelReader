package com.novelreader.domain.usecase

import android.util.Log
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.FailedChapterErrorType
import com.novelreader.domain.usecase.importnovel.ChapterEntry
import com.novelreader.domain.usecase.importnovel.ChapterInserter
import com.novelreader.domain.usecase.webimport.ChapterFetcher
import javax.inject.Inject
import javax.inject.Singleton

private const val FAILURE_TAG = "WebImportFailure"

@Singleton
class RetryChapterUseCase @Inject constructor(
    private val failedChapterDao: FailedChapterDao,
    private val chapterInserter: ChapterInserter,
    private val chapterFetcher: ChapterFetcher,
    private val novelDao: NovelDao
) {
    suspend fun retryByUrl(failedId: Long): Result<Unit> {
        val failed = failedChapterDao.getById(failedId)
            ?: return Result.failure(IllegalArgumentException("Falha não encontrada"))
        val url = failed.url
            ?: return Result.failure(IllegalStateException("Falha sem URL para re-tentar"))
        val expectedHost = novelDao.getNovelById(failed.novelId)?.sourceUrl?.let { hostOf(it) }.orEmpty()
        if (expectedHost.isBlank()) {
            return Result.failure(IllegalStateException("Novel sem URL de origem para re-tentar"))
        }

        return try {
            val fetched = chapterFetcher.fetch(url, failed.fileName, failed.title, expectedHost)
            if (looksLikeStaleContent(fetched.content)) {
                Log.w(
                    FAILURE_TAG,
                    "retry failedId=$failedId novelId=${failed.novelId} url=$url fileName=${failed.fileName} errType=empty_content errMsg=content blank or stale"
                )
                failedChapterDao.deleteById(failedId)
                failedChapterDao.insert(
                    failed.copy(
                        errorType = FailedChapterErrorType.EMPTY_CONTENT,
                        errorMessage = "Retry returned empty/stale content",
                        attemptedAt = System.currentTimeMillis()
                    )
                )
                return Result.failure(IllegalStateException("Retry returned empty/stale content"))
            }
            chapterInserter.insertEntries(
                failed.novelId,
                listOf(
                    ChapterEntry(
                        novelTitle = "",
                        chapterTitle = fetched.title,
                        content = fetched.content,
                        fileName = failed.fileName
                    )
                )
            )
            failedChapterDao.deleteById(failedId)
            Log.w(
                FAILURE_TAG,
                "retry failedId=$failedId novelId=${failed.novelId} url=$url fileName=${failed.fileName} errType=success errMsg=recovered"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            val errorType = FailedChapterErrorType.classify(e)
            Log.w(
                FAILURE_TAG,
                "retry failedId=$failedId novelId=${failed.novelId} url=$url fileName=${failed.fileName} errType=$errorType errMsg=${e.message ?: "Erro"}"
            )
            failedChapterDao.deleteById(failedId)
            failedChapterDao.insert(
                failed.copy(
                    errorType = errorType,
                    errorMessage = e.message ?: "Erro desconhecido",
                    attemptedAt = System.currentTimeMillis()
                )
            )
            Result.failure(e)
        }
    }

    private fun hostOf(url: String): String = try { java.net.URI(url).host.orEmpty() } catch (_: Exception) { "" }
}
