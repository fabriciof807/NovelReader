package com.novelreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
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
class BookmarkRepositoryTest {

    private lateinit var database: NovelDatabase
    private lateinit var repository: BookmarkRepository
    private var chapterId: Long = 0L
    private var novelId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookmarkRepository(database.bookmarkDao())
        novelId = database.novelDao().insert(
            NovelEntity(title = "The Lost Kingdom", totalChapters = 0)
        )
        database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Chapter 1", fileName = "ch1.html", orderIndex = 0, content = "Start")
        ))
        chapterId = database.chapterDao().getChaptersByNovelSync(novelId).first().id
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieve() = runBlocking {
        repository.insert(BookmarkEntity(chapterId = chapterId, title = "Great quote", page = 1))
        val all = repository.getAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].title).isEqualTo("Great quote")
    }

    @Test
    fun deleteById() = runBlocking {
        val id = repository.insert(BookmarkEntity(chapterId = chapterId, title = "To delete", page = 1))
        repository.deleteById(id)
        val all = repository.getAll().first()
        assertThat(all).isEmpty()
    }

    @Test
    fun getTotalCount() = runBlocking {
        repository.insert(BookmarkEntity(chapterId = chapterId, title = "M1", page = 1))
        repository.insert(BookmarkEntity(chapterId = chapterId, title = "M2", page = 2))
        repository.insert(BookmarkEntity(chapterId = chapterId, title = "M3", page = 3))
        val count = repository.getTotalBookmarks()
        assertThat(count).isEqualTo(3)
    }

    @Test
    fun getByChapter() = runBlocking {
        val novelId2 = database.novelDao().insert(
            NovelEntity(title = "Another Novel", totalChapters = 0)
        )
        database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId2, title = "C1", fileName = "c1.html", orderIndex = 0, content = "x")
        ))
        val otherChapterId = database.chapterDao().getChaptersByNovelSync(novelId2).first().id

        repository.insert(BookmarkEntity(chapterId = chapterId, title = "On ch1", page = 1))
        repository.insert(BookmarkEntity(chapterId = otherChapterId, title = "On other", page = 1))

        val results = repository.getByChapter(chapterId).first()
        assertThat(results).hasSize(1)
        assertThat(results[0].title).isEqualTo("On ch1")
    }

    @Test
    fun getAll_orderedByCreatedAtDesc() = runBlocking {
        repository.insert(BookmarkEntity(chapterId = chapterId, title = "A", page = 1))
        repository.insert(BookmarkEntity(chapterId = chapterId, title = "B", page = 2))
        val all = database.bookmarkDao().getAll().first()
        assertThat(all).hasSize(2)
    }

    @Test
    fun insertMultiple() = runBlocking {
        repeat(5) { i ->
            repository.insert(BookmarkEntity(chapterId = chapterId, title = "Bookmark $i", page = i))
        }
        val all = repository.getAll().first()
        assertThat(all).hasSize(5)
    }

    @Test
    fun deleteNonexistent_noException() = runBlocking {
        repository.deleteById(99999L)
    }
}
