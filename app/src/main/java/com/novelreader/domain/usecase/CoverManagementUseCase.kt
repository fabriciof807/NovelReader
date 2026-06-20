package com.novelreader.domain.usecase

import android.net.Uri
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.storage.CoverStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverManagementUseCase @Inject constructor(
    private val novelRepository: NovelRepository,
    private val coverStorage: CoverStorage
) {
    suspend fun saveFromUri(novelId: Long, uri: Uri): Result<String?> {
        return try {
            val path = coverStorage.saveFromUri(novelId, uri)
            if (path != null) {
                novelRepository.updateCoverPath(novelId, path)
            }
            Result.success(path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFromUrl(novelId: Long, url: String): Result<String?> {
        return try {
            val path = coverStorage.saveFromUrl(novelId, url)
            if (path != null) {
                novelRepository.updateCoverPath(novelId, path)
            }
            Result.success(path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteNovelCovers(novelId: Long, coverPath: String?): Result<Unit> {
        return try {
            coverStorage.deleteCoverIfOwnedByApp(novelId, coverPath)
            coverStorage.deleteCharacterFolder(novelId)
            novelRepository.deleteById(novelId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
