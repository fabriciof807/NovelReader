package com.novelreader.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    @Query("SELECT * FROM novels ORDER BY lastReadAt DESC")
    fun getAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getNovelById(id: Long): NovelEntity?

    @Query("SELECT * FROM novels WHERE title = :title LIMIT 1")
    suspend fun getNovelByTitle(title: String): NovelEntity?

    @Query("SELECT * FROM novels WHERE title = :title COLLATE NOCASE LIMIT 1")
    suspend fun getNovelByTitleIgnoreCase(title: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(novel: NovelEntity): Long

    @Query("DELETE FROM novels WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE novels SET lastChapterId = :chapterId, lastReadAt = :timestamp WHERE id = :novelId")
    suspend fun updateLastRead(novelId: Long, chapterId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE novels SET totalChapters = :count WHERE id = :id")
    suspend fun updateChapterCount(id: Long, count: Int)

    @Query("UPDATE novels SET coverPath = :path WHERE id = :id")
    suspend fun updateCoverPath(id: Long, path: String)

    @Query("SELECT * FROM novels WHERE id IN (:ids)")
    suspend fun getNovelsByIds(ids: List<Long>): List<NovelEntity>

    @Query("SELECT * FROM novels WHERE autoUpdate = 1 AND sourceUrl != ''")
    suspend fun getAutoUpdateNovels(): List<NovelEntity>

    @Query("UPDATE novels SET sourceUrl = :sourceUrl WHERE id = :id")
    suspend fun updateSourceUrl(id: Long, sourceUrl: String)

    @Query("UPDATE novels SET lastCheckedAt = :timestamp WHERE id = :novelId")
    suspend fun updateLastChecked(novelId: Long, timestamp: Long)

    @Query("UPDATE novels SET autoUpdate = :enabled WHERE id = :novelId")
    suspend fun updateAutoUpdate(novelId: Long, enabled: Boolean)

    @Query("UPDATE novels SET hasUpdates = :on WHERE id = :id")
    suspend fun setHasUpdates(id: Long, on: Boolean)

    @Query("UPDATE novels SET lastChapterId = :chapterId WHERE id = :novelId")
    suspend fun updateLastChapterId(novelId: Long, chapterId: Long?)
}
