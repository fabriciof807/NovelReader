package com.novelreader.data.repository

import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterPhotoRepository @Inject constructor(
    private val characterPhotoDao: CharacterPhotoDao
) {
    fun getByCharacter(characterId: Long): Flow<List<CharacterPhotoEntity>> =
        characterPhotoDao.getByCharacter(characterId)

    suspend fun getByCharacterSync(characterId: Long): List<CharacterPhotoEntity> =
        characterPhotoDao.getByCharacterSync(characterId)

    suspend fun getByCharacterIds(characterIds: List<Long>): List<CharacterPhotoEntity> =
        characterPhotoDao.getByCharacterIds(characterIds)

    suspend fun insert(photo: CharacterPhotoEntity): Long =
        characterPhotoDao.insert(photo)

    suspend fun deleteById(id: Long) =
        characterPhotoDao.deleteById(id)

    suspend fun getAllPhotosSync(): List<CharacterPhotoEntity> =
        characterPhotoDao.getAllPhotosSync()
}
