package com.novelreader.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovelDaoTest {

    private lateinit var db: NovelDatabase
    private lateinit var dao: NovelDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.novelDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetAllReturnsInsertedNovel() = runTest {
        val id = dao.insert(NovelEntity(title = "Test Novel", totalChapters = 0))
        val all = dao.getAllNovels().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].id).isEqualTo(id)
        assertThat(all[0].title).isEqualTo("Test Novel")
    }

    @Test
    fun getNovelByIdReturnsNullForMissing() = runTest {
        val found = dao.getNovelById(999)
        assertThat(found).isNull()
    }

    @Test
    fun updateCoverPathPersists() = runTest {
        val id = dao.insert(NovelEntity(title = "X", totalChapters = 0))
        dao.updateCoverPath(id, "/covers/x.jpg")
        val updated = dao.getNovelById(id)
        assertThat(updated?.coverPath).isEqualTo("/covers/x.jpg")
    }

    @Test
    fun deleteByIdRemovesNovel() = runTest {
        val id = dao.insert(NovelEntity(title = "Y", totalChapters = 0))
        dao.deleteById(id)
        val all = dao.getAllNovels().first()
        assertThat(all).isEmpty()
    }

    @Test
    fun getNovelByTitleReturnsFirstMatch() = runTest {
        dao.insert(NovelEntity(title = "Same", totalChapters = 0))
        dao.insert(NovelEntity(title = "Same", totalChapters = 0))
        val found = dao.getNovelByTitle("Same")
        assertThat(found).isNotNull()
        assertThat(found?.title).isEqualTo("Same")
    }
}
