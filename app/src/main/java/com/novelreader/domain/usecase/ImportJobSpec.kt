package com.novelreader.domain.usecase

import java.util.UUID

data class ImportJobSpec(
    val id: UUID,
    val novelTitle: String,
    val links: List<String>,
    val chapterNumbers: List<Int>,
    val coverUrl: String?,
    val enqueuedAt: Long,
    val splitCount: Int = 1,
    val splitIndex: Int = 0,
    val sourceUrl: String = ""
) {
    companion object {
        const val BATCH_SIZE = 100

        fun create(
            novelTitle: String,
            links: List<ChapterLink>,
            coverUrl: String?,
            sourceUrl: String = ""
        ): List<ImportJobSpec> {
            val id = UUID.randomUUID()
            val enqueuedAt = System.currentTimeMillis()
            return links.chunked(BATCH_SIZE).mapIndexed { index, chunk ->
                ImportJobSpec(
                    id = id,
                    novelTitle = novelTitle,
                    links = chunk.map { it.url },
                    chapterNumbers = chunk.map { it.chapterNumber },
                    coverUrl = coverUrl,
                    enqueuedAt = enqueuedAt,
                    splitCount = (links.size + BATCH_SIZE - 1) / BATCH_SIZE,
                    splitIndex = index,
                    sourceUrl = sourceUrl
                )
            }
        }
    }
}
