package com.novelreader.domain.usecase.webimport

import org.jsoup.nodes.Document

data class ChapterPaginationState(
    val totalChapters: Int,
    val totalPage: Int,
    val pageSize: Int
)

private val CHAPTER_PAGINATION_SCRIPT_REGEX = Regex(
    "chapterPagination\\s*=\\s*\\{([^}]*)\\}",
    RegexOption.DOT_MATCHES_ALL
)

fun extractChapterPaginationState(doc: Document): ChapterPaginationState? {
    val scripts = doc.select("script")
    for (script in scripts) {
        val data = script.data()
        if (!data.contains("chapterPagination")) continue
        val match = CHAPTER_PAGINATION_SCRIPT_REGEX.find(data) ?: continue
        val body = match.groupValues[1]
        val totalChapters = extractScriptInt(body, "totalChapters") ?: 0
        val totalPage = extractScriptInt(body, "totalPage") ?: 0
        val pageSize = extractScriptInt(body, "pageSize") ?: 0
        if (totalPage == 0 && totalChapters == 0) return null
        return ChapterPaginationState(
            totalChapters = totalChapters,
            totalPage = totalPage,
            pageSize = pageSize
        )
    }
    return null
}

private val SCRIPT_INT_FIELD_REGEX = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(\d+)""")

private fun extractScriptInt(body: String, name: String): Int? {
    return SCRIPT_INT_FIELD_REGEX.findAll(body)
        .firstOrNull { it.groupValues[1] == name }
        ?.groupValues?.get(2)
        ?.toIntOrNull()
}
