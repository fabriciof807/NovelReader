package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import org.jsoup.HttpStatusException
import org.junit.Test

class WebFetchDiagnosticTest {

    @Test
    fun extract_capturesStatusUrlAndCfHeadersFromHttpStatusException() {
        val throwable = HttpStatusException("HTTP error fetching URL", 403, "https://www.freewebnovel.com/chapter-1")
        val headers = mapOf(
            "Server" to "cloudflare",
            "cf-ray" to "a1234abcd5678-GRU",
            "Set-Cookie" to "cf_clearance=abc123; path=/"
        )
        val bodySnippet = "Just a moment... Checking your browser before accessing..."

        val diag = extractWebFetchDiagnostic(
            throwable = throwable,
            callSite = "crawler",
            serverHeader = headers["Server"],
            cfRayHeader = headers["cf-ray"],
            setCookieHeader = headers["Set-Cookie"],
            bodySnippet = bodySnippet
        )

        assertThat(diag.callSite).isEqualTo("crawler")
        assertThat(diag.statusCode).isEqualTo(403)
        assertThat(diag.url).isEqualTo("https://www.freewebnovel.com/chapter-1")
        assertThat(diag.server).isEqualTo("cloudflare")
        assertThat(diag.cfRay).isEqualTo("a1234abcd5678-GRU")
        assertThat(diag.setCookie).isEqualTo("cf_clearance=abc123; path=/")
        assertThat(diag.bodySnippet).isEqualTo(bodySnippet)
        assertThat(diag.exceptionClass).isEqualTo("HttpStatusException")
    }

    @Test
    fun extract_handlesMissingHeadersAndUnknownStatus() {
        val throwable = RuntimeException("connection reset")

        val diag = extractWebFetchDiagnostic(
            throwable = throwable,
            callSite = "fetcher",
            serverHeader = null,
            cfRayHeader = null,
            setCookieHeader = null,
            bodySnippet = null
        )

        assertThat(diag.callSite).isEqualTo("fetcher")
        assertThat(diag.statusCode).isEqualTo(0)
        assertThat(diag.url).isEqualTo("")
        assertThat(diag.server).isEqualTo("")
        assertThat(diag.cfRay).isEqualTo("")
        assertThat(diag.setCookie).isEqualTo("")
        assertThat(diag.bodySnippet).isEqualTo("")
        assertThat(diag.exceptionClass).isEqualTo("RuntimeException")
    }

    @Test
    fun format_producesSingleParseableLineWithAllFields() {
        val diag = WebFetchDiagnostic(
            callSite = "fetcher",
            statusCode = 403,
            url = "https://www.freewebnovel.com/chapter-1",
            server = "cloudflare",
            cfRay = "a123-GRU",
            setCookie = "cf_clearance=x",
            bodySnippet = "<html>Just a moment...</html>",
            exceptionClass = "HttpStatusException"
        )

        val line = formatWebFetchDiagnostic(diag)

        assertThat(line).contains("callSite=fetcher")
        assertThat(line).contains("status=403")
        assertThat(line).contains("url=https://www.freewebnovel.com/chapter-1")
        assertThat(line).contains("server=cloudflare")
        assertThat(line).contains("cfRay=a123-GRU")
        assertThat(line).contains("setCookie=cf_clearance=x")
        assertThat(line).contains("body=<html>Just a moment...</html>")
        assertThat(line).contains("exception=HttpStatusException")
    }

    @Test
    fun format_omitsNewlinesSoLogLineIsSingleLine() {
        val diag = WebFetchDiagnostic(
            callSite = "crawler",
            statusCode = 503,
            url = "https://www.freewebnovel.com/",
            server = "cloudflare",
            cfRay = "x",
            setCookie = "y",
            bodySnippet = "line1\nline2\r\nline3",
            exceptionClass = "HttpStatusException"
        )

        val line = formatWebFetchDiagnostic(diag)

        assertThat(line).doesNotContain("\n")
        assertThat(line).doesNotContain("\r")
        assertThat(line).contains("body=line1 line2 line3")
    }
}
