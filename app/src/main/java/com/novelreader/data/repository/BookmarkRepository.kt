package com.novelreader.data.repository

import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao
) {
    fun getByChapter(chapterId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.getByChapter(chapterId)

    fun getAll(): Flow<List<BookmarkEntity>> =
        bookmarkDao.getAll()

    suspend fun insert(bookmark: BookmarkEntity): Long =
        bookmarkDao.insert(bookmark)

    suspend fun deleteById(id: Long) =
        bookmarkDao.deleteById(id)

    suspend fun getTotalBookmarks(): Int = bookmarkDao.getTotalCount()
}
