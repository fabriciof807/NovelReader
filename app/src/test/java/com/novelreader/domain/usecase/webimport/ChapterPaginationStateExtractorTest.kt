package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import org.jsoup.Jsoup
import org.junit.Test

class ChapterPaginationStateExtractorTest {

    @Test
    fun extractsTotalChaptersTotalPagePageSizeFromScriptTag() {
        val html = """
            <html>
              <head></head>
              <body>
                <script>
                  window.chapterPagination = {
                    currentPage: 1,
                    pageSize: 40,
                    totalPage: 6,
                    totalChapters: 223
                  };
                </script>
              </body>
            </html>
        """.trimIndent()
        val doc = Jsoup.parse(html)

        val state = extractChapterPaginationState(doc)

        assertThat(state).isNotNull()
        assertThat(state!!.totalChapters).isEqualTo(223)
        assertThat(state.totalPage).isEqualTo(6)
        assertThat(state.pageSize).isEqualTo(40)
    }

    @Test
    fun returnsNullWhenNoChapterPaginationScriptPresent() {
        val html = "<html><body><p>no script</p></body></html>"
        val doc = Jsoup.parse(html)

        val state = extractChapterPaginationState(doc)

        assertThat(state).isNull()
    }

    @Test
    fun returnsNullWhenScriptPresentButNotChapterPagination() {
        val html = """
            <html><body>
              <script>window.somethingElse = { totalPage: 1 };</script>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)

        val state = extractChapterPaginationState(doc)

        assertThat(state).isNull()
    }

    @Test
    fun handlesMissingOptionalFields() {
        val html = """
            <html><body>
              <script>window.chapterPagination = { totalPage: 3, totalChapters: 100, pageSize: 40 };</script>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)

        val state = extractChapterPaginationState(doc)

        assertThat(state).isNotNull()
        assertThat(state!!.totalChapters).isEqualTo(100)
        assertThat(state.totalPage).isEqualTo(3)
        assertThat(state.pageSize).isEqualTo(40)
    }
}
