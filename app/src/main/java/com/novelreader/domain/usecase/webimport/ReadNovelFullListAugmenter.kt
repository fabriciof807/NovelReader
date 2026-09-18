package com.novelreader.domain.usecase.webimport

import android.util.Log
import com.novelreader.BuildConfig
import com.novelreader.domain.usecase.ChapterLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadNovelFullListAugmenter @Inject constructor() : NovelListAugmenter {

    override fun canAugment(homeUrl: String): Boolean = try {
        val host = URI(homeUrl).host?.removePrefix("www.") ?: return false
        host == "readnovelfull.com"
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
        val novelId = homeDoc.selectFirst("[data-novel-id]")?.attr("data-novel-id")?.toIntOrNull()
        if (novelId == null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "augment: missing data-novel-id in $homeUrl")
            }
            return emptyList()
        }
        val uri = URI(homeUrl)
        val ajaxUrl = "${uri.scheme}://${uri.authority}/ajax/chapter-archive?novelId=$novelId"
        if (!budget.tryConsume()) return emptyList()
        val resp = httpClient.get(
            url = ajaxUrl,
            referrer = homeUrl,
            extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest"),
            policy = policy
        )
        if (resp.statusCode != 200) return emptyList()
        if (!resp.body.contains("href")) return emptyList()
        val fragment = Jsoup.parseBodyFragment(resp.body)
        val homeDomain = uri.host?.removePrefix("www.") ?: ""
        return extractChapterLinks(fragment, ajaxUrl, homeDomain)
    }

    private companion object {
        const val TAG = "WebFetchProbe"
    }
}
