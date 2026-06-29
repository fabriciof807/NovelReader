package com.novelreader.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.jsoup.Jsoup

class FreeWebNovelParserTest {

    private val parser = FreeWebNovelParser()

    @Test fun `canParse returns true for freewebnovel domain`() {
        assertThat(parser.canParse("freewebnovel.com")).isTrue()
        assertThat(parser.canParse("m.freewebnovel.com")).isTrue()
    }

    @Test fun `canParse returns false for other domains`() {
        assertThat(parser.canParse("example.com")).isFalse()
        assertThat(parser.canParse("novel.com.br")).isFalse()
    }

    @Test fun `parses novel title from pipe-separated title tag`() {
        val html = """
            <html><head><title>My Novel - Chapter 1 | FreeWebNovel</title></head>
            <body><div class='chapter-content'><p>content</p></div></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_1.html")
        assertThat(parsed.novelTitle).isEqualTo("My Novel")
    }

    @Test fun `parses chapter title from title tag`() {
        val html = """
            <html><head><title>My Novel - Chapter 1 | FreeWebNovel</title></head>
            <body><div class='chapter-content'><p>content</p></div></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_1.html")
        // canonical 'Novel - Chapter N | SiteName' pattern: strip site suffix, keep chapter part
        assertThat(parsed.chapterTitle).isEqualTo("Chapter 1")
    }

    @Test fun `extracts content from chapter-content div`() {
        val html = """
            <html><head><title>Novel - Ch1</title></head>
            <body><div class='chapter-content'><p>Body text</p></div></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "x.html")
        assertThat(parsed.content).contains("Body text")
    }

    @Test fun `falls back to paragraph extraction when chapter-content missing`() {
        val html = """
            <html><head><title>Novel - Ch1</title></head>
            <body><h1>Chapter 1</h1><p>Para A</p><p>Para B</p></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "x.html")
        assertThat(parsed.content).contains("Para A")
        assertThat(parsed.content).contains("Para B")
    }

    @Test fun `parses chapter title with en-dash separator stripping novel name`() {
        val html = """
            <html><head><title>Cultivation Novel – Chapter 5 – The Trial</title></head><body>
            <div class="chapter-content"><p>content</p></div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_5.html")
        assertThat(parsed.novelTitle).isEqualTo("Cultivation Novel")
        assertThat(parsed.chapterTitle).isEqualTo("Chapter 5 – The Trial")
    }

    @Test fun `parses chapter title with pipe separator stripping novel name`() {
        val html = """
            <html><head><title>Cultivation Novel | Chapter 5 | The Trial</title></head><body>
            <div class="chapter-content"><p>content</p></div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_5.html")
        assertThat(parsed.novelTitle).isEqualTo("Cultivation Novel")
        assertThat(parsed.chapterTitle).isEqualTo("Chapter 5")
    }
}
