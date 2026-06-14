package com.novelreader.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterDaoTest {

    private lateinit var db: NovelDatabase
    private lateinit var novelDao: NovelDao
    private lateinit var chapterDao: ChapterDao
    private var novelId: Long = 0L

    @Before
    fun createDb() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        ).allowMainThreadQueries().build()
        novelDao = db.novelDao()
        chapterDao = db.chapterDao()
        novelId = novelDao.insert(NovelEntity(title = "Host", totalChapters = 0))
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAllAndGetByNovelReturnsAll() = runTest {
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "C1", fileName = "c1.html", orderIndex = 0, content = "<p>a</p>"),
            ChapterEntity(novelId = novelId, title = "C2", fileName = "c2.html", orderIndex = 1, content = "<p>b</p>")
        ))
        val chapters = chapterDao.getChaptersByNovelSync(novelId)
        assertThat(chapters).hasSize(2)
    }

    @Test
    fun updateOrderIndexChangesOrder() = runTest {
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "C", fileName = "c.html", orderIndex = 0, content = "x")
        ))
        val firstId = chapterDao.getChaptersByNovelSync(novelId).first().id
        chapterDao.updateOrderIndex(firstId, 5)
        val chapter = chapterDao.getChaptersByNovelSync(novelId).first()
        assertThat(chapter.orderIndex).isEqualTo(5)
    }

    @Test
    fun markAsReadSetsFlag() = runTest {
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "C", fileName = "c.html", orderIndex = 0, content = "x")
        ))
        val firstId = chapterDao.getChaptersByNovelSync(novelId).first().id
        chapterDao.markAsRead(firstId, 200)
        val chapter = chapterDao.getChaptersByNovelSync(novelId).first()
        assertThat(chapter.isRead).isTrue()
        assertThat(chapter.lastScrollPosition).isEqualTo(200)
    }

    @Test
    fun searchInNovelFindsMatch() = runTest {
        chapterDao.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Chapter 1", fileName = "c1.html", orderIndex = 0, content = "the dragon awakens"),
            ChapterEntity(novelId = novelId, title = "Chapter 2", fileName = "c2.html", orderIndex = 1, content = "the prince rides")
        ))
        val results = chapterDao.searchInNovel(novelId, "\"dragon\"*")
        assertThat(results).hasSize(1)
        assertThat(results[0].title).isEqualTo("Chapter 1")
    }
}
