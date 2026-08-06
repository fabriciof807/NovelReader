package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChapterPaginationJsonParserTest {

    @Test
    fun parsesCodeAndHtmlWithEscapedCharacters() {
        val json = """{"code":200,"html":"<li>chapter 1<\/li>\n<p>a\u2019b<\/p>"}"""

        val parsed = parseChapterPaginationJson(json)

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.code).isEqualTo(200)
        assertThat(parsed.html).contains("chapter 1")
        assertThat(parsed.html).contains("a\u2019b")
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
        val json = """{"code":500,"html":"error"}"""
        assertThat(parseChapterPaginationJson(json)).isNull()
    }

    @Test
    fun returnsNullWhenHtmlFieldIsAbsent() {
        val json = """{"code":200,"page":1}"""
        assertThat(parseChapterPaginationJson(json)).isNull()
    }
}
