package com.novelreader.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FolderDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.FolderEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.data.local.preferences.ReaderPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportDataUseCaseTest {

    private val novelDao: NovelDao = mockk()
    private val chapterDao: ChapterDao = mockk()
    private val bookmarkDao: BookmarkDao = mockk()
    private val characterDao: CharacterDao = mockk()
    private val characterPhotoDao: CharacterPhotoDao = mockk()
    private val folderDao: FolderDao = mockk()
    private val appPreferences: AppPreferences = mockk()
    private val readerPreferences: ReaderPreferences = mockk()
    private val libraryPreferences: LibraryPreferences = mockk()

    private lateinit var useCase: ExportDataUseCase

    private val exportedNovel = NovelEntity(
        id = 1,
        title = "Favorite novel",
        sourceUrl = "https://example.com/novel",
        isFavorite = true,
        author = "Autor",
        totalChapters = 42,
        autoUpdate = true,
        lastReadAt = 1234L,
        lastChapterId = 10
    )

    @Before
    fun setUp() {
        useCase = ExportDataUseCase(
            novelDao = novelDao,
            chapterDao = chapterDao,
            bookmarkDao = bookmarkDao,
            characterDao = characterDao,
            characterPhotoDao = characterPhotoDao,
            folderDao = folderDao,
            appPreferences = appPreferences,
            readerPreferences = readerPreferences,
            libraryPreferences = libraryPreferences,
            ioDispatcher = Dispatchers.Unconfined
        )
        every { novelDao.getAllNovels() } returns flowOf(listOf(exportedNovel))
        coEvery { chapterDao.getChapterById(10) } returns ChapterEntity(
            id = 10,
            novelId = 1,
            title = "Chapter 1",
            fileName = "chapter-1",
            orderIndex = 1,
            content = "content"
        )
        coEvery { bookmarkDao.getAllSync() } returns listOf(
            BookmarkEntity(id = 1, chapterId = 10, title = "Bookmark")
        )
        coEvery { characterDao.getAllCharactersSync() } returns listOf(
            CharacterEntity(id = 20, novelId = 1, name = "Character")
        )
        coEvery { characterPhotoDao.getByCharacterIds(any()) } returns emptyList()
        every { folderDao.getAll() } returns flowOf(
            listOf(FolderEntity(id = 5, name = "Coleção", isPinned = true))
        )
        every { folderDao.getNovelsInFolder(5) } returns flowOf(listOf(exportedNovel))
        stubPrefs()
    }

    private fun stubPrefs() {
        every { appPreferences.appTheme } returns flowOf("dark")
        every { appPreferences.locale } returns flowOf("pt")
        every { appPreferences.dynamicColorEnabled } returns flowOf(false)
        every { appPreferences.appPalette } returns flowOf("papel")
        every { appPreferences.accentColor } returns flowOf("#ff6f00")
        every { appPreferences.homeWallpaper } returns flowOf("builtin:noite")
        every { appPreferences.homeWallpaperBlur } returns flowOf(18)
        every { appPreferences.wallpaperBehindBars } returns flowOf(false)
        every { appPreferences.savedThemes } returns flowOf(
            listOf(
                com.novelreader.data.local.preferences.SavedTheme(
                    "Noite", "amoled", "#7c4dff", "papel:dark", "#8d6e63"
                )
            )
        )
        every { readerPreferences.config } returns flowOf(
            com.novelreader.data.local.preferences.ReaderConfig(
                fontSize = 24, fontFamily = "sans", lineHeight = 2f, theme = "papel:light",
                accentColor = "#8d6e63", wallpaper = "file:reader_1.jpg",
                wallpaperBlur = 24, veil = 70,
                autoScrollSpeed = 1.5f, keepScreenOn = false, swipeDirection = "horizontal"
            )
        )
        every { libraryPreferences.sortOrder } returns flowOf("TITLE")
        every { libraryPreferences.chapterSortOrder } returns flowOf("DESCENDING")
        every { libraryPreferences.viewMode } returns flowOf("LIST")
    }

    @Test
    fun `default export writes version 3 and novel favorite`() = runTest {
        val root = JSONObject(useCase.execute())

        assertThat(root.getInt("version")).isEqualTo(3)
        assertThat(root.getJSONArray("novels").getJSONObject(0).getBoolean("isFavorite"))
            .isTrue()
        assertThat(root.getJSONArray("bookmarks").length()).isEqualTo(1)
        assertThat(root.getJSONArray("characters").length()).isEqualTo(1)
        assertThat(root.getJSONArray("collections").length()).isEqualTo(1)
        assertThat(root.getJSONObject("settings").has("reader")).isTrue()
    }

    @Test
    fun `novel export carries restore fields and last chapter pointer`() = runTest {
        val root = JSONObject(useCase.execute())
        val novel = root.getJSONArray("novels").getJSONObject(0)

        assertThat(novel.getString("author")).isEqualTo("Autor")
        assertThat(novel.getInt("totalChapters")).isEqualTo(42)
        assertThat(novel.getBoolean("autoUpdate")).isTrue()
        assertThat(novel.getLong("lastReadAt")).isEqualTo(1234L)
        assertThat(novel.getJSONObject("lastChapter").getString("fileName")).isEqualTo("chapter-1")
        assertThat(novel.getJSONObject("lastChapter").getInt("orderIndex")).isEqualTo(1)
    }

    @Test
    fun `bookmark export references its novel by title`() = runTest {
        val root = JSONObject(useCase.execute())

        assertThat(root.getJSONArray("bookmarks").getJSONObject(0).getString("novelTitle"))
            .isEqualTo("Favorite novel")
    }

    @Test
    fun `collection export lists pinned state and member titles`() = runTest {
        val root = JSONObject(useCase.execute())
        val collection = root.getJSONArray("collections").getJSONObject(0)

        assertThat(collection.getString("name")).isEqualTo("Coleção")
        assertThat(collection.getBoolean("isPinned")).isTrue()
        assertThat(collection.getJSONArray("novels").length()).isEqualTo(1)
    }

    @Test
    fun `settings export covers app reader and library preferences`() = runTest {
        val settings = JSONObject(useCase.execute()).getJSONObject("settings")

        assertThat(settings.getString("appTheme")).isEqualTo("dark")
        assertThat(settings.getString("locale")).isEqualTo("pt")
        assertThat(settings.getBoolean("dynamicColor")).isFalse()
        assertThat(settings.getJSONObject("reader").getInt("fontSize")).isEqualTo(24)
        assertThat(settings.getJSONObject("reader").getString("swipeDirection")).isEqualTo("horizontal")
        assertThat(settings.getString("appPalette")).isEqualTo("papel")
        assertThat(settings.getString("accentColor")).isEqualTo("#ff6f00")
        assertThat(settings.getString("wallpaperHome")).isEqualTo("builtin:noite")
        settings.getJSONArray("savedThemes").getJSONObject(0).let { theme ->
            assertThat(theme.getString("name")).isEqualTo("Noite")
            assertThat(theme.getString("palette")).isEqualTo("amoled")
            assertThat(theme.getString("accentColor")).isEqualTo("#7c4dff")
            assertThat(theme.getString("readerTheme")).isEqualTo("papel:dark")
            assertThat(theme.getString("readerAccentColor")).isEqualTo("#8d6e63")
        }
        assertThat(settings.getInt("wallpaperHomeBlur")).isEqualTo(18)
        assertThat(settings.getBoolean("wallpaperBehindBars")).isFalse()
        settings.getJSONObject("reader").let { reader ->
            assertThat(reader.getString("theme")).isEqualTo("papel:light")
            assertThat(reader.getString("accentColor")).isEqualTo("#8d6e63")
            assertThat(reader.getString("wallpaper")).isEqualTo("file:reader_1.jpg")
            assertThat(reader.getInt("wallpaperBlur")).isEqualTo(24)
            assertThat(reader.getInt("veil")).isEqualTo(70)
        }
        assertThat(settings.getJSONObject("library").getString("sortOrder")).isEqualTo("TITLE")
    }

    @Test
    fun `excluding novels writes an explicit empty novels array`() = runTest {
        val root = JSONObject(useCase.execute(ExportOptions(novels = false)))

        assertThat(root.getJSONArray("novels").length()).isEqualTo(0)
        assertThat(root.getJSONArray("bookmarks").length()).isEqualTo(1)
        assertThat(root.getJSONArray("characters").length()).isEqualTo(1)
    }

    @Test
    fun `excluding bookmarks writes an explicit empty bookmarks array`() = runTest {
        val root = JSONObject(useCase.execute(ExportOptions(bookmarks = false)))

        assertThat(root.getJSONArray("novels").length()).isEqualTo(1)
        assertThat(root.getJSONArray("bookmarks").length()).isEqualTo(0)
        assertThat(root.getJSONArray("characters").length()).isEqualTo(1)
    }

    @Test
    fun `excluding characters writes an explicit empty characters array`() = runTest {
        val root = JSONObject(useCase.execute(ExportOptions(characters = false)))

        assertThat(root.getJSONArray("novels").length()).isEqualTo(1)
        assertThat(root.getJSONArray("bookmarks").length()).isEqualTo(1)
        assertThat(root.getJSONArray("characters").length()).isEqualTo(0)
    }

    @Test
    fun `excluding collections and settings writes empty sections`() = runTest {
        val root = JSONObject(useCase.execute(ExportOptions(collections = false, settings = false)))

        assertThat(root.getJSONArray("collections").length()).isEqualTo(0)
        assertThat(root.getJSONObject("settings").length()).isEqualTo(0)
    }
}
