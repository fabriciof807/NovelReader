package com.novelreader.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.jsoup.Jsoup

class ReadNovelFullParserTest {

    private val parser = ReadNovelFullParser()

    @Test fun `canParse returns true for readnovelfull domain`() {
        assertThat(parser.canParse("readnovelfull.com")).isTrue()
        assertThat(parser.canParse("www.readnovelfull.com")).isTrue()
    }

    @Test fun `canParse returns false for other domains`() {
        assertThat(parser.canParse("example.com")).isFalse()
        assertThat(parser.canParse("freewebnovel.com")).isFalse()
    }

    @Test fun `canParse rejects lookalike domains`() {
        assertThat(parser.canParse("evilreadnovelfull.com")).isFalse()
        assertThat(parser.canParse("readnovelfull.com.evil.io")).isFalse()
        assertThat(parser.canParse("notreadnovelfull.com")).isFalse()
    }

    @Test fun `parses novel title from h3 with itemprop`() {
        val html = """
            <html><head></head><body>
            <h3 class="title" itemprop="name">Martial Peak</h3>
            <div id="chr-content"><p>content</p></div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_1.html")
        assertThat(parsed.novelTitle).isEqualTo("Martial Peak")
    }

    @Test fun `parses chapter title from chr-text span`() {
        val html = """
            <html><head></head><body>
            <h3 class="title" itemprop="name">Martial Peak</h3>
            <div class="chr-title"><h2><a class="chr-title" href="/mp/ch1.html">
            <span class="chr-text">Chapter 1 - The servant who sweeps</span></a></h2></div>
            <div id="chr-content"><p>content</p></div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_1.html")
        assertThat(parsed.chapterTitle).isEqualTo("Chapter 1 - The servant who sweeps")
    }

    @Test fun `extracts content from chr-content div`() {
        val html = """
            <html><head></head><body>
            <h3 class="title" itemprop="name">Martial Peak</h3>
            <div id="chr-content" class="chr-c">
            <p>First paragraph of the chapter.</p>
            <p>Second paragraph of the chapter.</p>
            </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "x.html")
        assertThat(parsed.content).contains("First paragraph")
        assertThat(parsed.content).contains("Second paragraph")
    }

    @Test fun `falls back to paragraphs when chr-content missing`() {
        val html = """
            <html><head><title>Martial Peak - Chapter 1</title></head>
            <body><h1>Chapter 1</h1><p>Para A</p><p>Para B</p></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_1.html")
        assertThat(parsed.content).contains("Para A")
        assertThat(parsed.content).contains("Para B")
    }

    @Test fun `parses novel title from fallback when h3 missing`() {
        val html = """
            <html><head><title>Martial Peak - Chapter 1</title></head>
            <body><div id="chr-content"><p>content</p></div></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_1.html")
        assertThat(parsed.novelTitle).isEqualTo("Martial Peak")
    }

    @Test fun `parses chapter title from h2 when chr-text missing`() {
        val html = """
            <html><head><title>Novel - Chapter 1</title></head>
            <body><h2>Chapter 1 - Title</h2><div id="chr-content"><p>content</p></div></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "x.html")
        assertThat(parsed.chapterTitle).isEqualTo("Chapter 1 - Title")
    }

    @Test fun `parses chapter title with em-dash separator stripping novel name`() {
        val html = """
            <html><head><title>Martial Peak — Chapter 5 — The Trial</title></head><body>
            <h3 class="title" itemprop="name">Martial Peak</h3>
            <div id="chr-content"><p>content</p></div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_5.html")
        assertThat(parsed.novelTitle).isEqualTo("Martial Peak")
        assertThat(parsed.chapterTitle).isEqualTo("Chapter 5 — The Trial")
    }
}
