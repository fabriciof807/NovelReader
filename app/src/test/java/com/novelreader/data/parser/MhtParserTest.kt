package com.novelreader.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MhtParserTest {

    private val parser = MhtParser()

    @Test
    fun parse_validMht() {
        val mht = buildMht(
            boundary = "----=_NextPart_001",
            contentType = "text/html; charset=utf-8",
            body = "<html><head><title>The Lost Kingdom</title></head><body><h1>Chapter 1</h1><p>Story begins here.</p></body></html>"
        )
        val result = parser.extractHtml(mht)
        assertThat(result).isNotNull()
        assertThat(result).contains("The Lost Kingdom")
        assertThat(result).contains("Chapter 1")
    }

    @Test
    fun parse_multipartBoundary() {
        val mht = buildMultipartMht(
            boundary = "----=_Part_42",
            parts = listOf(
                "image/png" to "BINARYDATAHERE",
                "text/html; charset=utf-8" to "<html><body><p>Second part content</p></body></html>"
            )
        )
        val result = parser.extractHtml(mht)
        assertThat(result).isNotNull()
        assertThat(result).contains("Second part content")
    }

    @Test
    fun parse_charsetInMeta() {
        val html = """<html><head><meta charset="iso-8859-1"><title>Test</title></head><body><p>cafe</p></body></html>"""
        val mht = buildMht(
            boundary = "----=_Boundary",
            contentType = "text/html; charset=iso-8859-1",
            body = html
        )
        val result = parser.extractHtml(mht)
        assertThat(result).isNotNull()
    }

    @Test
    fun parse_emptyContent() {
        val mht = buildMht(
            boundary = "----=_Empty",
            contentType = "text/html; charset=utf-8",
            body = ""
        )
        val result = parser.extractHtml(mht)
        assertThat(result).isEmpty()
    }

    @Test
    fun parse_binaryAndHtml() {
        val mht = buildMultipartMht(
            boundary = "----=_Mixed",
            parts = listOf(
                "application/zip" to "PKZIPBINARYDATA",
                "text/html" to "<html><body><p>Real content</p></body></html>"
            )
        )
        val result = parser.extractHtml(mht)
        assertThat(result).isNotNull()
        assertThat(result).contains("Real content")
    }

    @Test
    fun extractSubject() {
        val mht = buildMht(
            boundary = "----=_Subj",
            contentType = "text/html; charset=utf-8",
            body = "<html><body><p>Content</p></body></html>"
        ).replace("Subject: Test", "Subject: The Lost Kingdom - Chapter 1")
        val subject = parser.extractSubject(mht)
        assertThat(subject).isEqualTo("The Lost Kingdom - Chapter 1")
    }

    @Test
    fun isMhtFile_detectsExtensions() {
        assertThat(parser.isMhtFile("file.mht")).isTrue()
        assertThat(parser.isMhtFile("file.mhtml")).isTrue()
        assertThat(parser.isMhtFile("file.MHT")).isTrue()
        assertThat(parser.isMhtFile("file.html")).isFalse()
        assertThat(parser.isMhtFile("file.htm")).isFalse()
        assertThat(parser.isMhtFile("file.txt")).isFalse()
    }

    private fun buildMht(boundary: String, contentType: String, body: String): String = """
From: saved by NovelReader
Subject: Test
Content-Type: multipart/related; boundary="$boundary"
MIME-Version: 1.0

--$boundary
Content-Type: $contentType
Content-Transfer-Encoding: 8bit
Content-Location: main

$body
--$boundary--
""".trimIndent()

    private fun buildMultipartMht(boundary: String, parts: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.appendLine("""From: saved by NovelReader""")
        sb.appendLine("""Subject: Test""")
        sb.appendLine("""Content-Type: multipart/related; boundary="$boundary"""")
        sb.appendLine("""MIME-Version: 1.0""")
        sb.appendLine()
        for ((contentType, body) in parts) {
            sb.appendLine("--$boundary")
            sb.appendLine("Content-Type: $contentType")
            sb.appendLine("Content-Transfer-Encoding: 8bit")
            sb.appendLine()
            sb.appendLine(body)
        }
        sb.appendLine("--$boundary--")
        return sb.toString()
    }
}
