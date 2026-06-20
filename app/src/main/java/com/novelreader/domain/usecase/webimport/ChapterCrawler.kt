package com.novelreader.domain.usecase.webimport

import android.net.Uri
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.data.parser.ChapterNumberExtractor
import com.novelreader.data.parser.TitleExtractor
import com.novelreader.util.StringUtils
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

data class CrawlResult(
    val links: List<ChapterLink>,
    val coverUrl: String?,
    val novelTitle: String?
)

private val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.113 Mobile Safari/537.36"
private const val MAX_PAGES = 50
private const val PAGE_DELAY_MS = 500L

@Singleton
class ChapterCrawler @Inject constructor() {

    suspend fun crawlChapterList(homeUrl: String): CrawlResult {
        val homeDomain = Uri.parse(homeUrl).host ?: ""
        val allLinks = mutableListOf<ChapterLink>()
        var currentUrl: String? = homeUrl
        var coverUrl: String? = null
        var novelTitle: String? = null
        var pageCount = 0

        while (currentUrl != null && pageCount < MAX_PAGES) {
            val doc = fetchPage(currentUrl)
            if (pageCount == 0) {
                coverUrl = extractCoverUrl(doc, homeUrl)
                novelTitle = extractNovelTitle(doc)
            }
            allLinks.addAll(extractChapterLinks(doc, currentUrl, homeDomain))
            currentUrl = findNextPageUrl(doc, currentUrl)
            pageCount++
            if (currentUrl != null) delay(PAGE_DELAY_MS)
        }

        val links = allLinks.distinctBy { it.url }
        return CrawlResult(links = links, coverUrl = coverUrl, novelTitle = novelTitle)
    }

    private fun fetchPage(url: String): Document {
        if (!url.startsWith("https://")) throw SecurityException("Apenas HTTPS permitido")
        return Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(30_000)
            .followRedirects(true)
            .get()
    }

    private fun findNextPageUrl(doc: Document, currentUrl: String): String? {
        val nextTexts = listOf("next", "próxima", "proximo", ">", "»", "›")
        val nextSelectors = listOf("a.next", "a[rel=next]", ".pagination a", ".pager a", "a[aria-label*=next]", "a[aria-label*=Next]")

        for (selector in nextSelectors) {
            val el = doc.selectFirst(selector) ?: continue
            val href = el.attr("abs:href").ifEmpty { el.attr("href") }
            if (href.isNotBlank() && href != currentUrl) {
                return makeAbsolute(href, currentUrl)
            }
        }

        for (link in doc.select("a[href]")) {
            val text = link.text().trim().lowercase()
            val ariaLabel = (link.attr("aria-label") ?: "").lowercase()
            if (nextTexts.any { text == it || ariaLabel.contains(it) }) {
                val href = link.attr("abs:href").ifEmpty { link.attr("href") }
                if (href.isNotBlank() && href != currentUrl) {
                    return makeAbsolute(href, currentUrl)
                }
            }
        }

        return null
    }

    private fun extractChapterLinks(doc: Document, homeUrl: String, homeDomain: String): List<ChapterLink> {
        val allLinks = doc.select("a[href]")
        val candidateLinks = mutableListOf<Pair<String, String>>()

        for (link in allLinks) {
            val href = link.attr("abs:href").ifEmpty { link.attr("href") }
            val text = link.text().trim()

            if (href.isBlank() || href == "#" || href.startsWith("javascript:") || href.startsWith("mailto:")) continue
            if (text.isBlank()) continue

            val linkDomain = Uri.parse(href).host
            if (linkDomain != null && linkDomain != homeDomain && !href.startsWith("/")) continue

            val normalizedHref = if (href.startsWith("/")) {
                val base = homeUrl.trimEnd('/')
                "$base$href"
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

                val linkDomain = Uri.parse(href).host
                if (linkDomain != null && linkDomain != homeDomain && !href.startsWith("/")) continue

                val normalizedHref = if (href.startsWith("/")) {
                    val base = homeUrl.trimEnd('/')
                    "$base$href"
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
            val num = extractChapterNumber(title, url)
            result.add(ChapterLink(title = title, url = normalized, chapterNumber = num))
        }
        return result.distinctBy { it.url }
    }

    private fun extractChapterNumber(title: String, url: String): Int {
        val fileName = fileNameFromUrl(url, Int.MAX_VALUE)
        return ChapterNumberExtractor.extract(title = title, url = url, fileName = fileName)
    }

    private fun fileNameFromUrl(url: String, chapterNumber: Int): String {
        return StringUtils.fileNameFromUrl(url, "chapter_$chapterNumber")
    }

    private fun extractCoverUrl(doc: Document, homeUrl: String): String? {
        val ogImage = doc.select("meta[property=og:image]").first()?.attr("content")
        if (ogImage != null) return makeAbsolute(ogImage, homeUrl)
        val twitterImage = doc.select("meta[name=twitter:image]").first()?.attr("content")
        if (twitterImage != null) return makeAbsolute(twitterImage, homeUrl)
        val firstImage = doc.select("img[src]").firstOrNull {
            val src = it.attr("src")
            src.isNotBlank() && !src.contains("logo", ignoreCase = true)
                    && !src.contains("icon", ignoreCase = true)
                    && !src.contains("avatar", ignoreCase = true)
                    && !src.contains("banner", ignoreCase = true)
        }
        if (firstImage != null) return makeAbsolute(firstImage.attr("src"), homeUrl)
        return null
    }

    private fun makeAbsolute(url: String, base: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val baseUrl = base.trimEnd('/')
        return if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/$url"
    }

    private fun extractNovelTitle(doc: Document): String? {
        val ogTitle = doc.select("meta[property=og:title]").first()?.attr("content")?.trim()
        if (!ogTitle.isNullOrBlank()) return ogTitle
        val twitterTitle = doc.select("meta[name=twitter:title]").first()?.attr("content")?.trim()
        if (!twitterTitle.isNullOrBlank()) return twitterTitle
        val titleTag = doc.title().trim()
        if (titleTag.isNotBlank()) {
            val cleaned = TitleExtractor.cleanHtmlTitle(titleTag)
            if (cleaned.isNotBlank()) return cleaned
        }
        val h1 = doc.select("h1").firstOrNull { it.text().trim().length > 2 }
        if (h1 != null) return h1.text().trim()
        return null
    }
}
