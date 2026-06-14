package com.novelreader.data.worker

import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.ImportJobSpec
import org.junit.Test

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
}
