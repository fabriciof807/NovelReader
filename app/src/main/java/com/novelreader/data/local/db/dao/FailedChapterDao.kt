package com.novelreader.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.novelreader.data.local.db.entity.FailedChapterEntity

@Dao
interface FailedChapterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(failedChapter: FailedChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(failedChapters: List<FailedChapterEntity>)

    @Query("DELETE FROM failed_chapters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM failed_chapters WHERE novelId = :novelId")
    suspend fun deleteByNovelId(novelId: Long)

    @Query("DELETE FROM failed_chapters WHERE novelId = :novelId AND fileName = :fileName")
    suspend fun deleteByNovelAndFileName(novelId: Long, fileName: String)

    @Query("SELECT * FROM failed_chapters WHERE novelId = :novelId ORDER BY attemptedAt DESC")
    suspend fun getByNovel(novelId: Long): List<FailedChapterEntity>

    @Query("SELECT * FROM failed_chapters WHERE id = :id")
    suspend fun getById(id: Long): FailedChapterEntity?

    @Query("SELECT COUNT(*) FROM failed_chapters WHERE novelId = :novelId")
    suspend fun countByNovel(novelId: Long): Int
}
