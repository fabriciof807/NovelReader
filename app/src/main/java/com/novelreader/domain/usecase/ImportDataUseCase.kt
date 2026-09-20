package com.novelreader.domain.usecase

import android.content.Context
import android.net.Uri
import com.novelreader.data.local.db.dao.FolderDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.FolderEntity
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.local.preferences.ReaderPreferences
import com.novelreader.data.local.preferences.SavedThemeCodec
import com.novelreader.data.storage.WallpaperStorage
import com.novelreader.data.storage.PendingBookmark
import com.novelreader.data.storage.PendingCharacter
import com.novelreader.data.storage.PendingCollectionLink
import com.novelreader.data.storage.PendingNovel
import com.novelreader.data.storage.PendingPhoto
import com.novelreader.data.storage.PendingRestoreStore
import com.novelreader.util.RemoteHostGuard
import com.novelreader.di.qualifiers.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class ImportPreviewEntry(
    val title: String,
    val sourceUrl: String
)

data class ImportPreview(
    val novels: List<ImportPreviewEntry>,
    val bookmarksCount: Int,
    val charactersCount: Int,
    val collectionsCount: Int = 0,
    val hasSettings: Boolean = false
)

data class ImportResult(
    val novelsQueued: List<String>,
    val novelsFailed: List<String>,
    val novelsLocal: List<String> = emptyList(),
    val settingsApplied: Boolean = false,
    val bookmarksRestored: Int = 0,
    val bookmarksPending: Int = 0,
    val charactersRestored: Int = 0,
    val charactersPending: Int = 0,
    val collectionLinksRestored: Int = 0,
    val collectionLinksPending: Int = 0
)

