package com.novelreader.data.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeWebNovelParser @Inject constructor() : AbstractNovelParser() {

    override fun canParse(domain: String): Boolean {
        return com.novelreader.util.StringUtils.hostMatchesDomain(domain, "freewebnovel.com")
    }

    override fun parse(doc: Document, fileName: String): ParsedChapter {
        if (isNotFoundPage(doc)) {
            return ParsedChapter(
                novelTitle = parseNovelTitle(doc, fileName),
                chapterTitle = "",
                content = ""
            )
        }
        val novelTitle = parseNovelTitle(doc, fileName)
        val chapterTitle = parseChapterTitle(doc, fileName, novelTitle)
        val content = parseContent(doc)
        return ParsedChapter(
            novelTitle = novelTitle,
            chapterTitle = chapterTitle,
            content = content
        )
    }

    private fun isNotFoundPage(doc: Document): Boolean {
        val title = doc.title()
        if (title.contains("Page not found", ignoreCase = true) ||
            title.contains("Not Found", ignoreCase = true) ||
            title.contains("404", ignoreCase = true)
        ) return true
        val h1 = doc.selectFirst("h1")
        if (h1 != null) {
            val h1Text = h1.text()
            if (h1Text.contains("404") || h1Text.contains("Page not found", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun parseNovelTitle(doc: Document, fileName: String): String {
        val h1NovelLink = doc.selectFirst("h1.tit a[href*=/novel/]")
        if (h1NovelLink != null) {
            val text = h1NovelLink.text().trim()
            if (text.isNotEmpty()) return text
        }

        val h1 = doc.selectFirst("h1.tit")
        if (h1 != null) {
            val text = h1.text().trim()
            if (text.isNotEmpty()) return text
        }

        val ogTitle = doc.selectFirst("meta[property=og:title]")
        if (ogTitle != null) {
            val extracted = TitleExtractor.extractNovelTitle(ogTitle.attr("content").trim())
            if (extracted != null) return extracted
        }

        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val extracted = TitleExtractor.extractNovelTitle(titleTag)
            if (extracted != null) return extracted
            return TitleExtractor.cleanHtmlTitle(titleTag)
        }

        return fromFileName(fileName)
    }

    private fun parseChapterTitle(
        doc: Document,
        fileName: String,
        novelTitle: String
    ): String {
        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val fromTag = TitleExtractor.extractChapterTitleFromTag(titleTag, novelTitle)
            if (fromTag != null && fromTag.isNotEmpty() && fromTag != titleTag) return fromTag
            val extracted = TitleExtractor.extractChapterTitle(titleTag, novelTitle)
            if (extracted != null) return extracted
        }

        val h1 = doc.selectFirst("h1.tit")
        if (h1 != null) {
            val text = h1.text().trim()
            if (!text.contains(novelTitle, ignoreCase = true) && text.isNotEmpty()) {
                return text
            }
        }

        val h2 = doc.selectFirst("h2")
        if (h2 != null) {
            val text = h2.text().trim()
            if (text.contains("Chapter", ignoreCase = true)) {
                return TitleExtractor.cleanChapterTitleForDisplay(text, novelTitle)
            }
        }

        return fromFileName(fileName)
    }

    private fun parseContent(doc: Document): String {
        for (selector in CONTENT_SELECTORS) {
            val el = doc.selectFirst(selector)
            if (el != null) {
                sanitizeElement(el)
                val html = el.html().trim()
                if (html.length >= 50) return html
            }
        }

        val chapterBlock = extractChapterStartEnd(doc)
        if (chapterBlock != null && chapterBlock.length >= 200) return chapterBlock

        val fromParagraphs = extractParagraphs(doc)
        if (fromParagraphs.length >= 200) return fromParagraphs

        return fromParagraphs
    }

    private fun extractChapterStartEnd(doc: Document): String? {
        val start = doc.selectFirst("div.chapter-start") ?: return null
        val end = doc.selectFirst("div.chapter-end") ?: return null
        val block = Element("div")
        var current: Element? = start.nextElementSibling()
        while (current != null && current != end) {
            block.appendChild(current.clone())
            current = current.nextElementSibling()
        }
        block.select("div.read-ads, div[id^=pf-], script, ins, .pub").remove()
        val ps = block.select("p")
        val sb = StringBuilder()
        for (p in ps) {
            val text = p.text().trim()
            if (text.length < 20) continue
            sb.appendLine(p.outerHtml())
        }
        return sb.toString().trim().ifEmpty { null }
    }

    private companion object {
        val CONTENT_SELECTORS = listOf(
            "div.chapter-content",
            "div#chapter-content",
            "div.txt"
        )
    }
}
