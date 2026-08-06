package com.novelreader.data.remote

import android.content.Context
import com.novelreader.R
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.di.qualifiers.IoDispatcher
import com.novelreader.util.StringUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.jsoup.Jsoup
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val MVLEMPYR_API_URL =
    "https://chap.heliosarchive.online/wp-json/wp/v2/mvl-characters?per_page=15000"

@Singleton
class MvlempyrCharacterImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterDao: CharacterDao,
    private val characterPhotoDao: CharacterPhotoDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    data class ImportedCharacter(
        val name: String,
        val designImageUrl: String,
        val avatarUrl: String,
        val description: String
    )

    suspend fun fetchCharacters(url: String): List<ImportedCharacter> = withContext(io) {
        if (!url.startsWith("https://")) throw SecurityException("Apenas HTTPS permitido")
        val html = Jsoup.connect(url).timeout(30_000).followRedirects(true).get().html()

        val bookIdRegex = Regex("""filter\(e\s*=>\s*"(\d+)"\s*===\s*e\.BookId\)""")
        val bookId = bookIdRegex.find(html)?.groupValues?.get(1)
            ?: throw Exception(context.getString(R.string.mvlempyr_book_id_not_found))

        val json = URL(MVLEMPYR_API_URL).readText()
        val allCharacters = JSONArray(json)

        val filtered = (0 until allCharacters.length())
            .map { allCharacters.getJSONObject(it) }
            .filter { it.optString("BookId") == bookId }

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

    private fun downloadImage(url: String, dest: File): File? {
        if (!url.startsWith("https://")) return null
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val contentLength = connection.contentLength
            if (contentLength > 10 * 1024 * 1024) return null
            connection.getInputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        return StringUtils.sanitizeFileName(name)
    }
}
