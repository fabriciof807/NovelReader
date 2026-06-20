package com.novelreader.domain.usecase

import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.data.repository.CharacterPhotoRepository
import com.novelreader.data.repository.CharacterRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterManagementUseCase @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val characterPhotoRepository: CharacterPhotoRepository
) {
    suspend fun addCharacter(novelId: Long, name: String, photoPath: String?): Result<CharacterEntity> {
        return try {
            val id = characterRepository.insert(
                CharacterEntity(novelId = novelId, name = name, photoPath = photoPath)
            )
            if (photoPath != null) {
                characterPhotoRepository.insert(
                    CharacterPhotoEntity(characterId = id, photoPath = photoPath, orderIndex = 0)
                )
            }
            Result.success(characterRepository.getByNovelSync(novelId).first { it.id == id })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCharacter(id: Long, novelId: Long): Result<Unit> {
        return try {
            characterRepository.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePhoto(id: Long, path: String): Result<Unit> {
        return try {
            characterRepository.updatePhoto(id, path)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchAddPhotos(characterId: Long, photoPaths: List<String>): Result<Unit> {
        return try {
            val existing = characterPhotoRepository.getByCharacterSync(characterId)
            var order = existing.size
            for (path in photoPaths) {
                characterPhotoRepository.insert(
                    CharacterPhotoEntity(characterId = characterId, photoPath = path, orderIndex = order)
                )
                order++
            }
            if (existing.isEmpty() && photoPaths.isNotEmpty()) {
                characterRepository.updatePhoto(characterId, photoPaths.first())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPhoto(characterId: Long, photoPath: String): Result<Unit> {
        return try {
            val existing = characterPhotoRepository.getByCharacterSync(characterId)
            characterPhotoRepository.insert(
                CharacterPhotoEntity(characterId = characterId, photoPath = photoPath, orderIndex = existing.size)
            )
            if (existing.isEmpty()) {
                characterRepository.updatePhoto(characterId, photoPath)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePhoto(photoId: Long, characterId: Long): Result<Unit> {
        return try {
            characterPhotoRepository.deleteById(photoId)
            val remaining = characterPhotoRepository.getByCharacterSync(characterId)
            if (remaining.isNotEmpty()) {
                characterRepository.updatePhoto(characterId, remaining.first().photoPath)
            } else {
                characterRepository.updatePhoto(characterId, "")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateName(characterId: Long, name: String): Result<Unit> {
        return try {
            characterRepository.updateName(characterId, name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateNotes(characterId: Long, notes: String?): Result<Unit> {
        return try {
            characterRepository.updateNotes(characterId, notes)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(characterId: Long, isFavorite: Boolean): Result<Unit> {
        return try {
            characterRepository.toggleFavorite(characterId, isFavorite)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCharacters(novelId: Long): List<CharacterEntity> {
        return characterRepository.getByNovelSync(novelId)
    }
}
