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
        val fromStore = store.read(id)
        if (fromStore != null) return fromStore
        return ImportJobSpec(
            id = id,
            novelTitle = title ?: "",
            links = emptyList(),
            chapterNumbers = emptyList(),
            coverUrl = cover,
            enqueuedAt = enqueuedAt,
            splitCount = splitCount,
            splitIndex = splitIndex,
            sourceUrl = sourceUrl
        )
    }
}
