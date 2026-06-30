package com.novelreader.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.parser.GenericFallbackParser
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.domain.usecase.webimport.ChapterCrawler
import com.novelreader.domain.usecase.webimport.ChapterFetcher
import com.novelreader.domain.usecase.webimport.CoverDownloader
import com.novelreader.domain.usecase.webimport.HttpClient
import com.novelreader.domain.usecase.webimport.NovelListAugmenter
import com.novelreader.domain.usecase.webimport.InMemoryCloudflareCookieStore
import com.novelreader.domain.usecase.webimport.NovelImporter
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
        val novelImporter = NovelImporter(novelDao, chapterDao, ChapterOrderNormalizer(chapterDao))

        useCase = WebImportUseCase(
            context = context,
            chapterCrawler = ChapterCrawler(httpClient, emptySet<NovelListAugmenter>()),
            chapterFetcher = ChapterFetcher(parserRegistry, httpClient),
            coverDownloader = coverDownloader,
            novelImporter = novelImporter,
            failedChapterDao = failedChapterDao,
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
}
