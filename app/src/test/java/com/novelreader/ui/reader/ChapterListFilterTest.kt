package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.ChapterEntity
import org.junit.Test

class ChapterListFilterTest {

    private fun ch(id: Long, title: String) = ChapterEntity(
        id = id, novelId = 1L, title = title, fileName = "ch$id.html", orderIndex = id.toInt(), content = ""
    )

    @Test
    fun `blank query returns all chapters unchanged`() {
        val list = listOf(ch(1, "Chapter 1 - Beginning"), ch(2, "Chapter 2 - Middle"), ch(3, "Chapter 3 - End"))
        assertThat(filterChaptersByQuery(list, "")).isEqualTo(list)
        assertThat(filterChaptersByQuery(list, "   ")).isEqualTo(list)
    }

    @Test
    fun `matching query returns only matches`() {
        val list = listOf(ch(1, "Chapter 1 - Beginning"), ch(2, "Chapter 2 - Middle"), ch(3, "Chapter 3 - End"))
        val result = filterChaptersByQuery(list, "Middle")
        assertThat(result.map { it.id }).containsExactly(2L)
    }

    @Test
    fun `non-matching query returns empty`() {
        val list = listOf(ch(1, "Chapter 1"), ch(2, "Chapter 2"))
        assertThat(filterChaptersByQuery(list, "Nonexistent")).isEmpty()
    }

    @Test
    fun `query is case-insensitive`() {
        val list = listOf(ch(1, "The Beginning"), ch(2, "The Middle"))
        val result = filterChaptersByQuery(list, "the")
        assertThat(result.map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `query is trimmed`() {
        val list = listOf(ch(1, "The Beginning"), ch(2, "The Middle"))
        val result = filterChaptersByQuery(list, "  Beginning  ")
        assertThat(result.map { it.id }).containsExactly(1L)
    }
}
