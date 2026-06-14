package com.novelreader.data.parser

import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeWebNovelParser @Inject constructor() : AbstractNovelParser() {

    override fun canParse(domain: String): Boolean {
        return domain.contains("freewebnovel.com")
    }

    override fun parse(doc: Document, fileName: String): ParsedChapter {
        val novelTitle = parseNovelTitle(doc, fileName)
        val chapterTitle = parseChapterTitle(doc, fileName)
        val content = parseContent(doc)
        return ParsedChapter(
            novelTitle = novelTitle,
            chapterTitle = chapterTitle,
            content = content
        )
    }

    private fun parseNovelTitle(doc: Document, fileName: String): String {
        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val parts = titleTag.split(Regex("\\s*-\\s*"))
            if (parts.size >= 2 && !parts[0].contains("Chapter", ignoreCase = true)) {
                return parts[0].trim()
            }
            val pipeIndex = titleTag.indexOf(" | ")
            return if (pipeIndex > 0) titleTag.substring(0, pipeIndex).trim()
            else titleTag
        }

        val h1 = doc.selectFirst("h1")
        if (h1 != null) return h1.text().trim()

        val ogTitle = doc.selectFirst("meta[property=og:title]")
        if (ogTitle != null) return ogTitle.attr("content").trim()

        return fromFileName(fileName)
    }

    private fun parseChapterTitle(doc: Document, fileName: String): String {
        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val dashParts = titleTag.split(Regex("\\s*-\\s*"))
            if (dashParts.size >= 2) {
                val afterNovel = dashParts.drop(1).joinToString(" - ")
                val pipeIndex = afterNovel.indexOf(" | ")
                return if (pipeIndex > 0) afterNovel.substring(0, pipeIndex).trim()
                else afterNovel.trim()
            }
        }

        val h2 = doc.selectFirst("h2")
        if (h2 != null) return h2.text().trim()

        val h1 = doc.selectFirst("h1")
        if (h1 != null && !h1.text().contains(fileName.substringBeforeLast(".").take(20), ignoreCase = true)) {
            return h1.text().trim()
        }

        return fromFileName(fileName)
    }

    private fun parseContent(doc: Document): String {
        val selectors = listOf("div.chapter-content", "div#chapter-content")
        for (selector in selectors) {
            val el = doc.selectFirst(selector)
            if (el != null) {
                sanitizeElement(el)
                val html = el.html().trim()
                if (html.length >= 50) return html
            }
        }
        return extractParagraphs(doc)
    }
}
