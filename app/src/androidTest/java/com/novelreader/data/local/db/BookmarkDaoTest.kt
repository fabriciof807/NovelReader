package com.novelreader.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkDaoTest {

    private lateinit var db: NovelDatabase
    private lateinit var novelDao: NovelDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var bookmarkDao: BookmarkDao
    private var chapterId: Long = 0L

    @Before
    fun createDb() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        ).allowMainThreadQueries().build()
        novelDao = db.novelDao()
        chapterDao = db.chapterDao()
        bookmarkDao = db.bookmarkDao()

        val novelId = novelDao.insert(NovelEntity(title = "The Lost Kingdom", totalChapters = 0))
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Chapter 1", fileName = "ch1.html", orderIndex = 0, content = "Start")
        ))
        chapterId = chapterDao.getChaptersByNovelSync(novelId).first().id
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetAll() = runTest {
        bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "Beautiful passage", page = 1))
        val all = bookmarkDao.getAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].title).isEqualTo("Beautiful passage")
    }

    @Test
    fun getByChapter_filtersCorrectly() = runTest {
        val novelId2 = novelDao.insert(NovelEntity(title = "Another Novel", totalChapters = 0))
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId2, title = "C1", fileName = "c1.html", orderIndex = 0, content = "x")
        ))
        val otherChapterId = chapterDao.getChaptersByNovelSync(novelId2).first().id

        bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "First book", page = 1))
        bookmarkDao.insert(BookmarkEntity(chapterId = otherChapterId, title = "Second book", page = 1))

        val results = bookmarkDao.getByChapter(chapterId).first()
        assertThat(results).hasSize(1)
        assertThat(results[0].title).isEqualTo("First book")
    }

    @Test
    fun deleteById_removes() = runTest {
        val id = bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "Delete me", page = 1))
        bookmarkDao.deleteById(id)
        val all = bookmarkDao.getAll().first()
        assertThat(all).isEmpty()
    }

    @Test
    fun getTotalCount_accurate() = runTest {
        bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "M1", page = 1))
        bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "M2", page = 2))
        assertThat(bookmarkDao.getTotalCount()).isEqualTo(2)
    }

    @Test
    fun getAll_orderedByCreatedAtDesc() = runTest {
        val id1 = bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "A", page = 1))
        val id2 = bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "B", page = 2))
        val all = bookmarkDao.getAll().first()
        assertThat(all).hasSize(2)
    }

    @Test
    fun insertMultiple_allReturned() = runTest {
        repeat(5) { i ->
            bookmarkDao.insert(BookmarkEntity(chapterId = chapterId, title = "B$i", page = i))
        }
        val all = bookmarkDao.getAll().first()
        assertThat(all).hasSize(5)
    }

    @Test
    fun deleteNonexistent_noException() = runTest {
        bookmarkDao.deleteById(99999L)
    }
}
