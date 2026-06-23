package com.novelreader.domain.usecase

import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.entity.FailedChapterErrorType
import com.novelreader.domain.usecase.importnovel.ChapterEntry
import com.novelreader.domain.usecase.importnovel.ChapterInserter
import com.novelreader.domain.usecase.webimport.ChapterFetcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetryChapterUseCase @Inject constructor(
    private val failedChapterDao: FailedChapterDao,
    private val chapterInserter: ChapterInserter,
    private val chapterFetcher: ChapterFetcher
) {
    suspend fun retryByUrl(failedId: Long): Result<Unit> {
        val failed = failedChapterDao.getById(failedId)
            ?: return Result.failure(IllegalArgumentException("Falha não encontrada"))
        val url = failed.url
            ?: return Result.failure(IllegalStateException("Falha sem URL para re-tentar"))

        return try {
            val fetched = chapterFetcher.fetch(url, failed.fileName, failed.title)
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
            Result.success(Unit)
        } catch (e: Exception) {
            failedChapterDao.deleteById(failedId)
            failedChapterDao.insert(
                failed.copy(
                    errorType = FailedChapterErrorType.classify(e),
                    errorMessage = e.message ?: "Erro desconhecido",
                    attemptedAt = System.currentTimeMillis()
                )
            )
            Result.failure(e)
        }
    }
}
