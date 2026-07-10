package com.novelreader.data.local.preferences

import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.ImportJobSpec
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportPreferencesTest {

    @Test
    fun `encode decode round-trip preserves all fields`() {
        val spec = ImportJobSpec(
            id = UUID.fromString("11111111-2222-3333-4444-555555555555"),
            novelTitle = "Test Novel",
            links = listOf("https://example.com/ch1", "https://example.com/ch2"),
            chapterNumbers = listOf(1, 2),
            coverUrl = "https://example.com/cover.jpg",
            enqueuedAt = 1234567890L,
            splitCount = 3,
            splitIndex = 1,
            sourceUrl = "https://example.com/novel",
            domain = "freewebnovel.com",
            targetNovelId = 42L
        )

        val json = ImportPreferences.encode(spec)
        val decoded = ImportPreferences.decode(json)

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.id).isEqualTo(spec.id)
        assertThat(decoded.novelTitle).isEqualTo(spec.novelTitle)
        assertThat(decoded.links).isEqualTo(spec.links)
        assertThat(decoded.chapterNumbers).isEqualTo(spec.chapterNumbers)
        assertThat(decoded.coverUrl).isEqualTo(spec.coverUrl)
        assertThat(decoded.enqueuedAt).isEqualTo(spec.enqueuedAt)
        assertThat(decoded.splitCount).isEqualTo(spec.splitCount)
        assertThat(decoded.splitIndex).isEqualTo(spec.splitIndex)
        assertThat(decoded.sourceUrl).isEqualTo(spec.sourceUrl)
        assertThat(decoded.domain).isEqualTo(spec.domain)
        assertThat(decoded.targetNovelId).isEqualTo(spec.targetNovelId)
    }

    @Test
    fun `encode decode preserves null targetNovelId`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "No Target",
            links = emptyList(),
            chapterNumbers = emptyList(),
            coverUrl = null,
            enqueuedAt = System.currentTimeMillis(),
            splitCount = 1,
            splitIndex = 0,
            sourceUrl = "",
            domain = "",
            targetNovelId = null
        )

        val json = ImportPreferences.encode(spec)
        val decoded = ImportPreferences.decode(json)

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.targetNovelId).isNull()
        assertThat(decoded.domain).isEmpty()
    }

    @Test
    fun `decode invalid json returns null`() {
        val decoded = ImportPreferences.decode("not valid json")
        assertThat(decoded).isNull()
    }
}
