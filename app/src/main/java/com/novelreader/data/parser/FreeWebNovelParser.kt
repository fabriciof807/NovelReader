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
            val extracted = TitleExtractor.extractNovelTitle(titleTag)
            if (extracted != null) return extracted
            return titleTag
        }

        val h1 = doc.selectFirst("h1")
        if (h1 != null) return h1.text().trim()

        val ogTitle = doc.selectFirst("meta[property=og:title]")
        if (ogTitle != null) return ogTitle.attr("content").trim()

        return fromFileName(fileName)
    }

    private fun parseChapterTitle(doc: Document, fileName: String): String {
        val novelTitleGuess = parseNovelTitle(doc, fileName)
        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val fromTag = TitleExtractor.extractChapterTitleFromTag(titleTag, novelTitleGuess)
            if (fromTag != null) return fromTag
            val extracted = TitleExtractor.extractChapterTitle(titleTag, novelTitleGuess)
            if (extracted != null) return extracted
        }

        val h2 = doc.selectFirst("h2")
        if (h2 != null) {
            val cleaned = TitleExtractor.cleanChapterTitleForDisplay(h2.text().trim(), novelTitleGuess)
            if (cleaned != h2.text().trim()) return cleaned
            if (cleaned.isNotEmpty()) return cleaned
        }

        val h1 = doc.selectFirst("h1")
        if (h1 != null) {
            val text = h1.text().trim()
            if (!text.contains(fileName.substringBeforeLast(".").take(20), ignoreCase = true)) {
                val cleaned = TitleExtractor.cleanChapterTitleForDisplay(text, novelTitleGuess)
                return cleaned
            }
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
