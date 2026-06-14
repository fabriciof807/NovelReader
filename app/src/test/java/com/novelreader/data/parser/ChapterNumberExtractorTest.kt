package com.novelreader.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChapterNumberExtractorTest {

    @Test fun `extracts Portuguese 'Capitulo N'`() {
        assertThat(ChapterNumberExtractor.extract("Capítulo 1")).isEqualTo(1)
    }

    @Test fun `extracts Portuguese 'Cap N' abbreviation`() {
        assertThat(ChapterNumberExtractor.extract("Cap 10 - A Luta")).isEqualTo(10)
    }

    @Test fun `extracts English 'Chapter N'`() {
        assertThat(ChapterNumberExtractor.extract("Chapter 42")).isEqualTo(42)
    }

    @Test fun `extracts 'Ch N' abbreviation`() {
        assertThat(ChapterNumberExtractor.extract("Ch. 7")).isEqualTo(7)
    }

    @Test fun `extracts 'Cap-N' with dash`() {
        assertThat(ChapterNumberExtractor.extract("cap-3 title")).isEqualTo(3)
    }

    @Test fun `extracts from URL when title is uninformative`() {
        val num = ChapterNumberExtractor.extract("Page Title", url = "https://x.com/chapter/99")
        assertThat(num).isEqualTo(99)
    }

    @Test fun `extracts from filename when title and url lack a number`() {
        val num = ChapterNumberExtractor.extract("No Number", fileName = "chapter_5.html")
        assertThat(num).isEqualTo(5)
    }

    @Test fun `returns MAX_VALUE when no number exists anywhere`() {
        val num = ChapterNumberExtractor.extract(
            title = "Epilogue",
            fileName = "epilogue.html",
            url = "https://x.com/epilogue"
        )
        assertThat(num).isEqualTo(Int.MAX_VALUE)
    }

    @Test fun `prefers keyword match over raw number`() {
        val num = ChapterNumberExtractor.extract("Chapter 2 - page 999")
        assertThat(num).isEqualTo(2)
    }
}
