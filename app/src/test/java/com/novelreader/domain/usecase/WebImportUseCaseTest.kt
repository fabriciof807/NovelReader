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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        val parserRegistry = ParserRegistry(
            parsers = emptySet(),
            fallbackParser = GenericFallbackParser(),
            mhtParser = MhtParser()
        )
        val httpClient = HttpClient(InMemoryCloudflareCookieStore())
        val coverDownloader = CoverDownloader(novelDao, httpClient)
        val novelImporter = NovelImporter(novelDao, chapterDao, ChapterOrderNormalizer(chapterDao), database.novelSourceDao())
        chapterFetcher = mockk(relaxed = true)

        useCase = WebImportUseCase(
            context = context,
            chapterCrawler = ChapterCrawler(httpClient, emptySet<NovelListAugmenter>()),
            chapterFetcher = chapterFetcher,
            coverDownloader = coverDownloader,
            novelImporter = novelImporter,
            failedChapterDao = failedChapterDao,
            novelDao = novelDao,
            chapterDao = chapterDao,
            io = Dispatchers.Unconfined
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

        coEvery { chapterFetcher.fetch(any(), any(), any()) } throws SecurityException("Apenas HTTPS permitido")

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
        coEvery { chapterFetcher.fetch("https://example.com/ch1.html", "ch1", "Chapter 1") } returns FetchedChapter(
            title = "",
            content = "",
            fileName = "ch1"
        )

        val result = useCase.importChapters(
            novelTitle = "Empty Content Novel",
            links = listOf(link)
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
    fun importChapters_rateLimitedException_insertsFailedChapterWithRetryHint() = runBlocking {
        val link = ChapterLink(title = "Chapter 1", url = "https://example.com/ch1.html", chapterNumber = 1)
        coEvery { chapterFetcher.fetch("https://example.com/ch1.html", "ch1", "Chapter 1") } throws
            RateLimitedException(url = "https://example.com/ch1.html", attempts = 5, lastStatusCode = 429)

        val result = useCase.importChapters(
            novelTitle = "Rate Limited Novel",
            links = listOf(link)
        )

        assertThat(result.isSuccess).isTrue()
        val novel = novelDao.getNovelByTitle("Rate Limited Novel")!!
        val chapters = chapterDao.getChaptersByNovelSync(novel.id)
        assertThat(chapters).isEmpty()
        val failed = failedChapterDao.getByNovel(novel.id)
        assertThat(failed).hasSize(1)
        assertThat(failed[0].fileName).isEqualTo("ch1")
        assertThat(failed[0].errorType).isEqualTo(FailedChapterErrorType.NETWORK)
        assertThat(failed[0].errorMessage.lowercase()).contains("rate")
    }

    @Test
    fun importChapters_progressReflectsValidContentOnly() = runBlocking {
        val good = ChapterLink(title = "Chapter 1", url = "https://example.com/ch1.html", chapterNumber = 1)
        val empty = ChapterLink(title = "Chapter 2", url = "https://example.com/ch2.html", chapterNumber = 2)
        val rateLimited = ChapterLink(title = "Chapter 3", url = "https://example.com/ch3.html", chapterNumber = 3)

        coEvery { chapterFetcher.fetch("https://example.com/ch1.html", "ch1", "Chapter 1") } returns FetchedChapter(
            title = "Chapter 1",
            content = "<p>${"Real content paragraph with enough text. ".repeat(20)}</p>",
            fileName = "ch1"
        )
        coEvery { chapterFetcher.fetch("https://example.com/ch2.html", "ch2", "Chapter 2") } returns FetchedChapter(
            title = "",
            content = "",
            fileName = "ch2"
        )
        coEvery { chapterFetcher.fetch("https://example.com/ch3.html", "ch3", "Chapter 3") } throws
            RateLimitedException(url = "https://example.com/ch3.html", attempts = 5, lastStatusCode = 429)

        val progressCalls = mutableListOf<Pair<Int, Int>>()
        useCase.importChapters(
            novelTitle = "Mixed Novel",
            links = listOf(good, empty, rateLimited),
            onProgress = { p, t -> progressCalls.add(p to t) }
        )

        val finalProgress = progressCalls.last()
        assertThat(finalProgress.first).isEqualTo(1)
        assertThat(finalProgress.second).isEqualTo(3)

        val novel = novelDao.getNovelByTitle("Mixed Novel")!!
        val chapters = chapterDao.getChaptersByNovelSync(novel.id)
        assertThat(chapters).hasSize(1)
        val failed = failedChapterDao.getByNovel(novel.id)
        assertThat(failed).hasSize(2)
    }
}
