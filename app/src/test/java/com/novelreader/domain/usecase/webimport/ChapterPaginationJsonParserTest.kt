package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChapterPaginationJsonParserTest {

    @Test
    fun parsesCodeHtmlPagePageSizeTotalPageTotalChapters() {
        val json = """
            {"code":200,"html":"<li>chapter 1</li>","page":2,"pageSize":40,"totalPage":6,"totalChapters":223}
        """.trimIndent()

        val parsed = parseChapterPaginationJson(json)

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.code).isEqualTo(200)
        assertThat(parsed.html).contains("chapter 1")
        assertThat(parsed.page).isEqualTo(2)
        assertThat(parsed.pageSize).isEqualTo(40)
        assertThat(parsed.totalPage).isEqualTo(6)
        assertThat(parsed.totalChapters).isEqualTo(223)
    }

    @Test
    fun returnsNullForUnparseableJson() {
        assertThat(parseChapterPaginationJson("not json at all")).isNull()
    }

    @Test
    fun returnsNullForJsonMissingCode() {
        val json = """{"html":"x","page":1}"""
        assertThat(parseChapterPaginationJson(json)).isNull()
    }

    @Test
    fun returnsNullForJsonWithNonSuccessCode() {
        val json = """{"code":500,"html":"error","page":1,"pageSize":40,"totalPage":6,"totalChapters":223}"""
        assertThat(parseChapterPaginationJson(json)).isNull()
    }

    @Test
    fun returnsParsedEvenIfOptionalFieldsMissing() {
        val json = """{"code":200,"html":"<li>x</li>"}"""
        val parsed = parseChapterPaginationJson(json)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.page).isEqualTo(0)
        assertThat(parsed.pageSize).isEqualTo(0)
        assertThat(parsed.totalPage).isEqualTo(0)
        assertThat(parsed.totalChapters).isEqualTo(0)
    }
}
