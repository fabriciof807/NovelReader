package com.novelreader.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.dao.NovelSourceDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterErrorType
import com.novelreader.data.parser.GenericFallbackParser
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.domain.usecase.webimport.ChapterCrawler
import com.novelreader.domain.usecase.webimport.ChapterFetcher
import com.novelreader.domain.usecase.webimport.CoverDownloader
import com.novelreader.domain.usecase.webimport.FetchedChapter
import com.novelreader.domain.usecase.webimport.HttpClient
import com.novelreader.domain.usecase.webimport.NovelListAugmenter
import com.novelreader.domain.usecase.webimport.InMemoryCloudflareCookieStore
import com.novelreader.domain.usecase.webimport.NovelImporter
import com.novelreader.domain.usecase.webimport.RateLimitedException
import com.novelreader.util.StringUtils
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebImportUseCaseTest {

    private lateinit var database: NovelDatabase
    private lateinit var novelDao: NovelDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var failedChapterDao: FailedChapterDao
    private lateinit var useCase: WebImportUseCase
    private lateinit var chapterFetcher: ChapterFetcher

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        novelDao = database.novelDao()
        chapterDao = database.chapterDao()
        failedChapterDao = database.failedChapterDao()
        chapterFetcher = mockk(relaxed = true)
        useCase = buildUseCase(Dispatchers.Unconfined)
    }

    private fun buildUseCase(io: CoroutineDispatcher): WebImportUseCase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parserRegistry = ParserRegistry(
            parsers = emptySet(),
            fallbackParser = GenericFallbackParser(),
            mhtParser = MhtParser()
        )
        val httpClient = HttpClient(InMemoryCloudflareCookieStore())
        return WebImportUseCase(
            context = context,
            chapterCrawler = ChapterCrawler(httpClient, emptySet<NovelListAugmenter>()),
            chapterFetcher = chapterFetcher,
            coverDownloader = CoverDownloader(novelDao, httpClient),
            novelImporter = NovelImporter(novelDao, chapterDao, ChapterOrderNormalizer(chapterDao), database.novelSourceDao()),
            failedChapterDao = failedChapterDao,
            novelDao = novelDao,
            chapterDao = chapterDao,
            io = io
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fetchChapterList_rejectsHttpUrl() = runBlocking {
        val result = useCase.fetchChapterList("http://example.com/novel")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun importChapters_handlesHttpLinksWithOnError() = runBlocking {
        val link = ChapterLink(title = "Chapter 1", url = "http://example.com/ch1.html", chapterNumber = 1)

        coEvery { chapterFetcher.fetch(any(), any(), any(), any()) } throws SecurityException("Apenas HTTPS permitido")

        var errorMessage = ""
        val result = useCase.importChapters(
            novelTitle = "Test Novel",
            links = listOf(link),
            onError = { _, msg -> errorMessage = msg }
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(errorMessage).contains("HTTPS")
        val novel = novelDao.getNovelByTitle("Test Novel")
        assertThat(novel).isNotNull()
    }

    @Test
    fun importChapters_passesTheSourceUrlHostToTheFetcher() = runBlocking {
        val link = ChapterLink(title = "Ch 1", url = "https://mirror.example.com/ch1.html", chapterNumber = 1)
        coEvery {
            chapterFetcher.fetch("https://mirror.example.com/ch1.html", "ch1", "Ch 1", "example.com")
        } returns FetchedChapter(title = "Ch 1", content = "<p>${"Real content. ".repeat(30)}</p>", fileName = "ch1")

        useCase.importChapters(
            novelTitle = "Host Pinned Novel",
            links = listOf(link),
            sourceUrl = "https://example.com/novel"
        )

        io.mockk.coVerify {
            chapterFetcher.fetch("https://mirror.example.com/ch1.html", "ch1", "Ch 1", "example.com")
        }
    }

    @Test
    fun importChapters_emptyLinks_returnsSuccess() = runBlocking {
        val result = useCase.importChapters(
            novelTitle = "Empty Novel",
            links = emptyList()
        )
        assertThat(result.isSuccess).isTrue()
        val novel = novelDao.getNovelByTitle("Empty Novel")
        assertThat(novel).isNotNull()
    }

    @Test
    fun looksLikeStaleContent_detects404Footer() {
        val footer = """
            <h1>404 Page not found</h1>
            <p>Novel list Your Library Latest Novels Latest Release Most Popular Completed Novels</p>
            <p>Genres Action Adult Adventure Comedy Drama</p>
            <p>Welcome to Freewebnovel</p>
        """.trimIndent()
        assertThat(looksLikeStaleContent(footer)).isTrue()
    }

    @Test
    fun looksLikeStaleContent_acceptsRealContent() {
        val real = "<p>This is a real chapter with plenty of actual text content that should not be considered stale.</p>".repeat(20)
        assertThat(looksLikeStaleContent(real)).isFalse()
    }

    @Test
    fun looksLikeStaleContent_detectsShortContent() {
        assertThat(looksLikeStaleContent("too short")).isTrue()
    }

    @Test
    fun importChapters_emptyContent_insertsFailedChapterAndDoesNotPersistEmpty() = runBlocking {
        val link = ChapterLink(title = "Chapter 1", url = "https://example.com/ch1.html", chapterNumber = 1)
        coEvery { chapterFetcher.fetch("https://example.com/ch1.html", "ch1", "Chapter 1", "example.com") } returns FetchedChapter(
            title = "",
            content = "",
            fileName = "ch1"
        )

        val result = useCase.importChapters(
            novelTitle = "Empty Content Novel",
            links = listOf(link),
            sourceUrl = "https://example.com/novel"
        )

        assertThat(result.isSuccess).isTrue()
        val novel = novelDao.getNovelByTitle("Empty Content Novel")!!
        val chapters = chapterDao.getChaptersByNovelSync(novel.id)
        assertThat(chapters).isEmpty()
        val failed = failedChapterDao.getByNovel(novel.id)
        assertThat(failed).hasSize(1)
        assertThat(failed[0].fileName).isEqualTo("ch1")
        assertThat(failed[0].errorType).isEqualTo(FailedChapterErrorType.EMPTY_CONTENT)
    }

    @Test
    fun importChapters_reFetchEmptyContent_deletesOldRow() = runBlocking {
        val novelId = novelDao.insert(com.novelreader.data.local.db.entity.NovelEntity(
            title = "Reimport Empty Novel"
        ))
        chapterDao.insertAll(listOf(
            ChapterEntity(
                id = 0, novelId = novelId, title = "Chapter 1", fileName = "ch1",
                orderIndex = 0, content = ""
            )
        ))
        novelDao.updateLastChapterId(novelId, 1L)
        novelDao.updateChapterCount(novelId, 1)

        val link = ChapterLink(title = "Chapter 1", url = "https://example.com/ch1.html", chapterNumber = 1)
        coEvery { chapterFetcher.fetch("https://example.com/ch1.html", "ch1", "Chapter 1", "example.com") } returns FetchedChapter(
            title = "", content = "", fileName = "ch1"
        )

        val result = useCase.importChapters(
            novelTitle = "Reimport Empty Novel",
            links = listOf(link),
            sourceUrl = "https://example.com/novel"
        )

        assertThat(result.isSuccess).isTrue()
        val chapters = chapterDao.getChaptersByNovelSync(novelId)
        assertThat(chapters).isEmpty()
        val novel = novelDao.getNovelById(novelId)!!
        assertThat(novel.lastChapterId).isNull()
        assertThat(novel.totalChapters).isEqualTo(0)
        val failed = failedChapterDao.getByNovel(novelId)
        assertThat(failed).hasSize(1)
        assertThat(failed[0].fileName).isEqualTo("ch1")
        assertThat(failed[0].errorType).isEqualTo(FailedChapterErrorType.EMPTY_CONTENT)
    }

    @Test
    fun importChapters_catchAlsoDeletesOldRow() = runBlocking {
        val novelId = novelDao.insert(com.novelreader.data.local.db.entity.NovelEntity(
            title = "Catch Delete Novel"
        ))
        chapterDao.insertAll(listOf(
            ChapterEntity(
                id = 0, novelId = novelId, title = "Ch 1", fileName = "ch1",
                orderIndex = 0, content = ""
            )
        ))
        novelDao.updateLastChapterId(novelId, 1L)
        novelDao.updateChapterCount(novelId, 1)

        val link = ChapterLink(title = "Ch 1", url = "https://example.com/ch1.html", chapterNumber = 1)
        coEvery { chapterFetcher.fetch("https://example.com/ch1.html", "ch1", "Ch 1", "example.com") } throws
            RateLimitedException(url = "https://example.com/ch1.html", attempts = 5, lastStatusCode = 429)

        val result = useCase.importChapters(
            novelTitle = "Catch Delete Novel",
            links = listOf(link),
            sourceUrl = "https://example.com/novel"
        )

        assertThat(result.isSuccess).isTrue()
        val chapters = chapterDao.getChaptersByNovelSync(novelId)
        assertThat(chapters).isEmpty()
        val novel = novelDao.getNovelById(novelId)!!
        assertThat(novel.lastChapterId).isNull()
        val failed = failedChapterDao.getByNovel(novelId)
        assertThat(failed).hasSize(1)
        assertThat(failed[0].errorType).isEqualTo(FailedChapterErrorType.NETWORK)
    }

    @Test
    fun importChapters_emptyContent_crashBetweenInsertAndDelete_doesNotLoseOldRow() = runBlocking {
        val novelId = novelDao.insert(com.novelreader.data.local.db.entity.NovelEntity(
            title = "Crash Novel"
        ))
        chapterDao.insertAll(listOf(
            ChapterEntity(
                id = 0, novelId = novelId, title = "Ch 1", fileName = "ch1",
                orderIndex = 0, content = ""
            )
        ))
        novelDao.updateChapterCount(novelId, 1)

        val link = ChapterLink(title = "Ch 1", url = "https://example.com/ch1.html", chapterNumber = 1)
        coEvery { chapterFetcher.fetch("https://example.com/ch1.html", "ch1", "Ch 1", "example.com") } returns FetchedChapter(
            title = "", content = "", fileName = "ch1"
        )

        val failingDao = io.mockk.mockk<FailedChapterDao>(relaxed = true)
        coEvery { failingDao.deleteByNovelAndFileName(any(), any()) } returns Unit
        coEvery { failingDao.insert(any()) } throws RuntimeException("Simulated crash")
        coEvery { failingDao.getByNovel(any()) } returns emptyList()

        val useCaseWithFailingDao = WebImportUseCase(
            context = ApplicationProvider.getApplicationContext<Context>(),
            chapterCrawler = com.novelreader.domain.usecase.webimport.ChapterCrawler(
                HttpClient(com.novelreader.domain.usecase.webimport.InMemoryCloudflareCookieStore()),
                emptySet<com.novelreader.domain.usecase.webimport.NovelListAugmenter>()
            ),
            chapterFetcher = chapterFetcher,
            coverDownloader = com.novelreader.domain.usecase.webimport.CoverDownloader(novelDao, HttpClient(com.novelreader.domain.usecase.webimport.InMemoryCloudflareCookieStore())),
            novelImporter = com.novelreader.domain.usecase.webimport.NovelImporter(novelDao, chapterDao, com.novelreader.domain.usecase.ChapterOrderNormalizer(chapterDao), database.novelSourceDao()),
            failedChapterDao = failingDao,
            novelDao = novelDao,
            chapterDao = chapterDao,
            io = Dispatchers.Unconfined
        )

        val result = useCaseWithFailingDao.importChapters(
            novelTitle = "Crash Novel",
            links = listOf(link),
            sourceUrl = "https://example.com/novel"
        )

        assertThat(result.isSuccess).isFalse()
        val chapters = chapterDao.getChaptersByNovelSync(novelId)
        assertThat(chapters).hasSize(1)
        val novel = novelDao.getNovelById(novelId)!!
        assertThat(novel.totalChapters).isEqualTo(1)
    }

    @Test
    fun importChapters_emptyContent_totalChaptersIsConsistent() = runBlocking {
        val links = listOf(
            ChapterLink(title = "Ch 1", url = "https://example.com/ch1.html", chapterNumber = 1),
            ChapterLink(title = "Ch 2", url = "https://example.com/ch2.html", chapterNumber = 2),
            ChapterLink(title = "Ch 3", url = "https://example.com/ch3.html", chapterNumber = 3),
            ChapterLink(title = "Ch 4", url = "https://example.com/ch4.html", chapterNumber = 4),
            ChapterLink(title = "Ch 5", url = "https://example.com/ch5.html", chapterNumber = 5)
        )

        coEvery { chapterFetcher.fetch("https://example.com/ch1.html", "ch1", "Ch 1", "example.com") } returns FetchedChapter(
            title = "Ch 1", content = "<p>${"Real content. ".repeat(30)}</p>", fileName = "ch1"
        )
        coEvery { chapterFetcher.fetch("https://example.com/ch2.html", "ch2", "Ch 2", "example.com") } returns FetchedChapter(
            title = "", content = "", fileName = "ch2"
        )
        coEvery { chapterFetcher.fetch("https://example.com/ch3.html", "ch3", "Ch 3", "example.com") } returns FetchedChapter(
            title = "Ch 3", content = "<p>${"Real content. ".repeat(30)}</p>", fileName = "ch3"
        )
        coEvery { chapterFetcher.fetch("https://example.com/ch4.html", "ch4", "Ch 4", "example.com") } returns FetchedChapter(
            title = "", content = "", fileName = "ch4"
        )
        coEvery { chapterFetcher.fetch("https://example.com/ch5.html", "ch5", "Ch 5", "example.com") } returns FetchedChapter(
            title = "", content = "", fileName = "ch5"
        )

        useCase.importChapters(
            novelTitle = "Total Consistent Novel",
            links = links,
            sourceUrl = "https://example.com/novel"
        )

        val novel = novelDao.getNovelByTitle("Total Consistent Novel")!!
        assertThat(novel.totalChapters).isEqualTo(2)
    }

    @Test
    fun importChapters_flushesEvery20ChaptersBeforeTheBatchEnds() = runTest {
        val links = (1..25).map {
            ChapterLink(title = "Chapter $it", url = "https://example.com/ch$it.html", chapterNumber = it)
        }
        val chaptersInDbAtFetch = mutableListOf<Int>()
        val novelTotalAtFetch = mutableListOf<Int>()
        coEvery { chapterFetcher.fetch(any(), any(), any(), any()) } coAnswers {
            val url = invocation.args[0] as String
            val novel = novelDao.getNovelByTitle("Incremental Novel")
            chaptersInDbAtFetch += novel?.let { chapterDao.getChaptersByNovelSync(it.id).size } ?: 0
            novelTotalAtFetch += novel?.totalChapters ?: -1
            FetchedChapter(
                title = "Chapter",
                content = "<p>${"Real content. ".repeat(30)}</p>",
                fileName = StringUtils.fileNameFromUrl(url, "chapter")
            )
        }

        val result = buildUseCase(UnconfinedTestDispatcher(testScheduler)).importChapters(
            novelTitle = "Incremental Novel",
            links = links,
            sourceUrl = "https://example.com/novel"
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(chaptersInDbAtFetch).hasSize(25)
        assertThat(chaptersInDbAtFetch[19]).isEqualTo(0)
        assertThat(chaptersInDbAtFetch[20]).isEqualTo(20)
        assertThat(novelTotalAtFetch[20]).isEqualTo(20)

        val novel = novelDao.getNovelByTitle("Incremental Novel")!!
        assertThat(chapterDao.getChaptersByNovelSync(novel.id)).hasSize(25)
        assertThat(novelDao.getNovelById(novel.id)!!.totalChapters).isEqualTo(25)
    }

    @Test
    fun importChapters_keepsChapterOrderAcrossIncrementalFlushes() = runTest {
        val links = (1..25).map {
            ChapterLink(title = "Chapter $it", url = "https://example.com/order$it.html", chapterNumber = it)
        }
        coEvery { chapterFetcher.fetch(any(), any(), any(), any()) } coAnswers {
            val url = invocation.args[0] as String
            FetchedChapter(
                title = "Chapter",
                content = "<p>${"Real content. ".repeat(30)}</p>",
                fileName = StringUtils.fileNameFromUrl(url, "chapter")
            )
        }

        val result = buildUseCase(UnconfinedTestDispatcher(testScheduler)).importChapters(
            novelTitle = "Ordered Novel",
            links = links,
            sourceUrl = "https://example.com/novel"
        )

        assertThat(result.isSuccess).isTrue()
        val novel = novelDao.getNovelByTitle("Ordered Novel")!!
        val chapters = chapterDao.getChaptersByNovelSync(novel.id)
        assertThat(chapters.map { it.orderIndex }).isEqualTo((0 until 25).toList())
        assertThat(chapters.map { it.fileName }).isEqualTo((1..25).map { "order$it" })
    }

    @Test
    fun importChapters_belowTheFlushThresholdPersistsEverythingAtTheEnd() = runTest {
        val links = (1..5).map {
            ChapterLink(title = "Chapter $it", url = "https://example.com/small$it.html", chapterNumber = it)
        }
        coEvery { chapterFetcher.fetch(any(), any(), any(), any()) } coAnswers {
            val url = invocation.args[0] as String
            FetchedChapter(
                title = "Chapter",
                content = "<p>${"Real content. ".repeat(30)}</p>",
                fileName = StringUtils.fileNameFromUrl(url, "chapter")
            )
        }

        val result = buildUseCase(UnconfinedTestDispatcher(testScheduler)).importChapters(
            novelTitle = "Small Novel",
            links = links,
            sourceUrl = "https://example.com/novel"
        )

        assertThat(result.isSuccess).isTrue()
        val novel = novelDao.getNovelByTitle("Small Novel")!!
        assertThat(chapterDao.getChaptersByNovelSync(novel.id)).hasSize(5)
        assertThat(novelDao.getNovelById(novel.id)!!.totalChapters).isEqualTo(5)
    }
}
