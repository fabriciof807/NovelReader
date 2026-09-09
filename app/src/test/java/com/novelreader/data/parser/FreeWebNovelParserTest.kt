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

    @Test fun `canParse rejects lookalike domains`() {
        assertThat(parser.canParse("evilfreewebnovel.com")).isFalse()
        assertThat(parser.canParse("freewebnovel.com.evil.io")).isFalse()
        assertThat(parser.canParse("notfreewebnovel.com")).isFalse()
        assertThat(parser.canParse("freewebnovel.com.br")).isFalse()
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

    @Test fun `parses real fixture chapter page - novel title from h1 tit`() {
        val html = java.io.File("src/test/resources/freewebnovel/chapter1_child_of_destiny.html").readText()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter-1")
        assertThat(parsed.novelTitle).isEqualTo("Child of Destiny")
    }

    @Test fun `parses real fixture chapter page - chapter title from h1 tit`() {
        val html = java.io.File("src/test/resources/freewebnovel/chapter1_child_of_destiny.html").readText()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter-1")
        assertThat(parsed.chapterTitle).isEqualTo("Chapter 1 Only One Alive")
    }

    @Test fun `parses real fixture chapter page - content is non-empty and has real paragraphs`() {
        val html = java.io.File("src/test/resources/freewebnovel/chapter1_child_of_destiny.html").readText()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter-1")
        assertThat(parsed.content).isNotEmpty()
        assertThat(parsed.content.length).isGreaterThan(1000)
        assertThat(parsed.content).contains("White Village")
        assertThat(parsed.content).contains("January 17")
        assertThat(parsed.content).doesNotContain("read-ads")
        assertThat(parsed.content).doesNotContain("Page not found")
    }

    @Test fun `parses 404 page gracefully - content does not contain 'Page not found' text`() {
        val html = java.io.File("src/test/resources/freewebnovel/notfound_chapter_old_format.html").readText()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "child-of-destiny/chapter-1.html")
        assertThat(parsed.content).doesNotContain("Page not found")
    }
}
