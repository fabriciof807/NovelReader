package com.novelreader.regression

import com.novelreader.data.parser.HtmlSanitizer
import org.junit.Test

class EdgeCaseTest {

    @Test
    fun largeChapterContent_doesNotCrash() {
        val sb = StringBuilder()
        sb.append("<html><head><title>Large Novel</title></head><body>")
        repeat(50000) { i ->
            sb.append("<p>Paragraph $i with some text to simulate a very long chapter content.</p>")
        }
        sb.append("</body></html>")

        val sanitized = HtmlSanitizer.sanitizeHtml(sb.toString())
        assert(sanitized.isNotEmpty())
    }
}
