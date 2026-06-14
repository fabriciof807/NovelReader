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
                    titleFromMhtSubject(mhtSubject) ?: result.novelTitle
                } else {
                    result.novelTitle
                }

                val finalChapterTitle = if (result.chapterTitle.isBlank() ||
                    result.chapterTitle.equals(fileBasedTitle, ignoreCase = true)
                ) {
                    chapterTitleFromMhtSubject(mhtSubject)
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

    private fun titleFromMhtSubject(subject: String?): String? {
        if (subject.isNullOrBlank()) return null
        val cleaned = subject.trim()
        val separators = listOf(" | ", " – ", " - ", " — ", " :: ", " « ")
        for (sep in separators) {
            val idx = cleaned.indexOf(sep)
            if (idx > 0) {
                val candidate = cleaned.substring(0, idx).trim()
                if (!candidate.contains("Chapter", ignoreCase = true) &&
                    candidate.length < 100
                ) return candidate
            }
        }
        val pipeIdx = cleaned.indexOf(" | ")
        return if (pipeIdx > 0) cleaned.substring(0, pipeIdx).trim() else cleaned
    }

    private fun chapterTitleFromMhtSubject(subject: String?): String? {
        if (subject.isNullOrBlank()) return null
        val cleaned = subject.trim()
        val pipeIdx = cleaned.indexOf(" | ")
        val candidate = if (pipeIdx > 0) cleaned.substring(pipeIdx + 3).trim() else cleaned
        if (candidate.contains("Chapter", ignoreCase = true) ||
            candidate.contains("Cap", ignoreCase = true)
        ) {
            val secondPipe = candidate.indexOf(" | ")
            return if (secondPipe > 0) candidate.substring(0, secondPipe).trim() else candidate
        }
        val separators = listOf(" - ", " – ", " — ", " :: ", " « ")
        for (sep in separators) {
            val idx = candidate.indexOf(sep)
            if (idx > 0) {
                val after = candidate.substring(idx + sep.length).trim()
                if (after.isNotEmpty()) {
                    val pipe2 = after.indexOf(" | ")
                    return if (pipe2 > 0) after.substring(0, pipe2).trim() else after
                }
            }
        }
        return null
    }
}
