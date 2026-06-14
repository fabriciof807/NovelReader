package com.novelreader.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.jsoup.Jsoup

class GenericFallbackParserTest {

    private val parser = GenericFallbackParser()

    @Test fun `canParse returns true for any domain`() {
        assertThat(parser.canParse("anything.com")).isTrue()
        assertThat(parser.canParse("")).isTrue()
    }

    @Test fun `parses title with pipe separator`() {
        val html = """
            <html><head><title>My Novel | Chapter 1</title></head>
            <body><p>content</p></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "chapter_1.html")
        assertThat(parsed.novelTitle).isEqualTo("My Novel")
        assertThat(parsed.chapterTitle).isEqualTo("Chapter 1")
    }

    @Test fun `parses title with dash separator`() {
        val html = """
            <html><head><title>Cool Book - Chapter 5</title></head>
            <body></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "x.html")
        assertThat(parsed.novelTitle).isEqualTo("Cool Book")
    }

    @Test fun `falls back to og_title meta`() {
        val html = """
            <html><head>
                <meta property='og:title' content='Meta Title' />
            </head><body></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "x.html")
        assertThat(parsed.novelTitle).isEqualTo("Meta Title")
    }

    @Test fun `extracts content from entry-content div`() {
        val html = """
            <html><head><title>Book | Ch1</title></head>
            <body><div class='entry-content'><p>Para1</p><p>Para2</p></div></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "x.html")
        assertThat(parsed.content).contains("Para1")
        assertThat(parsed.content).contains("Para2")
    }

    @Test fun `uses filename when no title is found`() {
        val html = "<html><body><p>only body</p></body></html>"
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "my_great_novel.html")
        assertThat(parsed.novelTitle).isEqualTo("my great novel")
    }
}