@Singleton
class ImportDataUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webImportUseCase: WebImportUseCase,
    private val backgroundImportManager: BackgroundImportManager,
    private val pendingRestoreStore: PendingRestoreStore,
    private val restoreApplier: PendingRestoreApplier,
    private val novelDao: NovelDao,
    private val folderDao: FolderDao,
    private val appPreferences: AppPreferences,
    private val readerPreferences: ReaderPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val wallpaperStorage: WallpaperStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun previewImport(uri: Uri): ImportPreview = withContext(ioDispatcher) {
        val root = JSONObject(readJson(uri))
        val novelsArr = root.optJSONArray("novels") ?: JSONArray()
        val novels = (0 until novelsArr.length()).map { i ->
            val obj = novelsArr.getJSONObject(i)
            ImportPreviewEntry(
                title = obj.optString("title"),
                sourceUrl = obj.optString("sourceUrl")
            )
        }
        ImportPreview(
            novels = novels,
            bookmarksCount = (root.optJSONArray("bookmarks") ?: JSONArray()).length(),
            charactersCount = (root.optJSONArray("characters") ?: JSONArray()).length(),
            collectionsCount = (root.optJSONArray("collections") ?: JSONArray()).length(),
            hasSettings = root.optJSONObject("settings")?.length() ?: 0 > 0
        )
    }

    suspend fun execute(
        uri: Uri,
        selectedTitles: Set<String>? = null
    ): ImportResult = withContext(ioDispatcher) {
        val root = JSONObject(readJson(uri))

        val settingsApplied = applySettings(root.optJSONObject("settings"))

        val novelsArr = root.optJSONArray("novels") ?: JSONArray()

        val queued = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val local = mutableListOf<String>()
        val titleRemap = mutableMapOf<String, String>()

        for (i in 0 until novelsArr.length()) {
            val novel = novelsArr.getJSONObject(i)
            val title = novel.optString("title")
            val sourceUrl = novel.optString("sourceUrl")
            val isFavorite = if (novel.has("isFavorite")) novel.getBoolean("isFavorite") else null
            if (selectedTitles != null && title !in selectedTitles) continue
            if (sourceUrl.isBlank()) {
                local.add(title)
                continue
            }
            val host = try {
                java.net.URI(sourceUrl).host.orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (!RemoteHostGuard.isAllowed(host)) {
                failed.add(title)
                continue
            }
            try {
                val result = webImportUseCase.fetchChapterList(sourceUrl)
                result.onSuccess { fetchResult ->
                    if (fetchResult.chapters.isNotEmpty()) {
                        val novelTitle = fetchResult.novelTitle ?: title
                        titleRemap[title] = novelTitle
                        val chapterLinks = fetchResult.chapters.map { link ->
                            ChapterLink(link.title, link.url, link.chapterNumber)
                        }
                        backgroundImportManager.startImport(
                            novelTitle = novelTitle,
                            links = chapterLinks,
                            coverUrl = fetchResult.coverUrl,
                            sourceUrl = sourceUrl,
                            isFavorite = isFavorite
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

        val finalTitle: (String) -> String = { backupTitle -> titleRemap[backupTitle] ?: backupTitle }

        suspend fun isProcessable(backupTitle: String): Boolean {
            val target = finalTitle(backupTitle)
            return target in queued || novelDao.getNovelByTitleIgnoreCase(target) != null
        }

        val bookmarksArr = root.optJSONArray("bookmarks") ?: JSONArray()
        val charactersArr = root.optJSONArray("characters") ?: JSONArray()
        val collectionsArr = root.optJSONArray("collections") ?: JSONArray()

        val pendingBookmarks = mutableListOf<PendingBookmark>()
        var processedBookmarks = 0
        for (i in 0 until bookmarksArr.length()) {
            val obj = bookmarksArr.getJSONObject(i)
            val backupTitle = obj.optString("novelTitle")
            // ponytail: v2 backups have no novel reference — nothing to attribute them to
            if (backupTitle.isBlank()) continue
            if (!isProcessable(backupTitle)) continue
            processedBookmarks++
            val chapterObj = obj.optJSONObject("chapter") ?: JSONObject()
            pendingBookmarks += PendingBookmark(
                novelTitle = finalTitle(backupTitle),
                title = obj.optString("title"),
                note = obj.optString("note").takeIf { it.isNotBlank() },
                page = obj.optInt("page"),
                scrollPosition = obj.optInt("scrollPosition"),
                createdAt = obj.optLong("createdAt"),
                chapterFileName = chapterObj.optString("fileName"),
                chapterOrderIndex = chapterObj.optInt("orderIndex")
            )
        }

        val pendingCharacters = mutableListOf<PendingCharacter>()
        var processedCharacters = 0
        for (i in 0 until charactersArr.length()) {
            val obj = charactersArr.getJSONObject(i)
            val backupTitle = obj.optString("novelTitle")
            if (backupTitle.isBlank()) continue
            if (!isProcessable(backupTitle)) continue
            processedCharacters++
            pendingCharacters += PendingCharacter(
                novelTitle = finalTitle(backupTitle),
                name = obj.optString("name"),
                notes = obj.optString("notes").takeIf { it.isNotBlank() },
                isFavorite = obj.optBoolean("isFavorite"),
                photoPath = obj.optString("photoPath").takeIf { it.isNotBlank() },
                createdAt = obj.optLong("createdAt"),
                photos = (obj.optJSONArray("photos") ?: JSONArray()).let { arr ->
                    (0 until arr.length()).mapNotNull { j ->
                        val p = arr.optJSONObject(j) ?: return@mapNotNull null
                        PendingPhoto(p.optString("photoPath"), p.optInt("orderIndex"))
                    }
                }
            )
        }

        val pendingLinks = mutableListOf<PendingCollectionLink>()
        var processedLinks = 0
        for (i in 0 until collectionsArr.length()) {
            val obj = collectionsArr.getJSONObject(i)
            val name = obj.optString("name")
            if (name.isBlank()) continue
            ensureFolder(name, obj.optBoolean("isPinned"))
            val titles = obj.optJSONArray("novels") ?: JSONArray()
            for (j in 0 until titles.length()) {
                val backupTitle = titles.optString(j)
                if (backupTitle.isBlank()) continue
                if (!isProcessable(backupTitle)) continue
                processedLinks++
                pendingLinks += PendingCollectionLink(folderName = name, novelTitle = finalTitle(backupTitle))
            }
        }

        val pendingNovels = mutableListOf<PendingNovel>()
        for (i in 0 until novelsArr.length()) {
            val novel = novelsArr.getJSONObject(i)
            val title = novel.optString("title")
            if (selectedTitles != null && title !in selectedTitles) continue
            val target = finalTitle(title)
            if (target !in queued && !isProcessable(title)) continue
            val lastChapter = novel.optJSONObject("lastChapter") ?: JSONObject()
            pendingNovels += PendingNovel(
                title = target,
                author = novel.optString("author").takeIf { it.isNotBlank() },
                autoUpdate = novel.optBoolean("autoUpdate"),
                lastReadAt = novel.optLong("lastReadAt"),
                lastChapterFileName = lastChapter.optString("fileName"),
                lastChapterOrderIndex = lastChapter.optInt("orderIndex")
            )
        }

        if (pendingBookmarks.isNotEmpty() || pendingCharacters.isNotEmpty() ||
            pendingLinks.isNotEmpty() || pendingNovels.isNotEmpty()
        ) {
            pendingRestoreStore.update { current ->
                current.copy(
                    bookmarks = current.bookmarks + pendingBookmarks,
                    characters = current.characters + pendingCharacters,
                    collectionLinks = current.collectionLinks + pendingLinks,
                    novels = current.novels + pendingNovels
                )
            }
        }

        val applied = restoreApplier.applyPending()

        ImportResult(
            novelsQueued = queued,
            novelsFailed = failed,
            novelsLocal = local,
            settingsApplied = settingsApplied,
            bookmarksRestored = applied.bookmarks,
            bookmarksPending = processedBookmarks - applied.bookmarks,
            charactersRestored = applied.characters,
            charactersPending = processedCharacters - applied.characters,
            collectionLinksRestored = applied.collectionLinks,
            collectionLinksPending = processedLinks - applied.collectionLinks
        )
    }

    private suspend fun ensureFolder(name: String, isPinned: Boolean) {
        val exists = folderDao.getAll().first().any { it.name.equals(name, ignoreCase = true) }
        if (!exists) {
            folderDao.insert(FolderEntity(name = name, isPinned = isPinned))
        }
    }

    private suspend fun restorableWallpaperRef(ref: String): String {
        val sanitized = PreferenceAllowlists.sanitizeWallpaperRef(ref)
        if (PreferenceAllowlists.WALLPAPER_NONE == sanitized) return sanitized
        if (WallpaperStorage.builtinId(sanitized) != null) return sanitized
        return if (wallpaperStorage.exists(sanitized)) sanitized
        else PreferenceAllowlists.WALLPAPER_NONE
    }

    private suspend fun applySettings(settings: JSONObject?): Boolean = withContext(ioDispatcher) {
        if (settings == null || settings.length() == 0) return@withContext false
        settings.optString("appTheme").takeIf { it.isNotBlank() }?.let { appPreferences.updateAppTheme(it) }
        settings.optString("locale").takeIf { it.isNotBlank() }?.let { appPreferences.updateLocale(it) }
        if (settings.has("dynamicColor")) {
            appPreferences.updateDynamicColorEnabled(settings.getBoolean("dynamicColor"))
        }
        settings.optString("appPalette").takeIf { it.isNotBlank() }?.let {
            appPreferences.updateAppPalette(it)
        }
        if (settings.has("accentColor")) {
            appPreferences.updateAccentColor(settings.optString("accentColor"))
        }
        if (settings.has("wallpaperBehindBars")) {
            appPreferences.updateWallpaperBehindBars(settings.getBoolean("wallpaperBehindBars"))
        }
        settings.optJSONArray("savedThemes")?.let { themes ->
            appPreferences.updateSavedThemes(SavedThemeCodec.decode(themes.toString()))
        }
        settings.optString("wallpaperHome").takeIf { it.isNotBlank() }?.let { ref ->
            appPreferences.updateHomeWallpaper(restorableWallpaperRef(ref))
        }
        if (settings.has("wallpaperHomeBlur")) {
            appPreferences.updateHomeWallpaperBlur(settings.getInt("wallpaperHomeBlur"))
        }
        settings.optJSONObject("reader")?.let { r ->
            if (r.has("fontSize")) readerPreferences.updateFontSize(r.getInt("fontSize"))
            r.optString("fontFamily").takeIf { it.isNotBlank() }?.let { readerPreferences.updateFontFamily(it) }
            if (r.has("lineHeight")) readerPreferences.updateLineHeight(r.getDouble("lineHeight").toFloat())
            r.optString("theme").takeIf { it.isNotBlank() }?.let { readerPreferences.updateTheme(it) }
            if (r.has("autoScrollSpeed")) {
                readerPreferences.updateAutoScrollSpeed(r.getDouble("autoScrollSpeed").toFloat())
            }
            if (r.has("keepScreenOn")) readerPreferences.updateKeepScreenOn(r.getBoolean("keepScreenOn"))
            r.optString("swipeDirection").takeIf { it.isNotBlank() }?.let { readerPreferences.updateSwipeDirection(it) }
            if (r.has("brightness")) readerPreferences.updateBrightness(r.optInt("brightness"))
            if (r.has("accentColor")) readerPreferences.updateAccentColor(r.optString("accentColor"))
            r.optString("wallpaper").takeIf { it.isNotBlank() }?.let { ref ->
                readerPreferences.updateWallpaper(restorableWallpaperRef(ref))
            }
            if (r.has("wallpaperBlur")) readerPreferences.updateWallpaperBlur(r.getInt("wallpaperBlur"))
            if (r.has("veil")) readerPreferences.updateVeil(r.getInt("veil"))
        }
        settings.optJSONObject("library")?.let { l ->
            l.optString("sortOrder").takeIf { it.isNotBlank() }?.let { libraryPreferences.updateSortOrder(it) }
            l.optString("chapterSortOrder").takeIf { it.isNotBlank() }?.let { libraryPreferences.updateChapterSortOrder(it) }
            l.optString("viewMode").takeIf { it.isNotBlank() }?.let { libraryPreferences.updateViewMode(it) }
        }
        true
    }

    private suspend fun readJson(uri: Uri): String = withContext(ioDispatcher) {
        val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)))
        reader.use { it.readText() }
    }
}
