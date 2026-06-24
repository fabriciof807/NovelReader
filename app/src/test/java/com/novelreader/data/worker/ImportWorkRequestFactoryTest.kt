package com.novelreader.data.worker

import androidx.work.Data
import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.ImportJobSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportWorkRequestFactoryTest {

    @Test fun `build request includes import tag and per-job tag`() {
        val spec = ImportJobSpec.create(
            "N",
            listOf(ChapterLink("c1", "https://x.com/1", 1)),
            null
        ).first()
        val request = ImportWorkRequestFactory.build(spec)

        assertThat(request.tags).contains(ChapterImportWorker.TAG_IMPORT)
        assertThat(request.tags).contains("job:${spec.id}")
    }

    @Test fun `build request is unique per spec`() {
        val spec1 = ImportJobSpec.create(
            "A",
            listOf(ChapterLink("c1", "https://x.com/1", 1)),
            null
        ).first()
        val spec2 = ImportJobSpec.create(
            "B",
            listOf(ChapterLink("c1", "https://x.com/1", 1)),
            null
        ).first()
        val r1 = ImportWorkRequestFactory.build(spec1)
        val r2 = ImportWorkRequestFactory.build(spec2)
        assertThat(r1).isNotNull()
        assertThat(r2).isNotNull()
        assertThat(r1.id).isNotEqualTo(r2.id)
    }

    @Test fun `build request does not include chapter link arrays in Data`() {
        val links = listOf(
            ChapterLink("c1", "https://x.com/1", 1),
            ChapterLink("c2", "https://x.com/2", 2)
        )
        val spec = ImportJobSpec.create("N", links, null).first()

        val request = ImportWorkRequestFactory.build(spec)
        val data: Data = request.workSpec.input

        val linkKeys = listOf("links", "nums", "key_links", "key_nums")
        for (key in linkKeys) {
            assertThat(data.keyValueMap.containsKey(key)).isFalse()
        }
    }

    @Test fun `build request data fits within WorkManager 10KB limit for large long-URL spec`() {
        val longUrl = "https://www.freewebnovel.com/novel/some-really-long-novel-slug-here/chapter-X-with-an-even-longer-chapter-title-slug-attached.html?ref=importer"
        val links = (1..100).map { ChapterLink("c$it", "$longUrl&idx=$it", it) }
        val spec = ImportJobSpec.create("Big Novel", links, "https://x.com/cover.jpg").first()

        val request = ImportWorkRequestFactory.build(spec)
        val data: Data = request.workSpec.input

        val smallData = Data.Builder()
            .putString(ChapterImportWorker.KEY_JOB_ID, data.getString(ChapterImportWorker.KEY_JOB_ID))
            .putString(ChapterImportWorker.KEY_TITLE, data.getString(ChapterImportWorker.KEY_TITLE))
            .putString(ChapterImportWorker.KEY_COVER, data.getString(ChapterImportWorker.KEY_COVER) ?: "")
            .putLong(ChapterImportWorker.KEY_ENQUEUED_AT, data.getLong(ChapterImportWorker.KEY_ENQUEUED_AT, 0L))
            .putInt(ChapterImportWorker.KEY_SPLIT_COUNT, data.getInt(ChapterImportWorker.KEY_SPLIT_COUNT, 1))
            .putInt(ChapterImportWorker.KEY_SPLIT_INDEX, data.getInt(ChapterImportWorker.KEY_SPLIT_INDEX, 0))
            .putString(ChapterImportWorker.KEY_SOURCE_URL, data.getString(ChapterImportWorker.KEY_SOURCE_URL) ?: "")
            .build()

        val threshold = 10_240
        val serializedSize = smallData.toByteArray().size
        assertThat(serializedSize).isLessThan(threshold)
    }
}
