package com.novelreader.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.novelreader.data.local.db.entity.NovelSourceEntity

@Dao
interface NovelSourceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: NovelSourceEntity): Long

    @Query("UPDATE novel_sources SET sourceUrl = :url, domain = :domain WHERE id = :id")
    suspend fun updateUrl(id: Long, url: String, domain: String)

    @Query("SELECT * FROM novel_sources WHERE novelId = :novelId ORDER BY isPrimary DESC, addedAt ASC")
    suspend fun getByNovel(novelId: Long): List<NovelSourceEntity>

    @Query("SELECT * FROM novel_sources WHERE novelId = :novelId AND isPrimary = 1 LIMIT 1")
    suspend fun getPrimary(novelId: Long): NovelSourceEntity?

    @Query("SELECT * FROM novel_sources WHERE novelId = :novelId AND sourceUrl = :url LIMIT 1")
    suspend fun findByNovelAndUrl(novelId: Long, url: String): NovelSourceEntity?

    @Query("UPDATE novel_sources SET isPrimary = 0 WHERE novelId = :novelId")
    suspend fun clearPrimary(novelId: Long)

    @Query("UPDATE novel_sources SET isPrimary = 1 WHERE id = :id")
    suspend fun setPrimary(id: Long)

    @Query("DELETE FROM novel_sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE novel_sources SET lastCheckedAt = :ts WHERE id = :id")
    suspend fun updateLastChecked(id: Long, ts: Long)

    @Query("UPDATE novel_sources SET autoUpdate = :on WHERE id = :id")
    suspend fun setAutoUpdate(id: Long, on: Boolean)

    @Query("""
        SELECT ns.* FROM novel_sources ns
        INNER JOIN novels n ON n.id = ns.novelId
        WHERE ns.autoUpdate = 1 AND n.autoUpdate = 1
    """)
    suspend fun getAllForAutoUpdate(): List<NovelSourceEntity>
}
