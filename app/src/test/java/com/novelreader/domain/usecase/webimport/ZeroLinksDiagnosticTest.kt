package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZeroLinksDiagnosticTest {

    @Test
    fun extract_capturesHomeUrlStatusBodyAndAttemptedUrls() {
        val diag = extractZeroLinksDiagnostic(
            callSite = "crawler",
            homeUrl = "https://www.freewebnovel.com/novel/x",
            finalUrl = "https://www.freewebnovel.com/novel/x",
            statusCode = 200,
            body = "<html><title>Just a moment...</title></html>",
            attemptedUrls = listOf("https://www.freewebnovel.com/novel/x")
        )

        assertThat(diag.callSite).isEqualTo("crawler")
        assertThat(diag.homeUrl).isEqualTo("https://www.freewebnovel.com/novel/x")
        assertThat(diag.finalUrl).isEqualTo("https://www.freewebnovel.com/novel/x")
        assertThat(diag.statusCode).isEqualTo(200)
        assertThat(diag.bodyLength).isEqualTo("<html><title>Just a moment...</title></html>".length)
        assertThat(diag.bodySnippet).isEqualTo("<html><title>Just a moment...</title></html>")
        assertThat(diag.attemptedUrls).containsExactly("https://www.freewebnovel.com/novel/x")
    }

    @Test
    fun extract_handlesRedirectToDifferentFinalUrl() {
        val diag = extractZeroLinksDiagnostic(
            callSite = "crawler",
            homeUrl = "https://freewebnovel.com/x",
            finalUrl = "https://www.freewebnovel.com/x",
            statusCode = 200,
            body = "<html></html>",
            attemptedUrls = listOf("https://freewebnovel.com/x", "https://www.freewebnovel.com/x")
        )

        assertThat(diag.homeUrl).isEqualTo("https://freewebnovel.com/x")
        assertThat(diag.finalUrl).isEqualTo("https://www.freewebnovel.com/x")
        assertThat(diag.attemptedUrls).hasSize(2)
    }

    @Test
    fun extract_truncatesBodySnippetAtMax() {
        val body = "x".repeat(1000)
        val diag = extractZeroLinksDiagnostic(
            callSite = "crawler",
            homeUrl = "https://example.com/",
            finalUrl = null,
            statusCode = 200,
            body = body,
            attemptedUrls = emptyList()
        )

        assertThat(diag.bodyLength).isEqualTo(1000)
        assertThat(diag.bodySnippet.length).isAtMost(256)
    }

    @Test
    fun format_producesSingleLineWithAllFields() {
        val diag = extractZeroLinksDiagnostic(
            callSite = "crawler",
            homeUrl = "https://www.freewebnovel.com/x",
            finalUrl = "https://www.freewebnovel.com/x",
            statusCode = 200,
            body = "<html>challenge page</html>",
            attemptedUrls = listOf("https://www.freewebnovel.com/x")
        )

        val line = formatZeroLinksDiagnostic(diag)

        assertThat(line).contains("callSite=crawler")
        assertThat(line).contains("status=200")
        assertThat(line).contains("homeUrl=https://www.freewebnovel.com/x")
        assertThat(line).contains("finalUrl=https://www.freewebnovel.com/x")
        assertThat(line).contains("linkCount=0")
        assertThat(line).contains("bodyLength=27")
        assertThat(line).contains("body=<html>challenge page</html>")
        assertThat(line).contains("attemptedUrls=[https://www.freewebnovel.com/x]")
    }

    @Test
    fun format_omitsNewlinesFromBody() {
        val diag = extractZeroLinksDiagnostic(
            callSite = "crawler",
            homeUrl = "https://example.com/",
            finalUrl = null,
            statusCode = 200,
            body = "line1\nline2\r\nline3",
            attemptedUrls = emptyList()
        )

        val line = formatZeroLinksDiagnostic(diag)

        assertThat(line).doesNotContain("\n")
        assertThat(line).doesNotContain("\r")
    }
}
