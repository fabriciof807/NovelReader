package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import org.jsoup.Jsoup
import org.junit.Test

class ChapterLinkExtractorTest {

    @Test
    fun resolvesRelativeHrefAgainstOrigin() {
        val homeUrl = "https://freewebnovel.com/novel/child-of-destiny"
        val homeDomain = "freewebnovel.com"
        val html = """
            <ul>
                <li><a href="/novel/child-of-destiny/chapter-1">Chapter 1 Only One Alive</a></li>
                <li><a href="/novel/child-of-destiny/chapter-2">Chapter 2 Shin Kinghad</a></li>
            </ul>
        """.trimIndent()
        val doc = Jsoup.parse(html)

        val links = extractChapterLinks(doc, homeUrl, homeDomain)

        assertThat(links).hasSize(2)
        assertThat(links[0].url).isEqualTo("https://freewebnovel.com/novel/child-of-destiny/chapter-1")
        assertThat(links[1].url).isEqualTo("https://freewebnovel.com/novel/child-of-destiny/chapter-2")
    }

    @Test
    fun doesNotDuplicateSlugInUrl() {
        val homeUrl = "https://freewebnovel.com/novel/reborn-in-a-perverse-monster-world-my-system-adapts-to-everything"
        val homeDomain = "freewebnovel.com"
        val html = """
            <ul>
                <li><a href="/novel/reborn-in-a-perverse-monster-world-my-system-adapts-to-everything/chapter-54">Chapter 54: Mira.</a></li>
            </ul>
        """.trimIndent()
        val doc = Jsoup.parse(html)

        val links = extractChapterLinks(doc, homeUrl, homeDomain)

        assertThat(links).hasSize(1)
        assertThat(links[0].url).doesNotContain("//novel/")
        assertThat(links[0].url).isEqualTo(
            "https://freewebnovel.com/novel/reborn-in-a-perverse-monster-world-my-system-adapts-to-everything/chapter-54"
        )
    }
}
