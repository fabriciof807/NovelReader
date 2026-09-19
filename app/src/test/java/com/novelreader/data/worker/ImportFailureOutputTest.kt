package com.novelreader.data.worker

import androidx.work.Data
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportFailureOutputTest {

    @Test fun `short message is kept as-is`() {
        val data = ImportFailureOutput.build("network", "timeout after 3 attempts")

        assertThat(data.getString(ChapterImportWorker.KEY_ERROR_MSG)).isEqualTo("timeout after 3 attempts")
        assertThat(data.getString(ChapterImportWorker.KEY_ERROR_TYPE)).isEqualTo("network")
    }

    @Test fun `null message falls back to the unknown error label`() {
        val data = ImportFailureOutput.build("parse", null)

        assertThat(data.getString(ChapterImportWorker.KEY_ERROR_MSG)).isEqualTo("Unknown error")
    }

    @Test fun `long message is truncated and marked`() {
        val message = "x".repeat(4_000)
        val bounded = ImportFailureOutput.build("parse", message)
            .getString(ChapterImportWorker.KEY_ERROR_MSG)!!

        assertThat(bounded.length).isAtMost(ImportFailureOutput.MAX_MESSAGE_CHARS + 1)
        assertThat(bounded).endsWith("…")
        assertThat(bounded).startsWith("x".repeat(64))
    }

    @Test fun `truncation does not split a surrogate pair`() {
        val message = "🐛".repeat(4_000)
        val bounded = ImportFailureOutput.build("parse", message)
            .getString(ChapterImportWorker.KEY_ERROR_MSG)!!

        assertThat(bounded).endsWith("…")
        val body = bounded.dropLast(1)
        assertThat(body.length % 2).isEqualTo(0)
        assertThat(body).isEqualTo("🐛".repeat(body.length / 2))
    }

    @Test fun `pathological message stays under the WorkManager 10KB limit`() {
        val message = "Data cannot occupy more than 10240 bytes".repeat(30_000)
        val data: Data = ImportFailureOutput.build("network", message)

        assertThat(data.toByteArray().size).isLessThan(10_240)
    }
}
