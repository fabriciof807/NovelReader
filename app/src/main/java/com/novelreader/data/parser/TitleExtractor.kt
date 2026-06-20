package com.novelreader.data.parser

object TitleExtractor {

    private val SEPARATORS = listOf(" | ", " – ", " - ", " — ", " :: ", " « ")

    fun extractNovelTitle(fullTitle: String?): String? {
        if (fullTitle.isNullOrBlank()) return null
        val cleaned = fullTitle.trim()
        if (cleaned.isBlank()) return null

        for (sep in SEPARATORS) {
            val idx = cleaned.indexOf(sep)
            if (idx > 0) {
                val candidate = cleaned.substring(0, idx).trim()
                if (!candidate.contains("Chapter", ignoreCase = true) &&
                    candidate.length < 100
                ) return candidate
            }
        }

        val pipeIdx = cleaned.indexOf(" | ")
        if (pipeIdx > 0) {
            val beforePipe = cleaned.substring(0, pipeIdx).trim()
            val dashParts = beforePipe.split(Regex("\\s+-\\s+"))
            if (dashParts.size >= 2 && !dashParts[0].contains("Chapter", ignoreCase = true)) {
                return dashParts[0].trim()
            }
            return beforePipe
        }

        return null
    }

    fun extractChapterTitle(fullTitle: String?, novelTitle: String? = null): String? {
        if (fullTitle.isNullOrBlank()) return null
        val cleaned = fullTitle.trim()
        if (cleaned.isBlank()) return null

        val withoutNovel = if (novelTitle != null && cleaned.startsWith(novelTitle)) {
            cleaned.removePrefix(novelTitle)
        } else cleaned

        for (sep in SEPARATORS) {
            val idx = withoutNovel.indexOf(sep)
            if (idx >= 0) {
                val after = withoutNovel.substring(idx + sep.length).trim()
                if (after.isNotEmpty()) {
                    val pipeIdx = after.indexOf(" | ")
                    return if (pipeIdx > 0) after.substring(0, pipeIdx).trim() else after
                }
            }
        }

        val pipeIdx = withoutNovel.indexOf(" | ")
        if (pipeIdx >= 0) {
            val candidate = withoutNovel.substring(pipeIdx + 3).trim()
            if (candidate.contains("Chapter", ignoreCase = true) ||
                candidate.contains("Cap", ignoreCase = true)
            ) {
                val secondPipe = candidate.indexOf(" | ")
                return if (secondPipe > 0) candidate.substring(0, secondPipe).trim() else candidate
            }
        }

        return null
    }

    fun cleanHtmlTitle(title: String): String {
        val cleaned = title
            .replace(Regex("""\s*[-–—|•·:]\s*(web)?novel.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[-–—|•·:]\s*(read|ler|online|free|gratis).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.ifBlank { title }
    }
}
