package com.novelreader.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.novelreader.data.local.db.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    @Query("SELECT * FROM characters WHERE novelId = :novelId ORDER BY isFavorite DESC, createdAt ASC")
    fun getByNovel(novelId: Long): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE novelId = :novelId ORDER BY isFavorite DESC, createdAt ASC")
    suspend fun getByNovelSync(novelId: Long): List<CharacterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(character: CharacterEntity): Long

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE characters SET photoPath = :path WHERE id = :id")
    suspend fun updatePhoto(id: Long, path: String)

    @Query("UPDATE characters SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("UPDATE characters SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)

    @Query("UPDATE characters SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
}
