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

private const val MAX_REQUESTS = 50
private const val PAGE_DELAY_MS = 1_500L
private const val DIAGNOSTIC_TAG = "WebFetchProbe"
private const val BODY_SNIPPET_MAX = 256

@Singleton
class ChapterCrawler @Inject constructor(
    private val httpClient: HttpClient,
    private val augmenters: Set<@JvmSuppressWildcards NovelListAugmenter>
) {
    @androidx.annotation.VisibleForTesting
    constructor(httpClient: HttpClient, requireHttps: Boolean) : this(httpClient, emptySet()) {
        this.requireHttps = requireHttps
    }

    @androidx.annotation.VisibleForTesting
    constructor(httpClient: HttpClient, augmenters: Set<NovelListAugmenter>, requireHttps: Boolean) : this(httpClient, augmenters) {
        this.requireHttps = requireHttps
    }

    private var requireHttps: Boolean = true

    suspend fun crawlChapterList(homeUrl: String): CrawlResult {
        val expectedHost = hostOf(homeUrl)
        require(expectedHost.isNotBlank()) { "Invalid novel host" }
        val policy = RemoteRequestPolicy.SameNovelDomain(expectedHost)
        val budget = RequestBudget(MAX_REQUESTS)
        val allLinks = mutableListOf<ChapterLink>()
        val attemptedUrls = mutableListOf<String>()
        var currentUrl: String? = homeUrl
        var coverUrl: String? = null
        var novelTitle: String? = null
        var pageCount = 0
        var lastFetchedStatus: Int = 0
        var lastFetchedBody: String = ""
        var lastFetchedFinalUrl: String? = null
        var firstPageDoc: Document? = null

        while (currentUrl != null) {
            if (!budget.tryConsume()) break
            val fetched = fetchPage(currentUrl, policy)
            attemptedUrls.add(currentUrl)
            lastFetchedStatus = fetched.statusCode
            lastFetchedBody = fetched.body
            lastFetchedFinalUrl = fetched.finalUrl
            val doc = fetched.document
            if (pageCount == 0) {
                coverUrl = extractCoverUrl(doc, homeUrl, expectedHost)
                novelTitle = extractNovelTitle(doc)
                firstPageDoc = doc
            }
            allLinks.addAll(extractChapterLinks(doc, currentUrl, expectedHost))
            currentUrl = findNextPageUrl(doc, currentUrl, expectedHost)
            pageCount++
            if (currentUrl != null) delay(PAGE_DELAY_MS)
        }

        firstPageDoc?.let { homeDoc ->
            for (augmenter in augmenters) {
                if (augmenter.canAugment(homeUrl)) {
                    val augmented = augmenter.augment(homeUrl, homeDoc, httpClient, policy, budget)
                    allLinks += resolveSameDomainLinks(augmented, homeUrl, expectedHost)
                }
            }
        }

        val links = allLinks.distinctBy { it.url }
        if (BuildConfig.DEBUG) {
            Log.w(
                DIAGNOSTIC_TAG,
                "chapterList: homeUrl=$homeUrl totalFound=${links.size} attemptedUrls=${attemptedUrls.size}"
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

    private data class FetchedPage(
        val document: Document,
        val statusCode: Int,
        val body: String,
        val finalUrl: String?
    )

    private suspend fun fetchPage(url: String, policy: RemoteRequestPolicy): FetchedPage {
        if (requireHttps && !url.startsWith("https://")) throw SecurityException("Apenas HTTPS permitido")
        val response = httpClient.get(url, policy = policy)
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

    private fun findNextPageUrl(doc: Document, currentUrl: String, expectedHost: String): String? {
        val nextTexts = listOf("next", "próxima", "proximo", ">", "»", "›")
        val nextSelectors = listOf("a.next", "a[rel=next]", ".pagination a", ".pager a", "a[aria-label*=next]", "a[aria-label*=Next]")

        for (selector in nextSelectors) {
            val el = doc.selectFirst(selector) ?: continue
            val resolved = resolveDiscoveredLink(el.attr("href"), currentUrl, expectedHost) ?: continue
            if (resolved != currentUrl) {
                return resolved
            }
        }

        for (link in doc.select("a[href]")) {
            val text = link.text().trim().lowercase()
            val ariaLabel = (link.attr("aria-label") ?: "").lowercase()
            if (nextTexts.any { text == it || ariaLabel.contains(it) }) {
                val resolved = resolveDiscoveredLink(link.attr("href"), currentUrl, expectedHost) ?: continue
                if (resolved != currentUrl) {
                    return resolved
                }
            }
        }

        return null
    }

    private fun resolveDiscoveredLink(href: String, baseUrl: String, expectedHost: String): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        return resolveSameDomain(trimmed, baseUrl, expectedHost)
    }

    private fun extractChapterLinks(doc: Document, baseUrl: String, expectedHost: String): List<ChapterLink> {
        return resolveSameDomainLinks(
            com.novelreader.domain.usecase.webimport.extractChapterLinks(doc, baseUrl, expectedHost),
            baseUrl,
            expectedHost
        )
    }

    private fun resolveSameDomainLinks(
        links: List<ChapterLink>,
        baseUrl: String,
        expectedHost: String
    ): List<ChapterLink> = links.mapNotNull { link ->
        val resolved = resolveSameDomain(link.url, baseUrl, expectedHost) ?: return@mapNotNull null
        link.copy(url = resolved)
    }

    private fun fileNameFromUrl(url: String, chapterNumber: Int): String {
        return StringUtils.fileNameFromUrl(url, "chapter_$chapterNumber")
    }

    private fun extractCoverUrl(doc: Document, homeUrl: String, expectedHost: String): String? {
        val ogImage = doc.select("meta[property=og:image]").first()?.attr("content")
        if (ogImage != null) return resolveDiscoveredLink(ogImage, homeUrl, expectedHost)
        val twitterImage = doc.select("meta[name=twitter:image]").first()?.attr("content")
        if (twitterImage != null) return resolveDiscoveredLink(twitterImage, homeUrl, expectedHost)
        val firstImage = doc.select("img[src]").firstOrNull {
            val src = it.attr("src")
            src.isNotBlank() && !src.contains("logo", ignoreCase = true)
                    && !src.contains("icon", ignoreCase = true)
                    && !src.contains("avatar", ignoreCase = true)
                    && !src.contains("banner", ignoreCase = true)
        }
        if (firstImage != null) return resolveDiscoveredLink(firstImage.attr("src"), homeUrl, expectedHost)
        return null
    }

    @androidx.annotation.VisibleForTesting
    internal fun resolveSameDomain(url: String, baseUrl: String, expectedHost: String): String? {
        val absolute = upgradeToHttps(resolveAgainstBase(url, baseUrl) ?: return null, baseUrl)
        val resolved = runCatching { URI(absolute) }.getOrNull() ?: return null
        val scheme = resolved.scheme?.lowercase()
        if (scheme !in setOf("http", "https")) return null
        if (requireHttps && scheme != "https") return null
        val host = resolved.host ?: return null
        if (!StringUtils.hostMatchesDomain(host, expectedHost)) return null
        return absolute
    }

    @androidx.annotation.VisibleForTesting
    internal fun makeAbsolute(url: String, base: String): String {
        val absolute = resolveAgainstBase(url, base) ?: return url
        return upgradeToHttps(absolute, base)
    }

    private fun resolveAgainstBase(url: String, base: String): String? =
        runCatching { URI(base).resolve(url).toString() }.getOrNull()

    private fun upgradeToHttps(url: String, base: String): String =
        if (base.startsWith("https://") && url.startsWith("http://")) {
            "https://" + url.removePrefix("http://")
        } else {
            url
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
