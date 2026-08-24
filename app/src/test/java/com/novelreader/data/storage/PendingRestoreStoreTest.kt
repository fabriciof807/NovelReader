package com.novelreader.data.storage

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PendingRestoreStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = PendingRestoreStore(context, Dispatchers.Unconfined)

    @Test
    fun `load on fresh install returns empty restore`() = runTest {
        assertThat(store.load().isEmpty()).isTrue()
    }

    @Test
    fun `update persists and round-trips all entry types`() = runTest {
        store.update {
            PendingRestore(
                bookmarks = listOf(
                    PendingBookmark("Novel", "Bm", "note", 3, 42, 100L, "ch1.html", 0)
                ),
                characters = listOf(
                    PendingCharacter(
                        novelTitle = "Novel", name = "Hero", notes = null, isFavorite = true,
                        photoPath = "/tmp/p.jpg", createdAt = 200L,
                        photos = listOf(PendingPhoto("/tmp/a.jpg", 0))
                    )
                ),
                collectionLinks = listOf(PendingCollectionLink("Favoritas", "Novel")),
                novels = listOf(
                    PendingNovel("Novel", "Autor", true, 300L, "ch9.html", 8)
                )
            )
        }

        val loaded = store.load()

        assertThat(loaded.bookmarks).hasSize(1)
        with(loaded.bookmarks[0]) {
            assertThat(novelTitle).isEqualTo("Novel")
            assertThat(title).isEqualTo("Bm")
            assertThat(note).isEqualTo("note")
            assertThat(page).isEqualTo(3)
            assertThat(scrollPosition).isEqualTo(42)
            assertThat(createdAt).isEqualTo(100L)
            assertThat(chapterFileName).isEqualTo("ch1.html")
            assertThat(chapterOrderIndex).isEqualTo(0)
        }
        assertThat(loaded.characters[0].photos).containsExactly(PendingPhoto("/tmp/a.jpg", 0))
        assertThat(loaded.collectionLinks).containsExactly(PendingCollectionLink("Favoritas", "Novel"))
        assertThat(loaded.novels[0].author).isEqualTo("Autor")
        assertThat(loaded.novels[0].autoUpdate).isTrue()
    }

    @Test
    fun `saving empty restore deletes the file`() = runTest {
        store.update { PendingRestore(bookmarks = listOf(emptyBookmark())) }
        val file = File(context.filesDir, "pending_restore.json")
        assertThat(file.exists()).isTrue()

        store.update { PendingRestore() }

        assertThat(file.exists()).isFalse()
        assertThat(store.load().isEmpty()).isTrue()
    }

    @Test
    fun `corrupt file is treated as empty`() = runTest {
        File(context.filesDir, "pending_restore.json").writeText("{ not json")
        assertThat(store.load().isEmpty()).isTrue()
    }

    @Test
    fun `null note and photoPath survive the round trip as absent`() = runTest {
        store.update {
            PendingRestore(
                bookmarks = listOf(PendingBookmark("N", "T", null, 0, 0, 1L, "f.html", 0)),
                characters = listOf(
                    PendingCharacter("N", "C", null, false, null, 1L, emptyList())
                )
            )
        }

        val loaded = store.load()

        assertThat(loaded.bookmarks[0].note).isNull()
        assertThat(loaded.characters[0].notes).isNull()
        assertThat(loaded.characters[0].photoPath).isNull()
    }

    private fun emptyBookmark() = PendingBookmark("N", "T", null, 0, 0, 0L, "f.html", 0)
}
