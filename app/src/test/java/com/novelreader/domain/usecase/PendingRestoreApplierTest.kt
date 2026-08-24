package com.novelreader.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.FolderEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.storage.PendingBookmark
import com.novelreader.data.storage.PendingCharacter
import com.novelreader.data.storage.PendingCollectionLink
import com.novelreader.data.storage.PendingNovel
import com.novelreader.data.storage.PendingPhoto
import com.novelreader.data.storage.PendingRestoreStore
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PendingRestoreApplierTest {

    private lateinit var db: NovelDatabase
    private lateinit var store: PendingRestoreStore
    private lateinit var applier: PendingRestoreApplier
    private val backgroundImportManager: BackgroundImportManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        ).build()
        store = PendingRestoreStore(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined)
        applier = PendingRestoreApplier(
            store = store,
            backgroundImportManager = backgroundImportManager,
            novelDao = db.novelDao(),
            chapterDao = db.chapterDao(),
            bookmarkDao = db.bookmarkDao(),
            characterDao = db.characterDao(),
            characterPhotoDao = db.characterPhotoDao(),
            folderDao = db.folderDao(),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `empty store applies nothing`() = runTest {
        assertThat(applier.applyPending()).isEqualTo(AppliedCounts())
    }

    @Test
    fun `restores bookmark by fileName and removes it from the store`() = runTest {
        val novelId = seedNovelWithChapter(fileName = "ch1.html", orderIndex = 0)

        store.update {
            it.copy(bookmarks = listOf(bookmark(novelTitle = "Novel")))
        }

        val counts = applier.applyPending()

        assertThat(counts.bookmarks).isEqualTo(1)
        val saved = db.bookmarkDao().getAllSync().single()
        assertThat(saved.chapterId).isEqualTo(db.chapterDao().getChaptersByNovelSync(novelId).single().id)
        assertThat(store.load().bookmarks).isEmpty()
    }

    @Test
    fun `falls back to orderIndex when fileName does not match`() = runTest {
        seedNovelWithChapter(fileName = "different.html", orderIndex = 7)

        store.update {
            it.copy(
                bookmarks = listOf(
                    bookmark(novelTitle = "Novel").copy(chapterFileName = "stale.html", chapterOrderIndex = 7)
                )
            )
        }

        val counts = applier.applyPending()

        assertThat(counts.bookmarks).isEqualTo(1)
        assertThat(db.bookmarkDao().getAllSync()).hasSize(1)
    }

    @Test
    fun `skips duplicate bookmarks instead of inserting twice`() = runTest {
        val novelId = seedNovelWithChapter(fileName = "ch1.html", orderIndex = 0)
        val chapterId = db.chapterDao().getChaptersByNovelSync(novelId).single().id
        db.bookmarkDao().insert(
            com.novelreader.data.local.db.entity.BookmarkEntity(
                chapterId = chapterId, title = "Mark", page = 2
            )
        )

        store.update { it.copy(bookmarks = listOf(bookmark("Novel"))) }
        val counts = applier.applyPending()

        assertThat(counts.bookmarks).isEqualTo(1)
        assertThat(db.bookmarkDao().getAllSync()).hasSize(1)
        assertThat(store.load().bookmarks).isEmpty()
    }

    @Test
    fun `keeps entries whose novel is still missing`() = runTest {
        store.update {
            it.copy(bookmarks = listOf(bookmark("Not imported yet")))
        }

        val counts = applier.applyPending()

        assertThat(counts.bookmarks).isEqualTo(0)
        assertThat(store.load().bookmarks).hasSize(1)
    }

    @Test
    fun `restores character with existing photos and skips missing files`() = runTest {
        seedNovelWithChapter(fileName = "ch1.html", orderIndex = 0)
        val existing = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().filesDir,
            "photo.jpg"
        ).apply { writeText("x") }
        val missing = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().filesDir,
            "gone.jpg"
        ).absolutePath

        store.update {
            it.copy(
                characters = listOf(
                    PendingCharacter(
                        novelTitle = "Novel",
                        name = "Hero",
                        notes = null,
                        isFavorite = true,
                        photoPath = missing,
                        createdAt = 1L,
                        photos = listOf(
                            PendingPhoto(existing.absolutePath, 0),
                            PendingPhoto(missing, 1)
                        )
                    )
                )
            )
        }

        val counts = applier.applyPending()

        assertThat(counts.characters).isEqualTo(1)
        val character = db.characterDao().getAllCharactersSync().single()
        assertThat(character.name).isEqualTo("Hero")
        assertThat(character.photoPath).isNull()
        assertThat(db.characterPhotoDao().getByCharacterSync(character.id)).hasSize(1)
    }

    @Test
    fun `does not duplicate an already restored character`() = runTest {
        val novelId = seedNovelWithChapter(fileName = "ch1.html", orderIndex = 0)
        db.characterDao().insert(
            com.novelreader.data.local.db.entity.CharacterEntity(novelId = novelId, name = "Hero")
        )

        store.update { it.copy(characters = listOf(character("Hero"))) }
        val counts = applier.applyPending()

        assertThat(counts.characters).isEqualTo(1)
        assertThat(db.characterDao().getAllCharactersSync()).hasSize(1)
    }

    @Test
    fun `links novel into folder by name ignoring case`() = runTest {
        val novelId = seedNovelWithChapter(fileName = "ch1.html", orderIndex = 0)
        db.folderDao().insert(FolderEntity(name = "Favoritas"))

        store.update {
            it.copy(collectionLinks = listOf(PendingCollectionLink("favoritas", "novel")))
        }

        val counts = applier.applyPending()

        assertThat(counts.collectionLinks).isEqualTo(1)
        assertThat(db.folderDao().getFolderIdsForNovel(novelId)).isNotEmpty()
    }

    @Test
    fun `keeps collection link when folder is missing`() = runTest {
        seedNovelWithChapter(fileName = "ch1.html", orderIndex = 0)

        store.update {
            it.copy(collectionLinks = listOf(PendingCollectionLink("Nope", "Novel")))
        }

        val counts = applier.applyPending()

        assertThat(counts.collectionLinks).isEqualTo(0)
        assertThat(store.load().collectionLinks).hasSize(1)
    }

    @Test
    fun `restores progress autoUpdate and author once chapters exist`() = runTest {
        seedNovelWithChapter(fileName = "ch9.html", orderIndex = 8)

        store.update {
            it.copy(
                novels = listOf(
                    PendingNovel(
                        title = "Novel",
                        author = "Author X",
                        autoUpdate = true,
                        lastReadAt = 12345L,
                        lastChapterFileName = "ch9.html",
                        lastChapterOrderIndex = 8
                    )
                )
            )
        }

        val counts = applier.applyPending()

        assertThat(counts.novels).isEqualTo(1)
        val novel = db.novelDao().getNovelByTitleIgnoreCase("Novel")!!
        assertThat(novel.author).isEqualTo("Author X")
        assertThat(novel.autoUpdate).isTrue()
        assertThat(novel.lastReadAt).isEqualTo(12345L)
        assertThat(db.chapterDao().getChapterById(novel.lastChapterId!!)?.fileName).isEqualTo("ch9.html")
    }

    @Test
    fun `keeps novel metadata when chapters have not landed yet`() = runTest {
        db.novelDao().insert(NovelEntity(title = "Novel"))

        store.update {
            it.copy(
                novels = listOf(
                    PendingNovel("Novel", null, false, 5L, "ch1.html", 0)
                )
            )
        }

        val counts = applier.applyPending()

        assertThat(counts.novels).isEqualTo(0)
        assertThat(store.load().novels).hasSize(1)
    }

    private suspend fun seedNovelWithChapter(fileName: String, orderIndex: Int): Long {
        val novelId = db.novelDao().insert(NovelEntity(title = "Novel"))
        db.chapterDao().insertAll(
            listOf(
                ChapterEntity(
                    novelId = novelId,
                    title = "Ch",
                    fileName = fileName,
                    orderIndex = orderIndex,
                    content = "c"
                )
            )
        )
        return novelId
    }

    private fun bookmark(novelTitle: String) = PendingBookmark(
        novelTitle = novelTitle,
        title = "Mark",
        note = null,
        page = 2,
        scrollPosition = 10,
        createdAt = 99L,
        chapterFileName = "ch1.html",
        chapterOrderIndex = 0
    )

    private fun character(name: String) = PendingCharacter(
        novelTitle = "Novel",
        name = name,
        notes = null,
        isFavorite = false,
        photoPath = null,
        createdAt = 1L,
        photos = emptyList()
    )
}
