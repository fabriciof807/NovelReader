package com.novelreader.data.parser

import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadNovelFullParser @Inject constructor() : AbstractNovelParser() {

    override fun canParse(domain: String): Boolean {
        return com.novelreader.util.StringUtils.hostMatchesDomain(domain, "readnovelfull.com")
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
            val extracted = TitleExtractor.extractNovelTitle(ogTitle.attr("content").trim())
            if (extracted != null) return extracted
        }

        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val extracted = TitleExtractor.extractNovelTitle(titleTag)
            if (extracted != null) return extracted
            return titleTag
        }

        return fromFileName(fileName)
    }

    private fun parseChapterTitle(doc: Document, fileName: String): String {
        val novelTitleGuess = parseNovelTitle(doc, fileName)
        val chrText = doc.selectFirst("span.chr-text")
        if (chrText != null) {
            val cleaned = TitleExtractor.cleanChapterTitleForDisplay(chrText.text().trim(), novelTitleGuess)
            return cleaned
        }

        val h2 = doc.selectFirst("h2")
        if (h2 != null) {
            val text = h2.text().trim()
            if (text.contains("Chapter", ignoreCase = true)) {
                return TitleExtractor.cleanChapterTitleForDisplay(text, novelTitleGuess)
            }
        }

        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val fromTag = TitleExtractor.extractChapterTitleFromTag(titleTag, novelTitleGuess)
            if (fromTag != null) return fromTag
            val extracted = TitleExtractor.extractChapterTitle(titleTag, novelTitleGuess)
            if (extracted != null) return extracted
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
