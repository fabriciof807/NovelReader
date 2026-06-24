package com.novelreader.data.worker

import androidx.work.Data
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
class SpecReaderTest {

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

    @Test fun `readFromData returns spec from file store keyed by job id`() {
        val spec = sampleSpec(
            links = listOf("https://long-domain.example/novel/slug/chapter-1.html", "https://long-domain.example/novel/slug/chapter-2.html"),
            numbers = listOf(1, 2)
        )
        store.write(spec)

        val data = dataFor(spec)
        val read = SpecReader.readFromData(data, store)

        assertThat(read).isNotNull()
        assertThat(read!!.id).isEqualTo(spec.id)
        assertThat(read.novelTitle).isEqualTo(spec.novelTitle)
        assertThat(read.links).isEqualTo(spec.links)
        assertThat(read.chapterNumbers).isEqualTo(spec.chapterNumbers)
        assertThat(read.coverUrl).isEqualTo(spec.coverUrl)
        assertThat(read.splitIndex).isEqualTo(spec.splitIndex)
        assertThat(read.splitCount).isEqualTo(spec.splitCount)
    }

    @Test fun `readFromData reads from file store even when links array is absent from input data`() {
        val spec = sampleSpec(
            links = listOf("https://long-domain.example/chap/1", "https://long-domain.example/chap/2", "https://long-domain.example/chap/3"),
            numbers = listOf(1, 2, 3)
        )
        store.write(spec)

        val data = Data.Builder()
            .putString(ChapterImportWorker.KEY_JOB_ID, spec.id.toString())
            .putString(ChapterImportWorker.KEY_TITLE, spec.novelTitle)
            .build()
        val read = SpecReader.readFromData(data, store)

        assertThat(read).isNotNull()
        assertThat(read!!.links).hasSize(3)
        assertThat(read.chapterNumbers).hasSize(3)
    }

    @Test fun `readFromData returns null when job id is missing from data`() {
        val data = Data.Builder().build()
        assertThat(SpecReader.readFromData(data, store)).isNull()
    }

    @Test fun `readFromData returns null when job id is malformed`() {
        val data = Data.Builder()
            .putString(ChapterImportWorker.KEY_JOB_ID, "not-a-uuid")
            .build()
        assertThat(SpecReader.readFromData(data, store)).isNull()
    }

    @Test fun `readFromData returns empty-links fallback when file store has no entry for the job id`() {
        val knownId = UUID.randomUUID()
        val data = Data.Builder()
            .putString(ChapterImportWorker.KEY_JOB_ID, knownId.toString())
            .putString(ChapterImportWorker.KEY_TITLE, "Ghost Novel")
            .build()
        val read = SpecReader.readFromData(data, store)

        assertThat(read).isNotNull()
        assertThat(read!!.links).isEmpty()
        assertThat(read.chapterNumbers).isEmpty()
        assertThat(read.novelTitle).isEqualTo("Ghost Novel")
    }

    private fun dataFor(spec: ImportJobSpec): Data = Data.Builder()
        .putString(ChapterImportWorker.KEY_JOB_ID, spec.id.toString())
        .putString(ChapterImportWorker.KEY_TITLE, spec.novelTitle)
        .putString(ChapterImportWorker.KEY_COVER, spec.coverUrl ?: "")
        .putLong(ChapterImportWorker.KEY_ENQUEUED_AT, spec.enqueuedAt)
        .putInt(ChapterImportWorker.KEY_SPLIT_COUNT, spec.splitCount)
        .putInt(ChapterImportWorker.KEY_SPLIT_INDEX, spec.splitIndex)
        .putString(ChapterImportWorker.KEY_SOURCE_URL, spec.sourceUrl)
        .build()

    private fun sampleSpec(links: List<String>, numbers: List<Int>) = ImportJobSpec(
        id = UUID.randomUUID(),
        novelTitle = "Read Spec Test Novel",
        links = links,
        chapterNumbers = numbers,
        coverUrl = "https://x.com/cover.jpg",
        enqueuedAt = 1_700_000_000_000L,
        splitCount = 1,
        splitIndex = 0,
        sourceUrl = "https://x.com/source"
    )
}
