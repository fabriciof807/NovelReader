package com.novelreader.data.parser

object ChapterNumberExtractor {

    private val CHAPTER_PATTERN = Regex(
        """(?:chapter|cap[íi]tulo|ch|cap)\s*[.:\-]?\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val NUM_PATTERN = Regex("""(\d+)""")

    fun extract(title: String, fileName: String? = null, url: String? = null): Int {
        val patterns = listOfNotNull(title, url, fileName)
        for (source in patterns) {
            CHAPTER_PATTERN.find(source)?.let { match ->
                return match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
            }
        }
        for (source in patterns) {
            NUM_PATTERN.find(source)?.let { match ->
                return match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
            }
        }
        return Int.MAX_VALUE
    }
}
