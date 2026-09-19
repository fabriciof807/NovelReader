package com.novelreader.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NewChapterItem
import com.novelreader.data.local.db.entity.NovelReadCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY orderIndex ASC")
    suspend fun getChaptersByNovelSync(novelId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY orderIndex ASC")
    fun observeChaptersByNovel(novelId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT COUNT(*) FROM chapters WHERE novelId = :novelId")
    suspend fun countByNovel(novelId: Long): Int

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND fileName = :fileName LIMIT 1")
    suspend fun getChapterByNovelAndFileName(novelId: Long, fileName: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("UPDATE chapters SET isRead = 1, lastScrollPosition = :position WHERE id = :id")
    suspend fun markAsRead(id: Long, position: Int = 0)

    @Query("UPDATE chapters SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, orderIndex: Int)

    @Query("UPDATE chapters SET content = :content, title = :title WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, title: String)

    @Query("SELECT * FROM chapters WHERE id IN (:ids)")
    suspend fun getChaptersByIds(ids: List<Long>): List<ChapterEntity>

    @Query("SELECT chapters.* FROM chapters INNER JOIN chapters_fts ON chapters.rowid = chapters_fts.rowid WHERE chapters.novelId = :novelId AND chapters_fts MATCH :query ORDER BY chapters.orderIndex ASC")
    suspend fun searchInNovel(novelId: Long, query: String): List<ChapterEntity>

    @Query("SELECT novelId, COUNT(*) as readCount FROM chapters WHERE isRead = 1 GROUP BY novelId")
    suspend fun getReadCountPerNovel(): List<NovelReadCount>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND (content IS NULL OR content = '')")
    suspend fun getEmptyChapters(novelId: Long): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE novelId = :novelId")
    suspend fun deleteByNovelId(novelId: Long)

    @Query("DELETE FROM chapters WHERE novelId = :novelId AND fileName = :fileName")
    suspend fun deleteByNovelIdAndFileName(novelId: Long, fileName: String): Int

    @Query(
        "SELECT n.title AS novelTitle, n.id AS novelId, c.title AS chapterTitle " +
            "FROM chapters c INNER JOIN novels n ON c.novelId = n.id " +
            "WHERE c.isNew = 1 ORDER BY n.lastReadAt DESC, c.orderIndex ASC"
    )
    fun getNewChaptersFlow(): Flow<List<NewChapterItem>>

    @Query("SELECT COUNT(*) FROM chapters WHERE isNew = 1")
    suspend fun countNewChapters(): Int

    @Query("UPDATE chapters SET isNew = 0")
    suspend fun clearAllNewFlags()
}
