package com.novelreader.data.parser

import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadNovelFullParser @Inject constructor() : AbstractNovelParser() {

    override fun canParse(domain: String): Boolean {
        return domain.contains("readnovelfull.com")
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
        val h3 = doc.selectFirst("h3.title[itemprop=name]")
        if (h3 != null) return h3.text().trim()

        val novelLink = doc.selectFirst("a.novel-title")
        if (novelLink != null) return novelLink.text().trim()

        val ogTitle = doc.selectFirst("meta[property=og:title]")
        if (ogTitle != null) {
            val content = ogTitle.attr("content").trim()
            val parts = content.split(Regex("\\s*[-–]\\s*"))
            if (parts.size >= 2 && !parts[0].contains("Chapter", ignoreCase = true)) {
                return parts[0].trim()
            }
        }

        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val dashParts = titleTag.split(Regex("\\s*[-–]\\s*"))
            if (dashParts.size >= 2 && !dashParts[0].contains("Chapter", ignoreCase = true)) {
                return dashParts[0].trim()
            }
        }

        return fromFileName(fileName)
    }

    private fun parseChapterTitle(doc: Document, fileName: String): String {
        val chrText = doc.selectFirst("span.chr-text")
        if (chrText != null) return chrText.text().trim()

        val h2 = doc.selectFirst("h2")
        if (h2 != null) {
            val text = h2.text().trim()
            if (text.contains("Chapter", ignoreCase = true)) return text
        }

        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val parts = titleTag.split(Regex("\\s*[-–]\\s*"))
            if (parts.size >= 2) {
                val afterNovel = parts.drop(1).joinToString(" - ")
                val pipeIndex = afterNovel.indexOf(" | ")
                return if (pipeIndex > 0) afterNovel.substring(0, pipeIndex).trim()
                else afterNovel.trim()
            }
        }

        return fromFileName(fileName)
    }

    private fun parseContent(doc: Document): String {
        val contentSelectors = listOf("div#chr-content", "div.chr-c")
        for (selector in contentSelectors) {
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
