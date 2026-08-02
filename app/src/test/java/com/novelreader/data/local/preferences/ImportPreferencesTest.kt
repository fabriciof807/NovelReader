package com.novelreader.data.local.preferences

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.ImportJobSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
            targetNovelId = 42L,
            isFavorite = true
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
        assertThat(decoded.isFavorite).isTrue()
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
            targetNovelId = null,
            isFavorite = null
        )

        val json = ImportPreferences.encode(spec)
        val decoded = ImportPreferences.decode(json)

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.targetNovelId).isNull()
        assertThat(decoded.isFavorite).isNull()
        assertThat(decoded.domain).isEmpty()
        assertThat(JSONObject(json).has("isFavorite")).isFalse()
    }

    @Test
    fun `encode decode preserves false favorite`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Not Favorite",
            links = emptyList(),
            chapterNumbers = emptyList(),
            coverUrl = null,
            enqueuedAt = 1L,
            isFavorite = false
        )

        assertThat(ImportPreferences.decode(ImportPreferences.encode(spec))!!.isFavorite)
            .isFalse()
    }

    @Test
    fun `decode invalid json returns null`() {
        val decoded = ImportPreferences.decode("not valid json")
        assertThat(decoded).isNull()
    }

    @Test
    fun `removeJobsByNovelTitle preserves other novel queue entries`() = runTest {
        val preferences = ImportPreferences(ApplicationProvider.getApplicationContext())
        preferences.clearQueue()
        val sharedId = UUID.randomUUID()
        preferences.enqueueJob(
            ImportJobSpec(sharedId, "Novel A", listOf("a/1"), listOf(1), null, 1L, splitCount = 2, splitIndex = 0)
        )
        preferences.enqueueJob(
            ImportJobSpec(sharedId, "Novel A", listOf("a/2"), listOf(2), null, 1L, splitCount = 2, splitIndex = 1)
        )
        preferences.enqueueJob(
            ImportJobSpec(UUID.randomUUID(), "Novel B", listOf("b/1"), listOf(1), null, 2L)
        )

        preferences.removeJobsByNovelTitle("Novel A")

        assertThat(preferences.pendingQueue.first().map { it.novelTitle })
            .containsExactly("Novel B")
        preferences.clearQueue()
    }

    @Test
    fun `removeJob removes every split with the shared logical id`() = runTest {
        val preferences = ImportPreferences(ApplicationProvider.getApplicationContext())
        preferences.clearQueue()
        val sharedId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        preferences.enqueueJob(
            ImportJobSpec(sharedId, "Novel A", listOf("a/1"), listOf(1), null, 1L, splitCount = 2, splitIndex = 0)
        )
        preferences.enqueueJob(
            ImportJobSpec(sharedId, "Novel A", listOf("a/2"), listOf(2), null, 1L, splitCount = 2, splitIndex = 1)
        )
        preferences.enqueueJob(
            ImportJobSpec(otherId, "Novel B", listOf("b/1"), listOf(1), null, 2L)
        )

        preferences.removeJob(sharedId)

        assertThat(preferences.pendingQueue.first().map { it.id }).containsExactly(otherId)
        preferences.clearQueue()
    }
}
