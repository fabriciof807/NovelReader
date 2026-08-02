package com.novelreader.data.worker

import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.ImportJobSpec
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpecFileStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var store: SpecFileStore

    @Before
    fun setUp() {
        store = SpecFileStore(tempFolder.root)
    }

    @After
    fun tearDown() {
        store.deleteAll()
    }

    @Test fun `write then read round-trips all spec fields`() {
        val spec = sampleSpec(
            links = listOf(
                "https://example.com/a/very/long/path/to/chapter/1?with=query&strings=here&lots=of&stuff=1",
                "https://example.com/chapter-2",
                "https://example.com/chapter-3"
            ),
            numbers = listOf(1, 2, 3)
        ).copy(isFavorite = true)

        store.write(spec)
        val read = store.read(spec.id)

        assertThat(read).isNotNull()
        assertThat(read!!.id).isEqualTo(spec.id)
        assertThat(read.novelTitle).isEqualTo(spec.novelTitle)
        assertThat(read.links).isEqualTo(spec.links)
        assertThat(read.chapterNumbers).isEqualTo(spec.chapterNumbers)
        assertThat(read.coverUrl).isEqualTo(spec.coverUrl)
        assertThat(read.enqueuedAt).isEqualTo(spec.enqueuedAt)
        assertThat(read.splitIndex).isEqualTo(spec.splitIndex)
        assertThat(read.splitCount).isEqualTo(spec.splitCount)
        assertThat(read.sourceUrl).isEqualTo(spec.sourceUrl)
        assertThat(read.isFavorite).isTrue()
    }

    @Test fun `write then read preserves coverUrl when null`() {
        val spec = sampleSpec(coverUrl = null)

        store.write(spec)
        val read = store.read(spec.id)

        assertThat(read).isNotNull()
        assertThat(read!!.coverUrl).isNull()
    }

    @Test fun `write then read preserves false and absent favorite metadata`() {
        val falseSpec = sampleSpec().copy(isFavorite = false)
        store.write(falseSpec)
        assertThat(store.read(falseSpec.id)!!.isFavorite).isFalse()

        val absentSpec = sampleSpec().copy(isFavorite = null)
        store.write(absentSpec)
        assertThat(store.read(absentSpec.id)!!.isFavorite).isNull()
    }

    @Test fun `write accepts spec with hundreds of long URLs without size-based failure`() {
        val longUrl = "https://www.freewebnovel.com/novel/some-really-long-novel-slug-here/chapter-X-with-an-even-longer-chapter-title-slug-attached.html?ref=importer&session=12345"
        val spec = sampleSpec(
            links = List(500) { "$longUrl&idx=$it" },
            numbers = (1..500).toList()
        )

        store.write(spec)
        val read = store.read(spec.id)

        assertThat(read).isNotNull()
        assertThat(read!!.links).hasSize(500)
        assertThat(read.chapterNumbers).hasSize(500)
    }

    @Test fun `read returns null when file does not exist`() {
        val read = store.read(UUID.randomUUID())
        assertThat(read).isNull()
    }

    @Test fun `delete removes the file`() {
        val spec = sampleSpec()
        store.write(spec)
        assertThat(store.read(spec.id)).isNotNull()

        store.delete(spec.id)

        assertThat(store.read(spec.id)).isNull()
    }

    @Test fun `delete is idempotent when file does not exist`() {
        store.delete(UUID.randomUUID())
    }

    @Test fun `deleteAll removes all written specs`() {
        store.write(sampleSpec())
        store.write(sampleSpec())
        store.write(sampleSpec())

        store.deleteAll()

        val remaining = tempFolder.root.listFiles()?.filter { it.isFile && it.name.endsWith(".json") } ?: emptyList()
        assertThat(remaining).isEmpty()
    }

    private fun sampleSpec(
        links: List<String> = listOf("https://x.com/1", "https://x.com/2"),
        numbers: List<Int> = listOf(1, 2),
        coverUrl: String? = "https://x.com/cover.jpg"
    ) = ImportJobSpec(
        id = UUID.randomUUID(),
        novelTitle = "Sample Novel",
        links = links,
        chapterNumbers = numbers,
        coverUrl = coverUrl,
        enqueuedAt = 1_700_000_000_000L,
        splitCount = 3,
        splitIndex = 1,
        sourceUrl = "https://x.com/source"
    )
}
