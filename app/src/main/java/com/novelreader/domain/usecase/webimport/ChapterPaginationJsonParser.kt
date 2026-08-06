package com.novelreader.domain.usecase.webimport

import org.json.JSONObject

data class ChapterPagination(
    val code: Int,
    val html: String
)

fun parseChapterPaginationJson(json: String): ChapterPagination? {
    return try {
        val obj = JSONObject(json)
        val code = obj.optInt("code", 0)
        if (code != 200) null
        else if (!obj.has("html")) null
        else ChapterPagination(code = code, html = obj.getString("html"))
    } catch (_: Exception) {
        null
    }
}
