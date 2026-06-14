package com.novelreader.e2e

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.repository.NovelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EndToEndTest {

    private lateinit var database: NovelDatabase
    private lateinit var novelDao: NovelDao
    private lateinit var novelRepository: NovelRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        novelDao = database.novelDao()
        novelRepository = NovelRepository(novelDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun novelDao_insertAndReadById() = runBlocking {
        val id = novelDao.insert(NovelEntity(title = "Test Novel", totalChapters = 5))
        val novel = novelDao.getNovelById(id)
        assert(novel != null) { "Novel should exist" }
        assert(novel!!.title == "Test Novel") { "Title should match" }
        assert(novel.totalChapters == 5) { "Chapters should match" }
    }

    @Test
    fun novelDao_getAllNovels() = runBlocking {
        novelDao.insert(NovelEntity(title = "Novel 1", totalChapters = 0))
        novelDao.insert(NovelEntity(title = "Novel 2", totalChapters = 0))
        val all = novelDao.getAllNovels().first()
        assert(all.size == 2) { "Should have 2 novels" }
    }

    @Test
    fun novelDao_updateAutoUpdate() = runBlocking {
        val id = novelDao.insert(NovelEntity(title = "Auto Novel", totalChapters = 0, autoUpdate = false))
        novelDao.updateAutoUpdate(id, true)
        val updated = novelDao.getNovelById(id)
        assert(updated!!.autoUpdate) { "autoUpdate should be true" }
    }

    @Test
    fun novelDao_toggleAutoUpdate() = runBlocking {
        val id = novelDao.insert(NovelEntity(title = "Toggle Novel", totalChapters = 0, autoUpdate = true))
        novelDao.updateAutoUpdate(id, false)
        val updated = novelDao.getNovelById(id)
        assert(!updated!!.autoUpdate) { "autoUpdate should be false after toggle" }
    }

    @Test
    fun novelDao_getAutoUpdateNovels() = runBlocking {
        novelDao.insert(NovelEntity(title = "A", totalChapters = 0, autoUpdate = true, sourceUrl = "https://a.com"))
        novelDao.insert(NovelEntity(title = "B", totalChapters = 0, autoUpdate = false, sourceUrl = "https://b.com"))
        novelDao.insert(NovelEntity(title = "C", totalChapters = 0, autoUpdate = true, sourceUrl = "https://c.com"))
        val autoNovels = novelDao.getAutoUpdateNovels()
        assert(autoNovels.size == 2) { "Should have 2 auto-update novels" }
    }

    @Test
    fun novelDao_updateSourceUrl() = runBlocking {
        val id = novelDao.insert(NovelEntity(title = "URL Novel", totalChapters = 0))
        novelDao.updateSourceUrl(id, "https://example.com/novel")
        val updated = novelDao.getNovelById(id)
        assert(updated!!.sourceUrl == "https://example.com/novel") { "sourceUrl should match" }
    }

    @Test
    fun novelDao_updateLastChecked() = runBlocking {
        val id = novelDao.insert(NovelEntity(title = "Check Novel", totalChapters = 0))
        novelDao.updateLastChecked(id, System.currentTimeMillis())
        val updated = novelDao.getNovelById(id)
        assert(updated!!.lastCheckedAt != null) { "lastCheckedAt should be set" }
    }

    @Test
    fun novelDao_deleteById() = runBlocking {
        val id = novelDao.insert(NovelEntity(title = "Delete Novel", totalChapters = 0))
        novelDao.deleteById(id)
        val deleted = novelDao.getNovelById(id)
        assert(deleted == null) { "Deleted novel should not exist" }
    }

    @Test
    fun novelRepository_insertAndRead() = runBlocking {
        val id = novelRepository.insert(NovelEntity(title = "Repository Novel", totalChapters = 10))
        val novel = novelRepository.getNovelById(id)
        assert(novel != null) { "Novel should exist" }
        assert(novel!!.title == "Repository Novel") { "Title should match" }
        assert(novel.totalChapters == 10) { "Chapters should match" }
    }

    @Test
    fun novelRepository_updateAutoUpdate() = runBlocking {
        val id = novelRepository.insert(NovelEntity(title = "Repo Auto", totalChapters = 0))
        novelRepository.updateAutoUpdate(id, true)
        val novel = novelRepository.getNovelById(id)
        assert(novel!!.autoUpdate) { "autoUpdate should be true" }
    }

    @Test
    fun novelRepository_toggleAutoUpdate() = runBlocking {
        val id = novelRepository.insert(NovelEntity(title = "Repo Toggle", totalChapters = 0))
        novelRepository.updateAutoUpdate(id, true)
        var novel = novelRepository.getNovelById(id)
        assert(novel!!.autoUpdate) { "Should be true after enable" }
        novelRepository.updateAutoUpdate(id, false)
        novel = novelRepository.getNovelById(id)
        assert(!novel!!.autoUpdate) { "Should be false after disable" }
    }

    @Test
    fun novelRepository_updateSourceUrl() = runBlocking {
        val id = novelRepository.insert(NovelEntity(title = "Repo URL", totalChapters = 0))
        novelRepository.updateSourceUrl(id, "https://repo.example.com")
        val novel = novelRepository.getNovelById(id)
        assert(novel!!.sourceUrl == "https://repo.example.com") { "sourceUrl should match" }
    }

    @Test
    fun novelRepository_getAutoUpdateNovels() = runBlocking {
        novelRepository.insert(NovelEntity(title = "Auto1", totalChapters = 0, autoUpdate = true, sourceUrl = "https://x.com"))
        novelRepository.insert(NovelEntity(title = "NonAuto", totalChapters = 0, autoUpdate = false, sourceUrl = "https://y.com"))
        novelRepository.insert(NovelEntity(title = "Auto2", totalChapters = 0, autoUpdate = true, sourceUrl = "https://z.com"))
        val autoNovels = novelRepository.getAutoUpdateNovels()
        assert(autoNovels.size == 2) { "Should have 2 auto-update novels" }
    }

    @Test
    fun novelRepository_deleteById() = runBlocking {
        val id = novelRepository.insert(NovelEntity(title = "Repo Delete", totalChapters = 0))
        novelRepository.deleteById(id)
        val deleted = novelRepository.getNovelById(id)
        assert(deleted == null) { "Deleted novel should not exist" }
    }

    @Test
    fun fullFlow_insertToggleAutoUpdateVerify() = runBlocking {
        val id = novelDao.insert(NovelEntity(
            title = "Full Flow Novel",
            totalChapters = 0,
            autoUpdate = false
        ))
        var novel = novelDao.getNovelById(id)
        assert(!novel!!.autoUpdate) { "Initial should be false" }
        novelDao.updateAutoUpdate(id, true)
        novel = novelDao.getNovelById(id)
        assert(novel!!.autoUpdate) { "After enable should be true" }
    }

    @Test
    fun autoUpdateToggleCycle() = runBlocking {
        val id = novelRepository.insert(NovelEntity(title = "Cycle Novel", totalChapters = 0))
        novelRepository.updateAutoUpdate(id, true)
        assert(novelRepository.getNovelById(id)!!.autoUpdate)
        novelRepository.updateAutoUpdate(id, false)
        assert(!novelRepository.getNovelById(id)!!.autoUpdate)
        novelRepository.updateAutoUpdate(id, true)
        assert(novelRepository.getNovelById(id)!!.autoUpdate)
    }
}
