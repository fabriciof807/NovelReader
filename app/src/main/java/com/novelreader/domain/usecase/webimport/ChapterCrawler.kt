package com.novelreader.domain.usecase.webimport

import android.util.Log
import com.novelreader.BuildConfig
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.data.parser.ChapterNumberExtractor
import com.novelreader.data.parser.TitleExtractor
import com.novelreader.util.StringUtils
import kotlinx.coroutines.delay
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class CrawlResult(
    val links: List<ChapterLink>,
    val coverUrl: String?,
    val novelTitle: String?
)

private const val MAX_PAGES = 50
private const val PAGE_DELAY_MS = 1_500L
private const val DIAGNOSTIC_TAG = "WebFetchProbe"
private const val BODY_SNIPPET_MAX = 256

@Singleton
class ChapterCrawler @Inject constructor(
    private val httpClient: HttpClient
) {
    @androidx.annotation.VisibleForTesting
    constructor(httpClient: HttpClient, requireHttps: Boolean) : this(httpClient) {
        this.requireHttps = requireHttps
    }

    private var requireHttps: Boolean = true

    suspend fun crawlChapterList(homeUrl: String): CrawlResult {
        val homeDomain = hostOf(homeUrl)
        val allLinks = mutableListOf<ChapterLink>()
        val attemptedUrls = mutableListOf<String>()
        var currentUrl: String? = homeUrl
        var coverUrl: String? = null
        var novelTitle: String? = null
        var pageCount = 0
        var lastFetchedStatus: Int = 0
        var lastFetchedBody: String = ""
        var lastFetchedFinalUrl: String? = null
        var paginationState: ChapterPaginationState? = null

        while (currentUrl != null && pageCount < MAX_PAGES) {
            val fetched = fetchPage(currentUrl)
            attemptedUrls.add(currentUrl)
            lastFetchedStatus = fetched.statusCode
            lastFetchedBody = fetched.body
            lastFetchedFinalUrl = fetched.finalUrl
            val doc = fetched.document
            if (pageCount == 0) {
                coverUrl = extractCoverUrl(doc, homeUrl)
                novelTitle = extractNovelTitle(doc)
                paginationState = extractChapterPaginationState(doc)
            }
            allLinks.addAll(extractChapterLinks(doc, currentUrl, homeDomain))
            currentUrl = findNextPageUrl(doc, currentUrl)
            pageCount++
            if (currentUrl != null) delay(PAGE_DELAY_MS)
        }

        if (paginationState != null && paginationState.totalPage > 1) {
            for (page in 2..paginationState.totalPage) {
                val ajaxUrl = buildChapterPaginationUrl(currentUrl ?: homeUrl, page, paginationState.pageSize)
                if (ajaxUrl == null) break
                val resp = httpClient.get(
                    url = ajaxUrl,
                    referrer = homeUrl,
                    extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest")
                )
                attemptedUrls.add(ajaxUrl)
                lastFetchedStatus = resp.statusCode
                lastFetchedBody = resp.body
                val parsed = parseChapterPaginationJson(resp.body)
                if (parsed == null) {
                    if (BuildConfig.DEBUG) {
                        Log.w(DIAGNOSTIC_TAG, "chapterPagination JSON parse failed for $ajaxUrl")
                    }
                    break
                }
                val fragment = Jsoup.parseBodyFragment(parsed.html)
                allLinks.addAll(extractChapterLinks(fragment, homeUrl, homeDomain))
                delay(PAGE_DELAY_MS)
            }
        }

        val links = allLinks.distinctBy { it.url }
        if (BuildConfig.DEBUG) {
            Log.w(
                DIAGNOSTIC_TAG,
                "chapterList: homeUrl=$homeUrl totalFound=${links.size} attemptedUrls=${attemptedUrls.size} " +
                    "expectedTotal=${paginationState?.totalChapters ?: -1} totalPages=${paginationState?.totalPage ?: -1}"
            )
        }
        if (BuildConfig.DEBUG && links.isEmpty() && attemptedUrls.isNotEmpty()) {
            val diag = extractZeroLinksDiagnostic(
                callSite = "crawler",
                homeUrl = homeUrl,
                finalUrl = lastFetchedFinalUrl,
                statusCode = lastFetchedStatus,
                body = lastFetchedBody,
                attemptedUrls = attemptedUrls.toList()
            )
            Log.w(DIAGNOSTIC_TAG, formatZeroLinksDiagnostic(diag))
        }
        return CrawlResult(links = links, coverUrl = coverUrl, novelTitle = novelTitle)
    }

    private fun buildChapterPaginationUrl(currentUrl: String, page: Int, pageSize: Int): String? {
        return try {
            val uri = URI(currentUrl)
            val query = uri.rawQuery.orEmpty()
            val separator = if (query.isEmpty()) "?" else "&"
            val params = "ajax=chapters&page=$page&pageSize=$pageSize"
            val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""
            "${uri.scheme}://${uri.authority}${uri.path.orEmpty()}$separator$params$fragment"
        } catch (_: Exception) {
            null
        }
    }

    private data class FetchedPage(
        val document: Document,
        val statusCode: Int,
        val body: String,
        val finalUrl: String?
    )

    private suspend fun fetchPage(url: String): FetchedPage {
        if (requireHttps && !url.startsWith("https://")) throw SecurityException("Apenas HTTPS permitido")
        val response = httpClient.get(url)
        val statusCode = response.statusCode
        if (statusCode in 200..299) {
            return FetchedPage(
                document = Jsoup.parse(response.body, url),
                statusCode = statusCode,
                body = response.body,
                finalUrl = response.finalUrl
            )
        }
        if (BuildConfig.DEBUG) {
            val diag = extractWebFetchDiagnostic(
                throwable = HttpStatusException("HTTP error fetching URL", statusCode, url),
                callSite = "crawler",
                serverHeader = response.headers["Server"],
                cfRayHeader = response.headers["cf-ray"],
                setCookieHeader = response.headers["Set-Cookie"],
                bodySnippet = response.body.take(BODY_SNIPPET_MAX)
            )
            Log.w(DIAGNOSTIC_TAG, formatWebFetchDiagnostic(diag))
        }
        if (isCloudflareChallenge(statusCode, response.body, response.headers)) {
            throw CloudflareChallengeRequiredException(
                url = url,
                evidence = response.body.take(BODY_SNIPPET_MAX)
            )
        }
        throw HttpStatusException("HTTP error fetching URL", statusCode, url)
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
        return com.novelreader.domain.usecase.webimport.extractChapterLinks(doc, homeUrl, homeDomain)
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

    private fun hostOf(url: String): String = try { URI(url).host.orEmpty() } catch (_: Exception) { "" }

    private fun hostOfOrNull(url: String): String? = try { URI(url).host } catch (_: Exception) { null }

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
