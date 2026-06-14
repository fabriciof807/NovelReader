package com.novelreader.data.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ParsedChapter(
    val novelTitle: String,
    val chapterTitle: String,
    val content: String
)

interface NovelParser {
    fun canParse(domain: String): Boolean
    fun parse(doc: Document, fileName: String): ParsedChapter
}

abstract class AbstractNovelParser : NovelParser {

    protected fun sanitizeElement(root: Element) {
        HtmlSanitizer.sanitizeElement(root)
    }

    protected fun extractParagraphs(doc: Document): String {
        val heading = doc.selectFirst("h1, h2")
        val paragraphs = doc.select("p")
        val sb = StringBuilder()

        if (heading != null) {
            val h = heading.clone()
            sanitizeElement(h)
            val html = h.outerHtml().trim()
            if (html.isNotEmpty()) sb.appendLine(html)
        }
        for (p in paragraphs) {
            val clone = p.clone()
            sanitizeElement(clone)
            val html = clone.outerHtml().trim()
            if (html.isNotEmpty()) sb.appendLine(html)
        }
        val result = sb.toString().trim()
        return result.ifEmpty { doc.html().trim() }
    }

    protected fun fromFileName(fileName: String, fallback: String = "Unknown"): String {
        return fileName.substringBeforeLast(".")
            .replace("-", " ")
            .replace("_", " ")
            .trim()
            .ifEmpty { fallback }
    }
}
