package com.novelreader.data.parser

import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParserRegistry @Inject constructor(
    private val parsers: Set<@JvmSuppressWildcards NovelParser>,
    private val fallbackParser: GenericFallbackParser,
    private val mhtParser: MhtParser
) {

    fun getParserForUrl(url: String): NovelParser {
        val domain = Uri.parse(url).host ?: ""
        return findParserForDomain(domain) ?: fallbackParser
    }

    fun getParserForDomain(domain: String): NovelParser {
        return findParserForDomain(domain) ?: fallbackParser
    }

    private fun findParserForDomain(domain: String): NovelParser? {
        return parsers.firstOrNull { it.canParse(domain) }
    }

    fun parse(html: String, fileName: String): ParsedChapter {
        val doc: Document = Jsoup.parse(html)
        val domain = tryDetectDomain(doc)
        val parser = if (domain != null) getParserForDomain(domain) else fallbackParser
        return parser.parse(doc, fileName)
    }

    fun parseRaw(raw: String, fileName: String): ParsedChapter {
        if (mhtParser.isMhtFile(fileName)) {
            val html = mhtParser.extractHtml(raw)
            val mhtSubject = mhtParser.extractSubject(raw)

            if (html != null) {
                val result = parse(html, fileName)
                val fileBasedTitle = fileName.substringBeforeLast(".")
                    .replace("-", " ").replace("_", " ").trim()

                val finalNovelTitle = if (result.novelTitle.isBlank() ||
                    result.novelTitle == "Unknown Novel" ||
                    result.novelTitle.equals(fileBasedTitle, ignoreCase = true)
                ) {
                    TitleExtractor.extractNovelTitle(mhtSubject) ?: result.novelTitle
                } else {
                    result.novelTitle
                }

                val finalChapterTitle = if (result.chapterTitle.isBlank() ||
                    result.chapterTitle.equals(fileBasedTitle, ignoreCase = true)
                ) {
                    TitleExtractor.extractChapterTitle(mhtSubject)
                        ?: result.chapterTitle
                } else {
                    result.chapterTitle
                }

                return ParsedChapter(
                    novelTitle = finalNovelTitle,
                    chapterTitle = finalChapterTitle,
                    content = result.content
                )
            }
        }

        return parse(raw, fileName)
    }

    private fun tryDetectDomain(doc: Document): String? {
        val links = doc.select("a[href]")
        for (link in links) {
            val href = link.attr("abs:href")
            if (href.isNotEmpty()) {
                val host = Uri.parse(href).host
                if (host != null && findParserForDomain(host) != null) {
                    return host
                }
            }
        }
        return null
    }
}
