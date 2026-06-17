package com.novelreader.domain.usecase

import android.content.Context
import android.net.Uri
import com.novelreader.data.local.preferences.PendingImportPreferences
import com.novelreader.di.qualifiers.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val novelsQueued: List<String>,
    val novelsFailed: List<String>,
    val bookmarksPending: Int,
    val charactersPending: Int
)

@Singleton
class ImportDataUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webImportUseCase: WebImportUseCase,
    private val backgroundImportManager: BackgroundImportManager,
    private val pendingImportPreferences: PendingImportPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun execute(uri: Uri): ImportResult = withContext(ioDispatcher) {
        val json = readJson(uri)
        val root = JSONObject(json)

        val novelsArr = root.optJSONArray("novels") ?: JSONArray()
        val bookmarksArr = root.optJSONArray("bookmarks") ?: JSONArray()
        val charactersArr = root.optJSONArray("characters") ?: JSONArray()

        val queued = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (i in 0 until novelsArr.length()) {
            val novel = novelsArr.getJSONObject(i)
            val title = novel.optString("title")
            val sourceUrl = novel.optString("sourceUrl")
            if (sourceUrl.isBlank()) continue
            try {
                val result = webImportUseCase.fetchChapterList(sourceUrl)
                result.onSuccess { fetchResult ->
                    if (fetchResult.chapters.isNotEmpty()) {
                        val novelTitle = fetchResult.novelTitle ?: title
                        val chapterLinks = fetchResult.chapters.map { link ->
                            ChapterLink(link.title, link.url, link.chapterNumber)
                        }
                        backgroundImportManager.startImport(
                            novelTitle = novelTitle,
                            links = chapterLinks,
                            coverUrl = fetchResult.coverUrl,
                            sourceUrl = sourceUrl
                        )
                        queued.add(novelTitle)
                    }
                }.onFailure {
                    failed.add(title)
                }
            } catch (_: Exception) {
                failed.add(title)
            }
        }

        val pendingBookmarks = mutableListOf<String>()
        for (i in 0 until bookmarksArr.length()) {
            pendingBookmarks.add(
                PendingImportPreferences.encodeBookmark(bookmarksArr.getJSONObject(i))
            )
        }

        val pendingCharacters = mutableListOf<String>()
        for (i in 0 until charactersArr.length()) {
            pendingCharacters.add(
                PendingImportPreferences.encodeCharacter(charactersArr.getJSONObject(i))
            )
        }

        if (pendingBookmarks.isNotEmpty()) {
            pendingImportPreferences.savePendingBookmarks(pendingBookmarks)
        }
        if (pendingCharacters.isNotEmpty()) {
            pendingImportPreferences.savePendingCharacters(pendingCharacters)
        }

        ImportResult(
            novelsQueued = queued,
            novelsFailed = failed,
            bookmarksPending = pendingBookmarks.size,
            charactersPending = pendingCharacters.size
        )
    }

    private suspend fun readJson(uri: Uri): String = withContext(ioDispatcher) {
        val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)))
        reader.use { it.readText() }
    }
}
