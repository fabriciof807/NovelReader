package com.novelreader.domain.usecase.webimport

import com.novelreader.domain.usecase.ChapterLink
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreewebnovelListAugmenter @Inject constructor() : NovelListAugmenter {

    @androidx.annotation.VisibleForTesting
    internal var delayFn: suspend (Long) -> Unit = { delay(it) }

    override fun canAugment(homeUrl: String): Boolean = try {
        val host = URI(homeUrl).host?.removePrefix("www.") ?: return false
        host == "freewebnovel.com"
    } catch (_: Exception) {
        false
    }

    override suspend fun augment(
        homeUrl: String,
        homeDoc: Document,
        httpClient: HttpClient,
        policy: RemoteRequestPolicy.SameNovelDomain,
        budget: RequestBudget
    ): List<ChapterLink> {
        val state = extractChapterPaginationState(homeDoc) ?: return emptyList()
        if (state.pageSize !in 1..MAX_PAGE_SIZE || state.totalPage <= 1) return emptyList()
        val all = mutableListOf<ChapterLink>()
        val homeDomain = (URI(homeUrl).host ?: "").removePrefix("www.")
        var consecutiveFailures = 0
        for (page in 2..state.totalPage) {
            if (!budget.tryConsume() || all.size >= MAX_LINKS) break
            val ajaxUrl = buildChapterPaginationUrl(homeUrl, page, state.pageSize) ?: break
            val succeeded = runCatching {
                val response = httpClient.get(
                    url = ajaxUrl,
                    referrer = homeUrl,
                    extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    policy = policy
                )
                if (response.statusCode != 200) return@runCatching false
                val parsed = parseChapterPaginationJson(response.body) ?: return@runCatching false
                if (parsed.code != 200) return@runCatching false
                val links = extractChapterLinks(Jsoup.parseBodyFragment(parsed.html), homeUrl, homeDomain)
                all += links.take((MAX_LINKS - all.size).coerceAtLeast(0))
                true
            }.getOrDefault(false)

            consecutiveFailures = if (succeeded) 0 else consecutiveFailures + 1
            delayFn(PAGE_DELAY_MS)
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) break
        }
        return all
    }

    private fun buildChapterPaginationUrl(homeUrl: String, page: Int, pageSize: Int): String? = try {
        val uri = URI(homeUrl)
        val query = uri.rawQuery.orEmpty()
        val separator = if (query.isEmpty()) "?" else "&"
        val params = "ajax=chapters&page=$page&pageSize=$pageSize"
        val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""
        "${uri.scheme}://${uri.authority}${uri.path.orEmpty()}$separator$params$fragment"
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val PAGE_DELAY_MS = 1_500L
        const val MAX_PAGE_SIZE = 200
        const val MAX_LINKS = 10_000
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}
