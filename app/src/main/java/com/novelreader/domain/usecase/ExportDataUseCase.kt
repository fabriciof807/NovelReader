package com.novelreader.domain.usecase

import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.FolderDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.data.local.preferences.ReaderPreferences
import com.novelreader.data.local.preferences.SavedThemeCodec
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.di.qualifiers.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ExportOptions(
    val novels: Boolean = true,
    val bookmarks: Boolean = true,
    val characters: Boolean = true,
    val collections: Boolean = true,
    val settings: Boolean = true
)

@Singleton
class ExportDataUseCase @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val characterDao: CharacterDao,
    private val characterPhotoDao: CharacterPhotoDao,
    private val folderDao: FolderDao,
    private val appPreferences: AppPreferences,
    private val readerPreferences: ReaderPreferences,
    private val libraryPreferences: LibraryPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun execute(options: ExportOptions = ExportOptions()): String = withContext(ioDispatcher) {
        val root = JSONObject()

        root.put("version", 3)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

        val novels = novelDao.getAllNovels().first()
        root.put("novels", if (options.novels) exportNovels(novels) else JSONArray())

        val novelMap = novels.associateBy { it.id }
        root.put("bookmarks", if (options.bookmarks) exportBookmarks(novelMap) else JSONArray())

        root.put("characters", if (options.characters) exportCharacters(novelMap) else JSONArray())

        root.put("collections", if (options.collections) exportCollections() else JSONArray())
        root.put("settings", if (options.settings) exportSettings() else JSONObject())

        root.toString(2)
    }

    private suspend fun exportNovels(novels: List<NovelEntity>): JSONArray {
        val arr = JSONArray()
        for (novel in novels) {
            val lastChapter = novel.lastChapterId?.let { chapterDao.getChapterById(it) }
            arr.put(JSONObject().apply {
                put("title", novel.title)
                put("sourceUrl", novel.sourceUrl)
                put("isFavorite", novel.isFavorite)
                put("author", novel.author ?: "")
                put("totalChapters", novel.totalChapters)
                put("autoUpdate", novel.autoUpdate)
                put("lastReadAt", novel.lastReadAt)
                put("lastChapter", JSONObject().apply {
                    put("fileName", lastChapter?.fileName ?: "")
                    put("orderIndex", lastChapter?.orderIndex ?: 0)
                })
            })
        }
        return arr
    }

    private suspend fun exportBookmarks(novelMap: Map<Long, NovelEntity>): JSONArray {
        val arr = JSONArray()
        val bookmarks = bookmarkDao.getAllSync()
        for (bm in bookmarks) {
            val chapter = chapterDao.getChapterById(bm.chapterId)
            arr.put(JSONObject().apply {
                put("novelTitle", chapter?.let { novelMap[it.novelId]?.title } ?: "")
                put("title", bm.title)
                put("note", bm.note ?: "")
                put("page", bm.page)
                put("scrollPosition", bm.scrollPosition)
                put("createdAt", bm.createdAt)
                put("chapter", JSONObject().apply {
                    put("title", chapter?.title ?: "")
                    put("fileName", chapter?.fileName ?: "")
                    put("orderIndex", chapter?.orderIndex ?: 0)
                })
            })
        }
        return arr
    }

    private suspend fun exportCharacters(novelMap: Map<Long, NovelEntity>): JSONArray {
        val arr = JSONArray()
        val characters = characterDao.getAllCharactersSync()
        val characterIds = characters.map { it.id }
        val photosMap = characterPhotoDao.getByCharacterIds(characterIds)
            .groupBy { it.characterId }
        for (char in characters) {
            arr.put(JSONObject().apply {
                put("name", char.name)
                put("notes", char.notes ?: "")
                put("isFavorite", char.isFavorite)
                put("photoPath", char.photoPath ?: "")
                put("createdAt", char.createdAt)
                put("novelTitle", novelMap[char.novelId]?.title ?: "")
                put("photos", JSONArray().apply {
                    val photos = photosMap[char.id]
                    if (photos != null) {
                        for (photo in photos) {
                            put(JSONObject().apply {
                                put("photoPath", photo.photoPath)
                                put("orderIndex", photo.orderIndex)
                            })
                        }
                    }
                })
            })
        }
        return arr
    }

    private suspend fun exportCollections(): JSONArray {
        val arr = JSONArray()
        for (folder in folderDao.getAll().first()) {
            val titles = folderDao.getNovelsInFolder(folder.id).first().map { it.title }
            arr.put(JSONObject().apply {
                put("name", folder.name)
                put("isPinned", folder.isPinned)
                put("novels", JSONArray(titles))
            })
        }
        return arr
    }

    private suspend fun exportSettings(): JSONObject {
        val config = readerPreferences.config.first()
        return JSONObject().apply {
            put("appTheme", appPreferences.appTheme.first())
            put("locale", appPreferences.locale.first())
            put("dynamicColor", appPreferences.dynamicColorEnabled.first())
            put("appPalette", appPreferences.appPalette.first())
            appPreferences.accentColor.first()?.let { put("accentColor", it) }
            put("wallpaperHome", appPreferences.homeWallpaper.first())
            put("wallpaperHomeBlur", appPreferences.homeWallpaperBlur.first())
            put("wallpaperBehindBars", appPreferences.wallpaperBehindBars.first())
            val themes = appPreferences.savedThemes.first()
            if (themes.isNotEmpty()) {
                put("savedThemes", JSONArray(SavedThemeCodec.encode(themes)))
            }
            put("reader", JSONObject().apply {
                put("fontSize", config.fontSize)
                put("fontFamily", config.fontFamily)
                put("lineHeight", config.lineHeight.toDouble())
                put("theme", config.theme)
                config.accentColor?.let { put("accentColor", it) }
                put("wallpaper", config.wallpaper)
                put("wallpaperBlur", config.wallpaperBlur)
                put("veil", config.veil)
                put("autoScrollSpeed", config.autoScrollSpeed.toDouble())
                put("keepScreenOn", config.keepScreenOn)
                put("swipeDirection", config.swipeDirection)
                put("brightness", config.brightness)
                put("tapZones", config.tapZones)
            })
            put("library", JSONObject().apply {
                put("sortOrder", libraryPreferences.sortOrder.first())
                put("chapterSortOrder", libraryPreferences.chapterSortOrder.first())
                put("viewMode", libraryPreferences.viewMode.first())
            })
        }
    }
}
