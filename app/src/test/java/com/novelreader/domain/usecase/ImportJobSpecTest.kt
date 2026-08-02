package com.novelreader.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.UUID

class ImportJobSpecTest {

    @Test
    fun `create populates all fields with unique id`() {
        val links = listOf(
            ChapterLink("c1", "https://x.com/1", 1),
            ChapterLink("c2", "https://x.com/2", 2)
        )
        val specs = ImportJobSpec.create("My Novel", links, "https://x.com/cover.jpg")
        val spec = specs.first()

        assertThat(spec.id).isNotEqualTo(UUID(0L, 0L))
        assertThat(spec.novelTitle).isEqualTo("My Novel")
        assertThat(spec.links).hasSize(2)
        assertThat(spec.links[0]).isEqualTo("https://x.com/1")
        assertThat(spec.links[1]).isEqualTo("https://x.com/2")
        assertThat(spec.chapterNumbers[0]).isEqualTo(1)
        assertThat(spec.chapterNumbers[1]).isEqualTo(2)
        assertThat(spec.coverUrl).isEqualTo("https://x.com/cover.jpg")
        assertThat(spec.isFavorite).isNull()
    }

    @Test
    fun `create accepts null cover`() {
        val specs = ImportJobSpec.create("N", listOf(ChapterLink("c", "u", 1)), null)
        assertThat(specs.first().coverUrl).isNull()
    }

    @Test
    fun `create preserves nullable favorite metadata`() {
        val link = listOf(ChapterLink("c", "u", 1))

        assertThat(ImportJobSpec.create("N", link, null, isFavorite = true).first().isFavorite)
            .isTrue()
        assertThat(ImportJobSpec.create("N", link, null, isFavorite = false).first().isFavorite)
            .isFalse()
    }

    @Test
    fun `create assigns same shared id across split chunks`() {
        val s1 = ImportJobSpec.create("A", listOf(ChapterLink("c", "u", 1)), null)
        val s2 = ImportJobSpec.create("A", listOf(ChapterLink("c", "u", 1)), null)
        assertThat(s1.first().id).isNotEqualTo(s2.first().id)
    }

    @Test
    fun `create preserves order of links within a chunk`() {
        val urls = listOf("https://x.com/c", "https://x.com/a", "https://x.com/b")
        val links = urls.mapIndexed { i, u -> ChapterLink("t$i", u, i) }
        val specs = ImportJobSpec.create("N", links, null)
        assertThat(specs.first().links).isEqualTo(urls)
    }

    @Test
    fun `create splits large lists into multiple specs sharing the same id`() {
        val links = (1..150).map { ChapterLink("c$it", "https://x.com/$it", it) }
        val specs = ImportJobSpec.create("N", links, null)

        assertThat(specs).hasSize(2)
        assertThat(specs[0].id).isEqualTo(specs[1].id)
        assertThat(specs[0].links).hasSize(100)
        assertThat(specs[1].links).hasSize(50)
        assertThat(specs[0].splitIndex).isEqualTo(0)
        assertThat(specs[1].splitIndex).isEqualTo(1)
        assertThat(specs[0].splitCount).isEqualTo(2)
    }
}
