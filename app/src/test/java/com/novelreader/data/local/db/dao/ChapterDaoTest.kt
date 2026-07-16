package com.novelreader.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChapterDaoTest {
    private lateinit var db: NovelDatabase
    private lateinit var chapterDao: ChapterDao
    private lateinit var novelDao: NovelDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        ).build()
        chapterDao = db.chapterDao()
        novelDao = db.novelDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `updateContent updates both content and title`() = runTest {
        val novelId = novelDao.insert(NovelEntity(title = "Test", sourceFolder = "", totalChapters = 1))
        chapterDao.insertAll(
            listOf(ChapterEntity(novelId = novelId, title = "Old", fileName = "ch1.html", orderIndex = 0, content = "old content"))
        )
        val chapterId = chapterDao.getChaptersByNovelSync(novelId).first().id
        chapterDao.updateContent(chapterId, "new content", "New Title")
        val updated = chapterDao.getChapterById(chapterId)
        assertThat(updated?.content).isEqualTo("new content")
        assertThat(updated?.title).isEqualTo("New Title")
    }

    @Test
    fun `updateContent does not affect other columns`() = runTest {
        val novelId = novelDao.insert(NovelEntity(title = "Test", sourceFolder = "", totalChapters = 1))
        chapterDao.insertAll(
            listOf(ChapterEntity(novelId = novelId, title = "T", fileName = "f.html", orderIndex = 5, content = "c"))
        )
        val chapterId = chapterDao.getChaptersByNovelSync(novelId).first().id
        chapterDao.updateContent(chapterId, "new", "New")
        val updated = chapterDao.getChapterById(chapterId)
        assertThat(updated?.orderIndex).isEqualTo(5)
        assertThat(updated?.novelId).isEqualTo(novelId)
    }
}
