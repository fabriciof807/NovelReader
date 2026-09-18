package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.ChapterLink
import org.jsoup.nodes.Document
import org.junit.Test

class NovelListAugmenterTest {

    private class FakeAug(val match: Boolean) : NovelListAugmenter {
        override fun canAugment(homeUrl: String): Boolean = match
        override suspend fun augment(
            homeUrl: String,
            homeDoc: Document,
            httpClient: HttpClient,
            policy: RemoteRequestPolicy.SameNovelDomain,
            budget: RequestBudget
        ): List<ChapterLink> = emptyList()
    }

    @Test
    fun `canAugment dispatches based on its own predicate`() {
        val matching = FakeAug(match = true)
        val nonMatching = FakeAug(match = false)

        assertThat(matching.canAugment("https://example.com/")).isTrue()
        assertThat(nonMatching.canAugment("https://example.com/")).isFalse()
    }
}
