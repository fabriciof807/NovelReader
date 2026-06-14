package com.novelreader.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class E2EFlowTest {

    private lateinit var db: NovelDatabase
    private lateinit var novelDao: NovelDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var characterDao: CharacterDao
    private lateinit var novelRepo: NovelRepository
    private lateinit var chapterRepo: ChapterRepository

    @Before
    fun createDb() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        ).allowMainThreadQueries().build()
        novelDao = db.novelDao()
        chapterDao = db.chapterDao()
        bookmarkDao = db.bookmarkDao()
        characterDao = db.characterDao()
        novelRepo = NovelRepository(novelDao)
        chapterRepo = ChapterRepository(chapterDao)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun importSaveSearchFavorite() = runTest {
        val novelId = novelDao.insert(NovelEntity(title = "The Lost Kingdom", totalChapters = 0))
        assertThat(novelId).isGreaterThan(0)

        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "The Awakening", fileName = "c1.html", orderIndex = 0, content = "The dragon awakens from its ancient slumber"),
            ChapterEntity(novelId = novelId, title = "The Journey", fileName = "c2.html", orderIndex = 1, content = "The prince rides through the dark forest"),
            ChapterEntity(novelId = novelId, title = "The Battle", fileName = "c3.html", orderIndex = 2, content = "Swords clash in the final battle")
        ))

        val chapters = chapterDao.getChaptersByNovelSync(novelId)
        assertThat(chapters).hasSize(3)

        novelDao.updateChapterCount(novelId, 3)
        val novel = novelDao.getNovelById(novelId)
        assertThat(novel!!.totalChapters).isEqualTo(3)

        val searchResults = chapterDao.searchInNovel(novelId, "\"dragon\"*")
        assertThat(searchResults).hasSize(1)
        assertThat(searchResults[0].title).isEqualTo("The Awakening")

        val chapterId = chapters.first().id
        bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "Great passage", page = 1))
        val bookmarks = bookmarkDao.getAll().first()
        assertThat(bookmarks).hasSize(1)
        assertThat(bookmarks[0].title).isEqualTo("Great passage")

        characterDao.insert(CharacterEntity(novelId = novelId, name = "Dragon"))
        characterDao.insert(CharacterEntity(novelId = novelId, name = "Prince"))
        val characters = characterDao.getByNovel(novelId).first()
        assertThat(characters).hasSize(2)
    }

    @Test
    fun fts4IndexesAfterInsert() = runTest {
        val novelId = novelDao.insert(NovelEntity(title = "FTS Test", totalChapters = 0))
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Hidden Treasure", fileName = "ht.html", orderIndex = 0, content = "The treasure was hidden deep in the cave"),
            ChapterEntity(novelId = novelId, title = "Simple Life", fileName = "sl.html", orderIndex = 1, content = "A quiet village life")
        ))

        val results = chapterDao.searchInNovel(novelId, "\"treasure\"*")
        assertThat(results).hasSize(1)
        assertThat(results[0].title).isEqualTo("Hidden Treasure")
    }

    @Test
    fun deleteNovelCascadeEverything() = runTest {
        val novelId = novelDao.insert(NovelEntity(title = "To Delete", totalChapters = 0))
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "C1", fileName = "c1.html", orderIndex = 0, content = "x")
        ))
        val chapterId = chapterDao.getChaptersByNovelSync(novelId).first().id
        bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "B1", page = 1))
        characterDao.insert(CharacterEntity(novelId = novelId, name = "Char1"))

        novelDao.deleteById(novelId)

        assertThat(novelDao.getNovelById(novelId)).isNull()
        assertThat(chapterDao.getChaptersByNovelSync(novelId)).isEmpty()
        assertThat(characterDao.getByNovelSync(novelId)).isEmpty()

        val remainingBookmarks = bookmarkDao.getAll().first()
        val remainingChapters = chapterDao.getChaptersByNovelSync(novelId)
        assertThat(remainingChapters).isEmpty()
        assertThat(remainingBookmarks).isEmpty()
    }

    @Test
    fun chapterOrderNormalization() = runTest {
        val novelId = novelDao.insert(NovelEntity(title = "Order Test", totalChapters = 0))
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Chapter 5", fileName = "c5.html", orderIndex = 5, content = "x"),
            ChapterEntity(novelId = novelId, title = "Chapter 2", fileName = "c2.html", orderIndex = 2, content = "y"),
            ChapterEntity(novelId = novelId, title = "Chapter 8", fileName = "c8.html", orderIndex = 8, content = "z")
        ))

        chapterRepo.reNormalizeOrderIndices(novelId)

        val chapters = chapterDao.getChaptersByNovelSync(novelId)
        assertThat(chapters[0].orderIndex).isEqualTo(0)
        assertThat(chapters[0].title).isEqualTo("Chapter 2")
        assertThat(chapters[1].orderIndex).isEqualTo(1)
        assertThat(chapters[1].title).isEqualTo("Chapter 5")
        assertThat(chapters[2].orderIndex).isEqualTo(2)
        assertThat(chapters[2].title).isEqualTo("Chapter 8")
    }

    @Test
    fun bookmarkCountAfterInsert() = runTest {
        val novelId = novelDao.insert(NovelEntity(title = "Bookmark Count", totalChapters = 0))
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "C1", fileName = "c1.html", orderIndex = 0, content = "x")
        ))
        val chapterId = chapterDao.getChaptersByNovelSync(novelId).first().id

        bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "B1", page = 1))
        bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "B2", page = 2))
        bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "B3", page = 3))

        assertThat(bookmarkDao.getTotalCount()).isEqualTo(3)
    }

    @Test
    fun searchAfterImport_findResults() = runTest {
        val novelId = novelDao.insert(NovelEntity(title = "Search Novel", totalChapters = 0))
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "The Dragon Lord", fileName = "c1.html", orderIndex = 0, content = "The dragon lord ruled the skies"),
            ChapterEntity(novelId = novelId, title = "The Prince", fileName = "c2.html", orderIndex = 1, content = "The prince was brave"),
            ChapterEntity(novelId = novelId, title = "The Kingdom", fileName = "c3.html", orderIndex = 2, content = "The kingdom prospered")
        ))

        val results = chapterDao.searchInNovel(novelId, "\"dragon\"*")
        assertThat(results).hasSize(1)
        assertThat(results[0].title).isEqualTo("The Dragon Lord")

        val allResults = chapterDao.searchInNovel(novelId, "\"the\"*")
        assertThat(allResults).hasSize(3)
    }
}
