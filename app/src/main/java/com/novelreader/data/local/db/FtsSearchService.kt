package com.novelreader.data.local.db

import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.entity.ChapterEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtsSearchService @Inject constructor(
    private val chapterDao: ChapterDao
) {
    suspend fun searchInNovel(novelId: Long, query: String): List<ChapterEntity> {
        val ftsQuery = query.trim()
            .replace(Regex("[\"()^+\\-~]"), " ")
            .replace(Regex("\\s+"), " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { "\"$it\"*" }
        if (ftsQuery.isBlank()) return emptyList()
        return chapterDao.searchInNovel(novelId, ftsQuery)
    }
}
