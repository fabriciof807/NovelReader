package com.novelreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChapterRepositoryTest {

    private lateinit var database: NovelDatabase
    private lateinit var repository: ChapterRepository
    private var novelId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChapterRepository(database.chapterDao())
        novelId = database.novelDao().insert(
            NovelEntity(title = "The Lost Kingdom", totalChapters = 0)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveByNovel() = runBlocking {
        repository.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Chapter 1", fileName = "ch1.html", orderIndex = 0, content = "Once upon a time"),
            ChapterEntity(novelId = novelId, title = "Chapter 2", fileName = "ch2.html", orderIndex = 1, content = "The adventure continues"),
            ChapterEntity(novelId = novelId, title = "Chapter 3", fileName = "ch3.html", orderIndex = 2, content = "The final battle")
        ))
        val chapters = repository.getChaptersByNovelSync(novelId)
        assertThat(chapters).hasSize(3)
        assertThat(chapters[0].title).isEqualTo("Chapter 1")
        assertThat(chapters[1].title).isEqualTo("Chapter 2")
        assertThat(chapters[2].title).isEqualTo("Chapter 3")
    }

    @Test
    fun getChapterById_existing() = runBlocking {
        val id = database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Chapter 1", fileName = "ch1.html", orderIndex = 0, content = "x")
        ))
        val chapter = repository.getChapterById(
            database.chapterDao().getChaptersByNovelSync(novelId).first().id
        )
        assertThat(chapter).isNotNull()
        assertThat(chapter!!.title).isEqualTo("Chapter 1")
    }

    @Test
    fun getChapterById_missing() = runBlocking {
        val chapter = repository.getChapterById(99999L)
        assertThat(chapter).isNull()
    }

    @Test
    fun markAsRead_setsFlagAndPosition() = runBlocking {
        database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Chapter 1", fileName = "ch1.html", orderIndex = 0, content = "x")
        ))
        val chapterId = database.chapterDao().getChaptersByNovelSync(novelId).first().id
        repository.markAsRead(chapterId, 150)
        val updated = repository.getChapterById(chapterId)
        assertThat(updated!!.isRead).isTrue()
        assertThat(updated.lastScrollPosition).isEqualTo(150)
    }

    @Test
    fun updateOrderIndex_persists() = runBlocking {
        database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Chapter 1", fileName = "ch1.html", orderIndex = 0, content = "x")
        ))
        val chapterId = database.chapterDao().getChaptersByNovelSync(novelId).first().id
        repository.updateOrderIndex(chapterId, 5)
        val updated = repository.getChapterById(chapterId)
        assertThat(updated!!.orderIndex).isEqualTo(5)
    }

    @Test
    fun searchInNovel_findsMatch() = runBlocking {
        database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "The Awakening", fileName = "c1.html", orderIndex = 0, content = "The dragon awakens from its slumber"),
            ChapterEntity(novelId = novelId, title = "The Journey", fileName = "c2.html", orderIndex = 1, content = "The prince rides through the forest")
        ))
        val results = repository.searchInNovel(novelId, "dragon")
        assertThat(results).hasSize(1)
        assertThat(results[0].title).isEqualTo("The Awakening")
    }

    @Test
    fun searchInNovel_noResults() = runBlocking {
        database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "The Beginning", fileName = "c1.html", orderIndex = 0, content = "A quiet start")
        ))
        val results = repository.searchInNovel(novelId, "nonexistent_term_xyz")
        assertThat(results).isEmpty()
    }

    @Test
    fun reNormalizeOrderIndices() = runBlocking {
        repository.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Chapter 5", fileName = "c5.html", orderIndex = 5, content = "c"),
            ChapterEntity(novelId = novelId, title = "Chapter 2", fileName = "c2.html", orderIndex = 2, content = "b"),
            ChapterEntity(novelId = novelId, title = "Chapter 8", fileName = "c8.html", orderIndex = 8, content = "a")
        ))
        repository.reNormalizeOrderIndices(novelId)
        val chapters = repository.getChaptersByNovelSync(novelId)
        assertThat(chapters[0].orderIndex).isEqualTo(0)
        assertThat(chapters[0].title).isEqualTo("Chapter 2")
        assertThat(chapters[1].orderIndex).isEqualTo(1)
        assertThat(chapters[1].title).isEqualTo("Chapter 5")
        assertThat(chapters[2].orderIndex).isEqualTo(2)
        assertThat(chapters[2].title).isEqualTo("Chapter 8")
    }

    @Test
    fun getChaptersByIds_filtersCorrectly() = runBlocking {
        repository.insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "A", fileName = "a.html", orderIndex = 0, content = "a"),
            ChapterEntity(novelId = novelId, title = "B", fileName = "b.html", orderIndex = 1, content = "b"),
            ChapterEntity(novelId = novelId, title = "C", fileName = "c.html", orderIndex = 2, content = "c")
        ))
        val all = repository.getChaptersByNovelSync(novelId)
        val ids = listOf(all[0].id, all[2].id)
        val result = repository.getChaptersByIds(ids)
        assertThat(result).hasSize(2)
    }

    @Test
    fun searchInNovel_utf8Accents() = runBlocking {
        database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Coração Selvagem", fileName = "c1.html", orderIndex = 0, content = "O coração selvagem bate forte")
        ))
        val results = repository.searchInNovel(novelId, "coração")
        assertThat(results).hasSize(1)
    }

    @Test
    fun searchInNovel_specialChars_doesNotCrash() = runBlocking {
        database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Test", fileName = "t.html", orderIndex = 0, content = "Normal content")
        ))
        val results = repository.searchInNovel(novelId, "@#$%^&")
        assertThat(results).isEmpty()
    }
}
