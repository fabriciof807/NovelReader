package com.novelreader.domain.usecase.webimport

import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.util.StringUtils
import org.jsoup.nodes.Document
import java.net.URI

internal fun extractChapterLinks(doc: Document, homeUrl: String, homeDomain: String): List<ChapterLink> {
    val allLinks = doc.select("a[href]")
    val candidateLinks = mutableListOf<Pair<String, String>>()

    for (link in allLinks) {
        val href = link.attr("abs:href").ifEmpty { link.attr("href") }
        val text = link.text().trim()

        if (href.isBlank() || href == "#" || href.startsWith("javascript:") || href.startsWith("mailto:")) continue
        if (text.isBlank()) continue

        val linkDomain = hostOfOrNull(href)
        if (linkDomain != null && linkDomain != homeDomain && !href.startsWith("/")) continue

        val normalizedHref = if (href.startsWith("/")) {
            val uri = URI(homeUrl)
            val origin = "${uri.scheme}://${uri.authority}"
            "$origin$href"
        } else href

        val lowerHref = normalizedHref.lowercase()
        val lowerText = text.lowercase()

        val chapterKeywords = listOf("chapter", "ch-", "ch_", "capitulo", "cap-", "vol-", "book-", "part-")
        val hasChapterKeyword = chapterKeywords.any { it in lowerHref || it in lowerText }
        val hasNumber = Regex("""\d+""").containsMatchIn(text)

        val excludeKeywords = listOf("home", "login", "register", "signup", "contact", "about", "privacy", "terms", "profile", "settings", "search", "forum", "discord", "facebook", "twitter", "instagram", "youtube")
        val isExcluded = excludeKeywords.any { it in lowerHref || it in lowerText }

        if (isExcluded) continue

        if (hasChapterKeyword && hasNumber) {
            candidateLinks.add(text to normalizedHref)
        }
    }

    if (candidateLinks.size < 3) {
        for (link in allLinks) {
            val href = link.attr("abs:href").ifEmpty { link.attr("href") }
            val text = link.text().trim()

            if (href.isBlank() || href == "#" || href.startsWith("javascript:") || href.startsWith("mailto:")) continue
            if (text.isBlank()) continue

            val linkDomain = hostOfOrNull(href)
            if (linkDomain != null && linkDomain != homeDomain && !href.startsWith("/")) continue

            val normalizedHref = if (href.startsWith("/")) {
                val uri = URI(homeUrl)
                val origin = "${uri.scheme}://${uri.authority}"
                "$origin$href"
            } else href

            val lowerHref = normalizedHref.lowercase()
            val lowerText = text.lowercase()

            val isExcluded = listOf("home", "login", "register", "signup", "contact", "about", "privacy", "terms", "profile", "settings", "search", "forum", "discord", "facebook", "twitter", "instagram", "youtube")
                .any { it in lowerHref || it in lowerText }
            if (isExcluded) continue

            val hasNumber = Regex("""\d+""").containsMatchIn(text) || Regex("""/\d+/""").containsMatchIn(normalizedHref)
            if (hasNumber) {
                candidateLinks.add(text to normalizedHref)
            }
        }
    }

    val seenUrls = mutableSetOf<String>()
    val result = mutableListOf<ChapterLink>()
    for ((title, url) in candidateLinks) {
        val normalized = url.trimEnd('/')
        if (normalized in seenUrls) continue
        seenUrls.add(normalized)
        val num = extractChapterNumberForLinks(title, url)
        result.add(ChapterLink(title = title, url = normalized, chapterNumber = num))
    }
    return result.distinctBy { it.url }
}

private fun hostOfOrNull(url: String): String? = try { URI(url).host } catch (_: Exception) { null }

private fun extractChapterNumberForLinks(title: String, url: String): Int {
    val fileName = StringUtils.fileNameFromUrl(url, "chapter_")
    return com.novelreader.data.parser.ChapterNumberExtractor.extract(title = title, url = url, fileName = fileName)
}
