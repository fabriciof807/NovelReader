package com.novelreader.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.jsoup.Jsoup

class ParserRegistryTest {

    private val freeWebNovel = FreeWebNovelParser()
    private val fallback = GenericFallbackParser()
    private val mhtParser = MhtParser()
    private val registry = ParserRegistry(
        parsers = setOf(freeWebNovel),
        fallbackParser = fallback,
        mhtParser = mhtParser
    )

    @Test fun `routing returns FreeWebNovelParser for freewebnovel domain`() {
        val parser = registry.getParserForDomain("freewebnovel.com")
        assertThat(parser).isInstanceOf(FreeWebNovelParser::class.java)
    }

    @Test fun `routing returns GenericFallbackParser for unknown domain`() {
        val parser = registry.getParserForDomain("example.com")
        assertThat(parser).isInstanceOf(GenericFallbackParser::class.java)
    }

    @Test fun `routing rejects a lookalike domain and falls back`() {
        val parser = registry.getParserForDomain("freewebnovel.com.evil.io")
        assertThat(parser).isInstanceOf(GenericFallbackParser::class.java)
    }

    @Test fun `routing is deterministic when several parsers match`() {
        val first = ParserRegistry(
            parsers = setOf(MatchingParserA(), MatchingParserB()),
            fallbackParser = fallback,
            mhtParser = mhtParser
        )
        val second = ParserRegistry(
            parsers = setOf(MatchingParserB(), MatchingParserA()),
            fallbackParser = fallback,
            mhtParser = mhtParser
        )

        assertThat(first.getParserForDomain("example.com")).isInstanceOf(MatchingParserA::class.java)
        assertThat(second.getParserForDomain("example.com")).isInstanceOf(MatchingParserA::class.java)
    }

    private class MatchingParserA : NovelParser {
        override fun canParse(domain: String): Boolean = domain == "example.com"
        override fun parse(doc: org.jsoup.nodes.Document, fileName: String) =
            ParsedChapter("A", "A", "A")
    }

    private class MatchingParserB : NovelParser {
        override fun canParse(domain: String): Boolean = domain == "example.com"
        override fun parse(doc: org.jsoup.nodes.Document, fileName: String) =
            ParsedChapter("B", "B", "B")
    }

    @Test fun `parse produces ParsedChapter with content`() {
        val html = """
            <html><head><title>Some Book | Ch 1</title></head>
            <body><article><p>Hello</p></article></body></html>
        """.trimIndent()
        val parsed = registry.parse(html, "chapter_1.html")
        assertThat(parsed.content).contains("Hello")
        assertThat(parsed.novelTitle).isEqualTo("Some Book")
    }

    @Test fun `parseRaw with MHT extracts html and uses subject for titles`() {
        val mht = buildString {
            appendLine("From: <a@b>")
            appendLine("Subject: Cool Book | Chapter 7")
            appendLine("Content-Type: multipart/related; boundary=BOUND")
            appendLine("")
            appendLine("--BOUND")
            appendLine("Content-Type: text/html; charset=utf-8")
            appendLine("Content-Transfer-Encoding: 7bit")
            appendLine("")
            appendLine("<html><body><p>Body</p></body></html>")
            appendLine("--BOUND--")
        }
        val parsed = registry.parseRaw(mht, "chapter_7.mht")
        assertThat(parsed.content).contains("Body")
        assertThat(parsed.novelTitle).isEqualTo("Cool Book")
        assertThat(parsed.chapterTitle).isEqualTo("Chapter 7")
    }

    @Test fun `parseRaw with non-MHT delegates to parse`() {
        val html = "<html><head><title>Plain | Ch 1</title></head><body><p>x</p></body></html>"
        val parsed = registry.parseRaw(html, "x.html")
        assertThat(parsed.novelTitle).isEqualTo("Plain")
    }

    private fun buildString(block: StringBuilder.() -> Unit): String {
        val sb = StringBuilder()
        sb.block()
        return sb.toString()
    }
}
