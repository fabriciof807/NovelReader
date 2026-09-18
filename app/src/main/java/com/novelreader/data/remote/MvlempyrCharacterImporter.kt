package com.novelreader.data.remote

import android.content.Context
import com.novelreader.R
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.di.qualifiers.IoDispatcher
import com.novelreader.domain.usecase.webimport.HttpClient
import com.novelreader.domain.usecase.webimport.HttpResponse
import com.novelreader.domain.usecase.webimport.RemoteRequestPolicy
import com.novelreader.util.StringUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val MVLEMPYR_API_BASE_URL =
    "https://chap.heliosarchive.online/wp-json/wp/v2/mvl-characters"

private const val MAX_CONSECUTIVE_FAILURES = 3

@Singleton
class MvlempyrCharacterImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterDao: CharacterDao,
    private val characterPhotoDao: CharacterPhotoDao,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val httpClient: HttpClient
) {

    data class ImportedCharacter(
        val name: String,
        val designImageUrl: String,
        val avatarUrl: String,
        val description: String
    )

    suspend fun fetchCharacters(url: String): List<ImportedCharacter> = withContext(io) {
        val pageResponse = httpClient.get(
            url = url,
            policy = RemoteRequestPolicy.AnyPublicHttps,
            maxBodyBytes = HttpClient.DEFAULT_MAX_BODY_BYTES,
            maxDecompressedBytes = HttpClient.DEFAULT_MAX_DECOMPRESSED_BYTES
        )
        if (pageResponse.statusCode !in 200..299) {
            throw Exception(context.getString(R.string.import_characters_error))
        }

        val bookIdRegex = Regex("""filter\(e\s*=>\s*"(\d+)"\s*===\s*e\.BookId\)""")
        val bookId = bookIdRegex.find(pageResponse.body)?.groupValues?.get(1)
            ?: throw Exception(context.getString(R.string.mvlempyr_book_id_not_found))

        val allCharacters = mutableListOf<JSONObject>()
        var consecutiveFailures = 0
        for (page in 1..MAX_API_PAGES) {
            val response = try {
                fetchApiPage(page)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val pageItems = response
                ?.takeIf { it.statusCode in 200..299 }
                ?.let { runCatching { JSONArray(it.body) }.getOrNull() }
            if (pageItems == null) {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) break
                continue
            }
            consecutiveFailures = 0
            if (pageItems.length() == 0) break
            for (index in 0 until pageItems.length()) {
                if (allCharacters.size >= MAX_API_ENTRIES) break
                allCharacters += pageItems.getJSONObject(index)
            }
            if (allCharacters.size >= MAX_API_ENTRIES) break
        }

        val filtered = allCharacters.filter { it.optString("BookId") == bookId }

        if (filtered.isEmpty()) throw Exception(context.getString(R.string.mvlempyr_no_characters_found))

        filtered.map { obj ->
            ImportedCharacter(
                name = obj.optString("Name", ""),
                designImageUrl = obj.optString("DesignImage", ""),
                avatarUrl = obj.optString("Avatar", ""),
                description = obj.optString("Description", "")
            )
        }
    }

    suspend fun importCharacters(url: String, novelId: Long): Int = withContext(io) {
        val characters = fetchCharacters(url)
        var count = 0
        for (char in characters) {
            val charDir = File(context.filesDir, "characters/$novelId/${sanitizeFileName(char.name)}")
            charDir.mkdirs()

            val designFile = File(charDir, "design.jpeg")
            val designPath = if (char.designImageUrl.isNotEmpty()) {
                downloadImage(char.designImageUrl, designFile)?.absolutePath
            } else null

            val characterId = characterDao.insert(
                CharacterEntity(
                    novelId = novelId,
                    name = char.name,
                    photoPath = designPath,
                    notes = char.description
                )
            )

            if (designPath != null) {
                characterPhotoDao.insert(
                    CharacterPhotoEntity(
                        characterId = characterId,
                        photoPath = designPath,
                        orderIndex = 0
                    )
                )
            }
            count++
        }
        count
    }

    private suspend fun fetchApiPage(page: Int): HttpResponse = httpClient.get(
        url = "$MVLEMPYR_API_BASE_URL?per_page=$API_PAGE_SIZE&page=$page",
        policy = RemoteRequestPolicy.AnyPublicHttps,
        maxBodyBytes = MAX_API_PAGE_BYTES,
        maxDecompressedBytes = MAX_API_PAGE_BYTES
    )

    private suspend fun downloadImage(url: String, dest: File): File? {
        val temp = File(dest.parentFile, "${dest.name}.tmp")
        return try {
            val response = httpClient.get(
                url = url,
                policy = RemoteRequestPolicy.AnyPublicHttps,
                maxBodyBytes = MAX_IMAGE_BYTES,
                maxDecompressedBytes = MAX_IMAGE_BYTES
            )
            if (response.statusCode !in 200..299) return null
            val bytes = response.bodyBytes ?: return null
            temp.writeBytes(bytes)
            if (!temp.renameTo(dest)) return null
            dest
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            temp.delete()
        }
    }

    private fun sanitizeFileName(name: String): String {
        return StringUtils.sanitizeFileName(name)
    }

    companion object {
        internal const val API_PAGE_SIZE = 100
        internal const val MAX_API_PAGES = 50
        internal const val MAX_API_ENTRIES = 5_000
        internal const val MAX_API_PAGE_BYTES = 1024 * 1024
        internal const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    }
}
