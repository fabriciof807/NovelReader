package com.novelreader.ui.reader

import com.novelreader.data.local.db.entity.ChapterEntity

fun filterChaptersByQuery(chapters: List<ChapterEntity>, query: String): List<ChapterEntity> {
    if (query.isBlank()) return chapters
    val needle = query.trim()
    return chapters.filter { it.title.contains(needle, ignoreCase = true) }
}
