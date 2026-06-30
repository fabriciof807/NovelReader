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

    override fun canAugment(homeUrl: String): Boolean = try {
        val host = URI(homeUrl).host?.removePrefix("www.") ?: return false
        host == "freewebnovel.com"
    } catch (_: Exception) {
        false
    }

    override suspend fun augment(
        homeUrl: String,
        homeDoc: Document,
        httpClient: HttpClient
    ): List<ChapterLink> {
        val state = extractChapterPaginationState(homeDoc) ?: return emptyList()
        if (state.totalPage <= 1) return emptyList()
        val all = mutableListOf<ChapterLink>()
        val homeDomain = (URI(homeUrl).host ?: "").removePrefix("www.")
        for (page in 2..state.totalPage) {
            val ajaxUrl = buildChapterPaginationUrl(homeUrl, page, state.pageSize) ?: break
            val resp = httpClient.get(
                url = ajaxUrl,
                referrer = homeUrl,
                extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest")
            )
            if (resp.statusCode != 200) continue
            val parsed = parseChapterPaginationJson(resp.body) ?: continue
            if (parsed.code != 200) continue
            val fragment = Jsoup.parseBodyFragment(parsed.html)
            all += extractChapterLinks(fragment, homeUrl, homeDomain)
            delay(PAGE_DELAY_MS)
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
    }
}
