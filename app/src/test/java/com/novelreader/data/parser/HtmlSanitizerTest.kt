package com.novelreader.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlSanitizerTest {

    @Test fun `removes script tag`() {
        val out = HtmlSanitizer.sanitizeHtml("<p>safe</p><script>alert(1)</script>")
        assertThat(out).doesNotContain("script")
        assertThat(out).contains("<p>safe</p>")
    }

    @Test fun `removes iframe tag`() {
        val out = HtmlSanitizer.sanitizeHtml("<iframe src='x'></iframe><p>ok</p>")
        assertThat(out).doesNotContain("iframe")
    }

    @Test fun `removes inline event handlers`() {
        val out = HtmlSanitizer.sanitizeHtml("<p onclick='hack()'>x</p>")
        assertThat(out).doesNotContain("onclick")
    }

    @Test fun `removes javascript img src`() {
        val out = HtmlSanitizer.sanitizeHtml("<img src='javascript:alert(1)' />")
        assertThat(out).doesNotContain("javascript:")
    }

    @Test fun `removes data img src`() {
        val out = HtmlSanitizer.sanitizeHtml("<img src='data:text/html;base64,xxx' />")
        assertThat(out).doesNotContain("data:")
    }

    @Test fun `preserves safe formatting tags`() {
        val out = HtmlSanitizer.sanitizeHtml("<p><em>hello</em> <strong>world</strong></p>")
        assertThat(out).contains("<em>hello</em>")
        assertThat(out).contains("<strong>world</strong>")
    }

    @Test fun `removes style attribute`() {
        val out = HtmlSanitizer.sanitizeHtml("<p style='color:red'>x</p>")
        assertThat(out).doesNotContain("style")
        assertThat(out).contains("<p>x</p>")
    }
}
