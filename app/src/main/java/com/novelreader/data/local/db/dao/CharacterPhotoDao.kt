package com.novelreader.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterPhotoDao {

    @Query("SELECT * FROM character_photos WHERE characterId = :characterId ORDER BY orderIndex ASC")
    fun getByCharacter(characterId: Long): Flow<List<CharacterPhotoEntity>>

    @Query("SELECT * FROM character_photos WHERE characterId = :characterId ORDER BY orderIndex ASC")
    suspend fun getByCharacterSync(characterId: Long): List<CharacterPhotoEntity>

    @Query("SELECT * FROM character_photos WHERE characterId IN (:characterIds) ORDER BY characterId, orderIndex ASC")
    suspend fun getByCharacterIds(characterIds: List<Long>): List<CharacterPhotoEntity>

    @Query("SELECT * FROM character_photos")
    suspend fun getAllPhotosSync(): List<CharacterPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: CharacterPhotoEntity): Long

    @Query("DELETE FROM character_photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
