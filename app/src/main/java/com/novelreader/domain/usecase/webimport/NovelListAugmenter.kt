package com.novelreader.domain.usecase.webimport

import com.novelreader.domain.usecase.ChapterLink
import org.jsoup.nodes.Document

interface NovelListAugmenter {
    fun canAugment(homeUrl: String): Boolean
    suspend fun augment(
        homeUrl: String,
        homeDoc: Document,
        httpClient: HttpClient
    ): List<ChapterLink>
}
