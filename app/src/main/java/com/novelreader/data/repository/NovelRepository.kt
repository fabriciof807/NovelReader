package com.novelreader.data.repository

import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NovelRepository @Inject constructor(
    private val novelDao: NovelDao
) {
    fun getAllNovels(): Flow<List<NovelEntity>> = novelDao.getAllNovels()

    suspend fun getNovelById(id: Long): NovelEntity? = novelDao.getNovelById(id)

    suspend fun getNovelByTitle(title: String): NovelEntity? = novelDao.getNovelByTitle(title)

    suspend fun insert(novel: NovelEntity): Long = novelDao.insert(novel)

    suspend fun deleteById(id: Long) = novelDao.deleteById(id)

    suspend fun updateLastRead(novelId: Long, chapterId: Long) =
        novelDao.updateLastRead(novelId, chapterId)

    suspend fun updateCoverPath(id: Long, path: String) =
        novelDao.updateCoverPath(id, path)

    suspend fun updateChapterCount(id: Long, count: Int) =
        novelDao.updateChapterCount(id, count)

    suspend fun getNovelsByIds(ids: List<Long>): List<NovelEntity> =
        novelDao.getNovelsByIds(ids)

    suspend fun getAutoUpdateNovels(): List<NovelEntity> =
        novelDao.getAutoUpdateNovels()

    suspend fun updateSourceUrl(id: Long, sourceUrl: String) =
        novelDao.updateSourceUrl(id, sourceUrl)

    suspend fun updateLastChecked(novelId: Long, timestamp: Long) =
        novelDao.updateLastChecked(novelId, timestamp)

    suspend fun updateAutoUpdate(novelId: Long, enabled: Boolean) =
        novelDao.updateAutoUpdate(novelId, enabled)
}
