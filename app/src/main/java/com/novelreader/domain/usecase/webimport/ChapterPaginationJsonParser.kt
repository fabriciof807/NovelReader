package com.novelreader.domain.usecase.webimport

data class ChapterPagination(
    val code: Int,
    val html: String,
    val page: Int,
    val pageSize: Int,
    val totalPage: Int,
    val totalChapters: Int
)

fun parseChapterPaginationJson(json: String): ChapterPagination? {
    val code = extractJsonNumberField(json, "code") ?: return null
    if (code != 200) return null
    val html = extractJsonStringField(json, "html") ?: return null
    val page = extractJsonNumberField(json, "page") ?: 0
    val pageSize = extractJsonNumberField(json, "pageSize") ?: 0
    val totalPage = extractJsonNumberField(json, "totalPage") ?: 0
    val totalChapters = extractJsonNumberField(json, "totalChapters") ?: 0
    return ChapterPagination(
        code = code,
        html = html,
        page = page,
        pageSize = pageSize,
        totalPage = totalPage,
        totalChapters = totalChapters
    )
}

private val JSON_STRING_FIELD_REGEX = Regex(""""([A-Za-z_][A-Za-z0-9_]*)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private val JSON_NUMBER_FIELD_REGEX = Regex(""""([A-Za-z_][A-Za-z0-9_]*)"\s*:\s*(-?\d+(?:\.\d+)?)""")

private fun extractJsonStringField(json: String, name: String): String? {
    return JSON_STRING_FIELD_REGEX.findAll(json)
        .firstOrNull { it.groupValues[1] == name }
        ?.groupValues?.get(2)
        ?.let { unescapeJsonString(it) }
}

private fun extractJsonNumberField(json: String, name: String): Int? {
    return JSON_NUMBER_FIELD_REGEX.findAll(json)
        .firstOrNull { it.groupValues[1] == name }
        ?.groupValues?.get(2)
        ?.toDoubleOrNull()
        ?.toInt()
}

private fun unescapeJsonString(raw: String): String {
    val sb = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '\\' && i + 1 < raw.length) {
            when (raw[i + 1]) {
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                '"' -> { sb.append('"'); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                '/' -> { sb.append('/'); i += 2 }
                'u' -> {
                    if (i + 5 < raw.length) {
                        val hex = raw.substring(i + 2, i + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                        } else { sb.append(c); i++ }
                    } else { sb.append(c); i++ }
                }
                else -> { sb.append(c); i += 1 }
            }
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}
