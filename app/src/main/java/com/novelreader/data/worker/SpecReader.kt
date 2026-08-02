package com.novelreader.data.worker

import androidx.work.Data
import com.novelreader.domain.usecase.ImportJobSpec
import java.util.UUID

object SpecReader {
    fun readFromData(data: Data, store: SpecFileStore): ImportJobSpec? {
        val idStr = data.getString(ChapterImportWorker.KEY_JOB_ID) ?: return null
        val id = try { UUID.fromString(idStr) } catch (_: Exception) { return null }
        val title = data.getString(ChapterImportWorker.KEY_TITLE)
        val cover = data.getString(ChapterImportWorker.KEY_COVER)?.takeIf { it.isNotBlank() }
        val enqueuedAt = data.getLong(ChapterImportWorker.KEY_ENQUEUED_AT, 0L)
            .let { if (it == 0L) System.currentTimeMillis() else it }
        val splitCount = data.getInt(ChapterImportWorker.KEY_SPLIT_COUNT, 1)
        val splitIndex = data.getInt(ChapterImportWorker.KEY_SPLIT_INDEX, 0)
        val sourceUrl = data.getString(ChapterImportWorker.KEY_SOURCE_URL) ?: ""
        val domain = data.getString(ChapterImportWorker.KEY_DOMAIN) ?: ""
        val targetNovelId = data.getLong(ChapterImportWorker.KEY_TARGET_NOVEL_ID, -1L).takeIf { it >= 0 }
        val isFavorite = if (data.getBoolean(ChapterImportWorker.KEY_IS_FAVORITE_PRESENT, false)) {
            data.getBoolean(ChapterImportWorker.KEY_IS_FAVORITE, false)
        } else {
            null
        }
        val fromStore = store.read(id)
        if (fromStore != null) {
            return fromStore.copy(
                domain = domain.ifEmpty { fromStore.domain },
                targetNovelId = targetNovelId ?: fromStore.targetNovelId,
                isFavorite = isFavorite ?: fromStore.isFavorite
            )
        }
        return ImportJobSpec(
            id = id,
            novelTitle = title ?: "",
            links = emptyList(),
            chapterNumbers = emptyList(),
            coverUrl = cover,
            enqueuedAt = enqueuedAt,
            splitCount = splitCount,
            splitIndex = splitIndex,
            sourceUrl = sourceUrl,
            domain = domain,
            targetNovelId = targetNovelId,
            isFavorite = isFavorite
        )
    }
}
