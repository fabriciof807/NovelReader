package com.novelreader.data.parser

import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenericFallbackParser @Inject constructor() : AbstractNovelParser() {

    override fun canParse(domain: String): Boolean = true

    override fun parse(doc: Document, fileName: String): ParsedChapter {
        val novelTitle = parseNovelTitle(doc, fileName)
        val chapterTitle = parseChapterTitle(doc, fileName, novelTitle)
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
        if (h1 != null) {
            val text = h1.text().trim()
            if (!text.contains("Chapter", ignoreCase = true) && text.length < 100) return text
        }

        val ogTitle = doc.selectFirst("meta[property=og:title]")
        if (ogTitle != null) {
            val og = ogTitle.attr("content").trim()
            if (og.isNotEmpty()) return og
        }

        val twitterTitle = doc.selectFirst("meta[name=twitter:title]")
        if (twitterTitle != null) {
            val tt = twitterTitle.attr("content").trim()
            if (tt.isNotEmpty()) return tt
        }

        val ogSite = doc.selectFirst("meta[property=og:site_name]")
        if (ogSite != null) {
            val os = ogSite.attr("content").trim()
            if (os.isNotEmpty()) return os
        }

        return fromFileName(fileName, "Unknown Novel")
    }

    private fun parseChapterTitle(doc: Document, fileName: String, novelTitle: String): String {
        val titleTag = doc.title().trim()
        if (titleTag.isNotEmpty()) {
            val extracted = TitleExtractor.extractChapterTitle(titleTag, novelTitle)
            if (extracted != null) return extracted
        }

        val h2 = doc.selectFirst("h2")
        if (h2 != null) {
            val text = h2.text().trim()
            if (text.isNotEmpty() && text != novelTitle) return text
        }

        val h1 = doc.selectFirst("h1")
        if (h1 != null) {
            val text = h1.text().trim()
            if (text.isNotEmpty() && text != novelTitle) return text
        }

        return fromFileName(fileName, "Chapter")
    }

    private fun parseContent(doc: Document): String {
        val selectors = listOf(
            "div.chapter-content", "div#chapter-content",
            "div.entry-content", "div.post-content",
            "div.article-content", "div.content",
            "article", "main"
        )
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
