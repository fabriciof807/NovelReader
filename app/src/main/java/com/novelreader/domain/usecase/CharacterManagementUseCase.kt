package com.novelreader.domain.usecase

import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterManagementUseCase @Inject constructor(
    private val characterDao: CharacterDao,
    private val characterPhotoDao: CharacterPhotoDao
) {
    suspend fun addCharacter(novelId: Long, name: String, photoPath: String?): Result<CharacterEntity> {
        return try {
            val id = characterDao.insert(
                CharacterEntity(novelId = novelId, name = name, photoPath = photoPath)
            )
            if (photoPath != null) {
                characterPhotoDao.insert(
                    CharacterPhotoEntity(characterId = id, photoPath = photoPath, orderIndex = 0)
                )
            }
            Result.success(characterDao.getByNovelSync(novelId).first { it.id == id })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCharacter(id: Long, novelId: Long): Result<Unit> {
        return try {
            characterDao.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePhoto(id: Long, path: String): Result<Unit> {
        return try {
            characterDao.updatePhoto(id, path)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchAddPhotos(characterId: Long, photoPaths: List<String>): Result<Unit> {
        return try {
            val existing = characterPhotoDao.getByCharacterSync(characterId)
            var order = existing.size
            for (path in photoPaths) {
                characterPhotoDao.insert(
                    CharacterPhotoEntity(characterId = characterId, photoPath = path, orderIndex = order)
                )
                order++
            }
            if (existing.isEmpty() && photoPaths.isNotEmpty()) {
                characterDao.updatePhoto(characterId, photoPaths.first())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPhoto(characterId: Long, photoPath: String): Result<Unit> {
        return try {
            val existing = characterPhotoDao.getByCharacterSync(characterId)
            characterPhotoDao.insert(
                CharacterPhotoEntity(characterId = characterId, photoPath = photoPath, orderIndex = existing.size)
            )
            if (existing.isEmpty()) {
                characterDao.updatePhoto(characterId, photoPath)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePhoto(photoId: Long, characterId: Long): Result<Unit> {
        return try {
            characterPhotoDao.deleteById(photoId)
            val remaining = characterPhotoDao.getByCharacterSync(characterId)
            if (remaining.isNotEmpty()) {
                characterDao.updatePhoto(characterId, remaining.first().photoPath)
            } else {
                characterDao.updatePhoto(characterId, "")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateName(characterId: Long, name: String): Result<Unit> {
        return try {
            characterDao.updateName(characterId, name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateNotes(characterId: Long, notes: String?): Result<Unit> {
        return try {
            characterDao.updateNotes(characterId, notes)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(characterId: Long, isFavorite: Boolean): Result<Unit> {
        return try {
            characterDao.toggleFavorite(characterId, isFavorite)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCharacters(novelId: Long): List<CharacterEntity> {
        return characterDao.getByNovelSync(novelId)
    }
}
