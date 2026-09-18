package com.novelreader.ui.reader

import com.novelreader.data.local.db.entity.ChapterEntity

fun filterChaptersByQuery(chapters: List<ChapterEntity>, query: String): List<ChapterEntity> {
    if (query.isBlank()) return chapters
    val needle = query.trim()
    return chapters.filter { it.title.contains(needle, ignoreCase = true) }
}

/** The order the sheet shows: the reversed list when the reader asked for it. */
fun chapterListDisplayOrder(chapters: List<ChapterEntity>, reversed: Boolean): List<ChapterEntity> =
    if (reversed) chapters.asReversed() else chapters

/**
 * Where the sheet opens: the position of the chapter being read in the list it is about to show, or
 * -1 when that chapter is not in the list (a filtered list, or no chapter loaded yet).
 */
fun chapterScrollTarget(chapters: List<ChapterEntity>, currentChapterId: Long?): Int =
    chapters.indexOfFirst { it.id == currentChapterId }
