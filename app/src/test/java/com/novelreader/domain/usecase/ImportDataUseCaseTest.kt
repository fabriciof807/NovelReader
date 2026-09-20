package com.novelreader.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.LibraryPreferences
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.local.preferences.ReaderPreferences
import com.novelreader.data.storage.PendingRestoreStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportDataUseCaseTest {
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private val uri: Uri = mockk()
    private val webImportUseCase: WebImportUseCase = mockk()
    private val backgroundImportManager: BackgroundImportManager = mockk(relaxed = true)
    private val restoreApplier: PendingRestoreApplier = mockk()
    private val novelDao: com.novelreader.data.local.db.dao.NovelDao = mockk()
    private val folderDao: com.novelreader.data.local.db.dao.FolderDao = mockk()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val wallpaperStorage: com.novelreader.data.storage.WallpaperStorage =
        mockk(relaxed = true)
    private lateinit var pendingRestoreStore: PendingRestoreStore

    private lateinit var useCase: ImportDataUseCase

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        coEvery {
            webImportUseCase.fetchChapterList("https://example.com/novel")
        } returns Result.success(
            FetchResult(
                chapters = listOf(ChapterLink("Chapter 1", "https://example.com/chapter-1", 1)),
                novelTitle = "Fetched Novel"
            )
        )
        coEvery { novelDao.getNovelByTitleIgnoreCase(any()) } returns null
        every { folderDao.getAll() } returns flowOf(emptyList())
        coEvery { folderDao.insert(any()) } returns 1L
        coEvery { restoreApplier.applyPending() } returns AppliedCounts()
        pendingRestoreStore = PendingRestoreStore(appContext, Dispatchers.Unconfined)
        useCase = ImportDataUseCase(
            context = context,
            webImportUseCase = webImportUseCase,
            backgroundImportManager = backgroundImportManager,
            pendingRestoreStore = pendingRestoreStore,
            restoreApplier = restoreApplier,
            novelDao = novelDao,
            folderDao = folderDao,
            appPreferences = AppPreferences(appContext),
            readerPreferences = ReaderPreferences(appContext),
            libraryPreferences = LibraryPreferences(appContext),
            wallpaperStorage = wallpaperStorage,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    // The preferences DataStore delegate caches the files dir of the first context it sees, so values
    // written here stay visible to other test classes that assert the defaults.
    @After
    fun resetSharedPreferences() {
        runBlocking {
            ReaderPreferences(appContext).apply {
                updateTheme("auto")
                updateFontFamily("serif")
                updateFontSize(20)
                updateLineHeight(1.8f)
                updateAccentColor(null)
                updateWallpaper(PreferenceAllowlists.WALLPAPER_NONE)
                updateWallpaperBlur(0)
                updateVeil(PreferenceAllowlists.DEFAULT_VEIL)
                updateAutoScrollSpeed(0f)
                updateKeepScreenOn(true)
                updateSwipeDirection("vertical")
            }
            AppPreferences(appContext).apply {
                updateAppTheme("system")
                updateAppPalette(PreferenceAllowlists.PALETTE_DYNAMIC)
                updateAccentColor(null)
                updateHomeWallpaper(PreferenceAllowlists.WALLPAPER_NONE)
                updateHomeWallpaperBlur(0)
                updateWallpaperBehindBars(true)
                updateLocale("pt")
                updateDynamicColorEnabled(true)
            }
            LibraryPreferences(appContext).apply {
                updateSortOrder("LAST_READ")
                updateChapterSortOrder("ASCENDING")
                updateViewMode("GRID")
            }
        }
    }

    @Test
    fun `execute forwards true favorite metadata`() = runTest {
        val result = execute(
            """
            {
              "novels": [{"title":"Backup Novel","sourceUrl":"https://example.com/novel","isFavorite":true}],
              "bookmarks": [],
              "characters": []
            }
            """.trimIndent()
        )

        assertThat(result.novelsFailed).isEmpty()
        assertThat(result.novelsQueued).containsExactly("Fetched Novel")

        coVerify {
            backgroundImportManager.startImport(
                novelTitle = "Fetched Novel",
                links = any(),
                coverUrl = null,
                sourceUrl = "https://example.com/novel",
                isFavorite = true
            )
        }
    }

    @Test
    fun `execute forwards false favorite metadata`() = runTest {
        execute("""{"novels":[{"title":"B","sourceUrl":"https://example.com/novel","isFavorite":false}]}""")

        coVerify {
            backgroundImportManager.startImport(
                novelTitle = any(),
                links = any(),
                coverUrl = any(),
                sourceUrl = any(),
                isFavorite = false
            )
        }
    }

    @Test
    fun `execute forwards null favorite metadata when importing a v1 backup`() = runTest {
        execute("""{"novels":[{"title":"B","sourceUrl":"https://example.com/novel"}]}""")

        coVerify {
            backgroundImportManager.startImport(
                novelTitle = any(),
                links = any(),
                coverUrl = any(),
                sourceUrl = any(),
                isFavorite = null
            )
        }
    }

    @Test
    fun `local novels with blank sourceUrl are reported as not restorable`() = runTest {
        val result = execute(
            """
            {
              "novels": [
                {"title":"Local Novel","sourceUrl":""},
                {"title":"Backup Novel","sourceUrl":"https://example.com/novel"}
              ]
            }
            """.trimIndent()
        )

        assertThat(result.novelsLocal).containsExactly("Local Novel")
        assertThat(result.novelsQueued).containsExactly("Fetched Novel")
    }

    @Test
    fun `settings section updates preferences and reports applied`() = runTest {
        val prefs = AppPreferences(appContext)
        val readerPrefs = ReaderPreferences(appContext)

        val result = execute(
            """
            {
              "novels": [],
              "bookmarks": [],
              "characters": [],
              "collections": [],
              "settings": {
                "appTheme": "dark",
                "locale": "en",
                "dynamicColor": false,
                "reader": {"fontSize": 24, "theme": "sepia", "swipeDirection": "horizontal"},
                "library": {"sortOrder": "TITLE", "viewMode": "LIST"}
              }
            }
            """.trimIndent()
        )

        assertThat(result.settingsApplied).isTrue()
        assertThat(prefs.appTheme.first()).isEqualTo("dark")
        assertThat(prefs.locale.first()).isEqualTo("en")
        assertThat(prefs.dynamicColorEnabled.first()).isFalse()
        readerPrefs.config.first().let {
            assertThat(it.fontSize).isEqualTo(24)
            assertThat(it.theme).isEqualTo("papel:light")
            assertThat(it.swipeDirection).isEqualTo("horizontal")
        }
        LibraryPreferences(appContext).sortOrder.first().let { assertThat(it).isEqualTo("TITLE") }
    }

    @Test
    fun `settings section applies the visual customization keys`() = runTest {
        val prefs = AppPreferences(appContext)
        val readerPrefs = ReaderPreferences(appContext)
        coEvery { wallpaperStorage.exists("file:home_5.jpg") } returns true

        val result = execute(
            """
            {
              "novels": [], "bookmarks": [], "characters": [], "collections": [],
              "settings": {
                "appPalette": "floresta",
                "accentColor": "#2e7d32",
                "wallpaperHome": "file:home_5.jpg",
                "wallpaperHomeBlur": 22,
                "wallpaperBehindBars": false,
                "reader": {
                  "theme": "papel:dark",
                  "accentColor": "#8d6e63",
                  "wallpaper": "builtin:aurora",
                  "wallpaperBlur": 12,
                  "veil": 65
                }
              }
            }
            """.trimIndent()
        )

        assertThat(result.settingsApplied).isTrue()
        assertThat(prefs.appPalette.first()).isEqualTo("floresta")
        assertThat(prefs.accentColor.first()).isEqualTo("#2e7d32")
        assertThat(prefs.homeWallpaper.first()).isEqualTo("file:home_5.jpg")
        assertThat(prefs.homeWallpaperBlur.first()).isEqualTo(22)
        assertThat(prefs.wallpaperBehindBars.first()).isFalse()
        readerPrefs.config.first().let { config ->
            assertThat(config.theme).isEqualTo("papel:dark")
            assertThat(config.accentColor).isEqualTo("#8d6e63")
            assertThat(config.wallpaper).isEqualTo("builtin:aurora")
            assertThat(config.wallpaperBlur).isEqualTo(12)
            assertThat(config.veil).isEqualTo(65)
        }
    }

    @Test
    fun `saved themes travel in the backup and come back sanitized`() = runTest {
        val prefs = AppPreferences(appContext)

        execute(
            """
            {
              "novels": [], "bookmarks": [], "characters": [], "collections": [],
              "settings": {
                "savedThemes": [
                  {"name": "Noite", "palette": "amoled", "accentColor": "#7c4dff",
                   "readerTheme": "papel:dark", "readerAccentColor": "#8d6e63"},
                  {"name": "  ", "palette": "papel"},
                  {"name": "Hostil", "palette": "papel;}body{}", "accentColor": "red",
                   "readerTheme": "x:y", "readerAccentColor": "url(javascript:1)"}
                ]
              }
            }
            """.trimIndent()
        )

        val themes = prefs.savedThemes.first()
        assertThat(themes.map { it.name }).containsExactly("Noite", "Hostil").inOrder()
        assertThat(themes.first().palette).isEqualTo("amoled")
        assertThat(themes.first().readerTheme).isEqualTo("papel:dark")
        assertThat(themes.last().palette).isEqualTo("indigo")
        assertThat(themes.last().accentColor).isNull()
        assertThat(themes.last().readerTheme).isEqualTo("auto")
    }

    @Test
    fun `a wallpaper file missing on this device falls back to none`() = runTest {
        val prefs = AppPreferences(appContext)
        val readerPrefs = ReaderPreferences(appContext)
        coEvery { wallpaperStorage.exists("file:home_gone.jpg") } returns false
        coEvery { wallpaperStorage.exists("file:reader_gone.jpg") } returns false

        execute(
            """
            {
              "novels": [], "bookmarks": [], "characters": [], "collections": [],
              "settings": {
                "wallpaperHome": "file:home_gone.jpg",
                "reader": {"wallpaper": "file:reader_gone.jpg"}
              }
            }
            """.trimIndent()
        )

        assertThat(prefs.homeWallpaper.first()).isEqualTo("none")
        assertThat(readerPrefs.config.first().wallpaper).isEqualTo("none")
    }

    @Test
    fun `a hostile wallpaper reference is dropped without touching the device`() = runTest {
        val prefs = AppPreferences(appContext)
        val readerPrefs = ReaderPreferences(appContext)

        execute(
            """
            {
              "novels": [], "bookmarks": [], "characters": [], "collections": [],
              "settings": {
                "wallpaperHome": "file:../../shared_prefs/app_prefs.xml",
                "reader": {"wallpaper": "/data/data/com.other/files/x.jpg"}
              }
            }
            """.trimIndent()
        )

        assertThat(prefs.homeWallpaper.first()).isEqualTo("none")
        assertThat(readerPrefs.config.first().wallpaper).isEqualTo("none")
        coVerify(exactly = 0) { wallpaperStorage.exists(any()) }
    }

    @Test
    fun `a hostile accent color is dropped and the palette default is kept`() = runTest {
        val prefs = AppPreferences(appContext)
        val readerPrefs = ReaderPreferences(appContext)

        execute(
            """
            {
              "novels": [], "bookmarks": [], "characters": [], "collections": [],
              "settings": {
                "appPalette": "floresta",
                "accentColor": "#fff;} body { display: none }",
                "reader": {"accentColor": "url(javascript:alert(1))"}
              }
            }
            """.trimIndent()
        )

        assertThat(prefs.appPalette.first()).isEqualTo("floresta")
        assertThat(prefs.accentColor.first()).isNull()
        assertThat(readerPrefs.config.first().accentColor).isNull()
    }

    @Test
    fun `private sourceUrl from a backup is not fetched`() = runTest {
        val result = execute(
            """{"novels":[{"title":"Evil","sourceUrl":"https://127.0.0.1/novel"}]}"""
        )

        assertThat(result.novelsFailed).containsExactly("Evil")
        assertThat(result.novelsQueued).isEmpty()
        coVerify(exactly = 0) { webImportUseCase.fetchChapterList(any()) }
    }

    @Test
    fun `settings section rejects a non-allowlisted font family`() = runTest {
        val readerPrefs = ReaderPreferences(appContext)
        val payload = "serif;} </style><script>alert(1)</script><style>a{"

        execute(
            """
            {
              "novels": [],
              "settings": {"reader": {"fontFamily": ${org.json.JSONObject.quote(payload)}}}
            }
            """.trimIndent()
        )

        assertThat(readerPrefs.config.first().fontFamily).isEqualTo("serif")
    }

    @Test
    fun `settings section applies an allowlisted font family`() = runTest {
        val readerPrefs = ReaderPreferences(appContext)

        execute(
            """
            {
              "novels": [],
              "settings": {"reader": {"fontFamily": "monospace"}}
            }
            """.trimIndent()
        )

        assertThat(readerPrefs.config.first().fontFamily).isEqualTo("monospace")
    }

    @Test
    fun `settings section applies a fixed reader brightness`() = runTest {
        val readerPrefs = ReaderPreferences(appContext)

        execute(
            """
            {
              "novels": [],
              "settings": {"reader": {"brightness": 35}}
            }
            """.trimIndent()
        )

        assertThat(readerPrefs.config.first().brightness).isEqualTo(35)
    }

    @Test
    fun `settings section rejects a brightness no display can show`() = runTest {
        val readerPrefs = ReaderPreferences(appContext)

        execute(
            """
            {
              "novels": [],
              "settings": {"reader": {"brightness": 0}}
            }
            """.trimIndent()
        )

        assertThat(readerPrefs.config.first().brightness)
            .isEqualTo(PreferenceAllowlists.BRIGHTNESS_SYSTEM)
    }

    @Test
    fun `backup without settings section reports settings not applied`() = runTest {
        val result = execute("""{"novels":[],"bookmarks":[],"characters":[]}""")

        assertThat(result.settingsApplied).isFalse()
    }

    @Test
    fun `entries for queued novels go to pending store under final title`() = runTest {
        val result = execute(
            """
            {
              "novels": [{"title":"Backup Novel","sourceUrl":"https://example.com/novel","autoUpdate":true,"lastReadAt":555}],
              "bookmarks": [
                {"novelTitle":"Backup Novel","title":"Mark","note":"n","page":2,"scrollPosition":9,
                 "createdAt":10,"chapter":{"fileName":"ch1.html","orderIndex":0}}
              ],
              "characters": [
                {"novelTitle":"Backup Novel","name":"Hero","notes":"","isFavorite":true,
                 "photoPath":"/x/p.jpg","createdAt":11,"photos":[{"photoPath":"/x/p.jpg","orderIndex":0}]}
              ],
              "collections": [
                {"name":"Coleção","isPinned":true,"novels":["Backup Novel"]}
              ]
            }
            """.trimIndent()
        )

        val pending = pendingRestoreStore.load()

        assertThat(pending.bookmarks.single().novelTitle).isEqualTo("Fetched Novel")
        assertThat(pending.bookmarks.single().chapterFileName).isEqualTo("ch1.html")
        assertThat(pending.characters.single().novelTitle).isEqualTo("Fetched Novel")
        assertThat(pending.collectionLinks.single()).isEqualTo(
            com.novelreader.data.storage.PendingCollectionLink("Coleção", "Fetched Novel")
        )
        assertThat(pending.novels.single().title).isEqualTo("Fetched Novel")
        assertThat(pending.novels.single().lastReadAt).isEqualTo(555L)

        coVerify { folderDao.insert(any()) }

        assertThat(result.bookmarksPending).isEqualTo(1)
        assertThat(result.charactersPending).isEqualTo(1)
        assertThat(result.collectionLinksPending).isEqualTo(1)
        assertThat(result.settingsApplied).isFalse()
    }

    @Test
    fun `entries whose novel is absent and not queued are skipped`() = runTest {
        val result = execute(
            """
            {
              "novels": [],
              "bookmarks": [
                {"novelTitle":"Ghost Novel","title":"Mark","page":0,"scrollPosition":0,
                 "createdAt":0,"chapter":{"fileName":"a.html","orderIndex":0}}
              ],
              "characters": []
            }
            """.trimIndent()
        )

        assertThat(pendingRestoreStore.load().isEmpty()).isTrue()
        assertThat(result.bookmarksPending).isEqualTo(0)
    }

    @Test
    fun `v2 bookmarks without novel reference are ignored`() = runTest {
        val result = execute(
            """
            {
              "novels": [],
              "bookmarks": [
                {"title":"Orphan","page":0,"scrollPosition":0,"createdAt":0,
                 "chapter":{"fileName":"a.html","orderIndex":0}}
              ],
              "characters": []
            }
            """.trimIndent()
        )

        assertThat(pendingRestoreStore.load().isEmpty()).isTrue()
        assertThat(result.bookmarksPending).isEqualTo(0)
    }

    @Test
    fun `applied counts from the applier land in the result`() = runTest {
        coEvery { restoreApplier.applyPending() } returns AppliedCounts(bookmarks = 2, characters = 1)
        coEvery {
            novelDao.getNovelByTitleIgnoreCase("Existing")
        } returns com.novelreader.data.local.db.entity.NovelEntity(id = 7, title = "Existing")

        val result = execute(
            """
            {
              "novels": [],
              "bookmarks": [
                {"novelTitle":"Existing","title":"A","page":0,"scrollPosition":0,"createdAt":0,
                 "chapter":{"fileName":"a.html","orderIndex":0}},
                {"novelTitle":"Existing","title":"B","page":0,"scrollPosition":0,"createdAt":0,
                 "chapter":{"fileName":"b.html","orderIndex":1}},
                {"novelTitle":"Existing","title":"C","page":0,"scrollPosition":0,"createdAt":0,
                 "chapter":{"fileName":"c.html","orderIndex":2}}
              ],
              "characters": [
                {"novelTitle":"Existing","name":"Hero","createdAt":0}
              ]
            }
            """.trimIndent()
        )

        assertThat(result.bookmarksRestored).isEqualTo(2)
        assertThat(result.bookmarksPending).isEqualTo(1)
        assertThat(result.charactersRestored).isEqualTo(1)
        assertThat(result.charactersPending).isEqualTo(0)
    }

    private suspend fun execute(json: String): ImportResult {
        every { contentResolver.openInputStream(uri) } returns
            ByteArrayInputStream(json.toByteArray())
        return useCase.execute(uri)
    }
}
