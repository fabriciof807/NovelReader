package com.novelreader.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.novelreader.data.local.db.entity.FolderEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.db.entity.NovelFolderCrossRef
import kotlinx.coroutines.flow.Flow

data class FolderCount(val folderId: Long, val count: Int)

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY isPinned DESC, name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE folders SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("SELECT COUNT(*) FROM folders WHERE isPinned = 1")
    suspend fun countPinned(): Int

    @Query("SELECT folderId as folderId, COUNT(*) as count FROM novel_folder GROUP BY folderId")
    fun getFolderCounts(): Flow<List<FolderCount>>

    @Query(
        "SELECT n.* FROM novels n INNER JOIN novel_folder nf ON n.id = nf.novelId " +
            "WHERE nf.folderId = :folderId ORDER BY n.lastReadAt DESC"
    )
    fun getNovelsInFolder(folderId: Long): Flow<List<NovelEntity>>

    @Query("SELECT folderId FROM novel_folder WHERE novelId = :novelId")
    suspend fun getFolderIdsForNovel(novelId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNovelToFolder(ref: NovelFolderCrossRef)

    @Query("DELETE FROM novel_folder WHERE folderId = :folderId AND novelId = :novelId")
    suspend fun removeNovelFromFolder(folderId: Long, novelId: Long)

    @Transaction
    suspend fun setNovelFolders(novelId: Long, folderIds: Set<Long>) {
        val current = getFolderIdsForNovel(novelId).toSet()
        for (id in folderIds - current) addNovelToFolder(NovelFolderCrossRef(id, novelId))
        for (id in current - folderIds) removeNovelFromFolder(id, novelId)
    }

    @Transaction
    suspend fun addNovelsToFolder(folderId: Long, novelIds: List<Long>) {
        for (novelId in novelIds) addNovelToFolder(NovelFolderCrossRef(folderId, novelId))
    }
}
