package com.novelreader.domain.usecase.importnovel

import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.parser.ChapterNumberExtractor
import javax.inject.Inject
import javax.inject.Singleton

data class ChapterEntry(
    val novelTitle: String,
    val chapterTitle: String,
    val content: String,
    val fileName: String,
    val existingId: Long? = null
)

@Singleton
class ChapterSorter @Inject constructor() {

    fun buildSortedEntries(
        novelTitle: String,
        parsedFiles: List<ParsedFile>,
        existingChapters: List<ChapterEntity>,
        existingFileNames: Set<String>
    ): List<ChapterEntry> {
        val allEntries = mutableListOf<ChapterEntry>()
        val existingNumbers: Set<Int> = existingChapters
            .filter { it.content.isNotBlank() }
            .mapNotNull { ch -> ChapterNumberExtractor.extract(ch.title, ch.fileName).takeIf { it != Int.MAX_VALUE } }
            .toSet()

        for (ch in existingChapters) {
            allEntries.add(
                ChapterEntry(
                    novelTitle = novelTitle,
                    chapterTitle = ch.title,
                    content = ch.content,
                    fileName = ch.fileName,
                    existingId = ch.id
                )
            )
        }

        val seenInBatch = mutableSetOf<String>()
        for (pf in parsedFiles) {
            if (pf.fileName in existingFileNames) continue
            if (!seenInBatch.add(pf.fileName)) continue
            val n = ChapterNumberExtractor.extract(pf.parsed.chapterTitle, pf.fileName)
            if (n != Int.MAX_VALUE && n in existingNumbers) continue
            allEntries.add(
                ChapterEntry(
                    novelTitle = pf.parsed.novelTitle,
                    chapterTitle = pf.parsed.chapterTitle,
                    content = pf.parsed.content,
                    fileName = pf.fileName
                )
            )
        }

        allEntries.sortBy { extractChapterNumber(it.chapterTitle, it.fileName) }
        return allEntries
    }

    private fun extractChapterNumber(title: String, fileName: String): Int {
        return ChapterNumberExtractor.extract(title = title, fileName = fileName)
    }
}
