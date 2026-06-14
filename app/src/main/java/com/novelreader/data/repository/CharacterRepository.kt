package com.novelreader.data.repository

import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterRepository @Inject constructor(
    private val characterDao: CharacterDao
) {
    fun getByNovel(novelId: Long): Flow<List<CharacterEntity>> = characterDao.getByNovel(novelId)

    suspend fun getByNovelSync(novelId: Long): List<CharacterEntity> = characterDao.getByNovelSync(novelId)

    suspend fun insert(character: CharacterEntity): Long = characterDao.insert(character)

    suspend fun deleteById(id: Long) = characterDao.deleteById(id)

    suspend fun updatePhoto(id: Long, path: String) = characterDao.updatePhoto(id, path)

    suspend fun updateName(id: Long, name: String) = characterDao.updateName(id, name)

    suspend fun updateNotes(id: Long, notes: String?) = characterDao.updateNotes(id, notes)

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = characterDao.toggleFavorite(id, isFavorite)
}
