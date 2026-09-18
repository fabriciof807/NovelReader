package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.parser.FreeWebNovelParser
import com.novelreader.data.parser.GenericFallbackParser
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParserRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ChapterFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var foreign: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var parserRegistry: ParserRegistry

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        foreign = MockWebServer()
        foreign.start()
        val port = server.url("").port
        val loopback = InetAddress.getByName("127.0.0.1")
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname in setOf("freewebnovel.com", "www.freewebnovel.com", "evil.example", "www.evil.example")) {
                    listOf(loopback)
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
        }
        val okClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dns(dns)
            .build()
        httpClient = HttpClient(InMemoryCloudflareCookieStore(), okClient)
        parserRegistry = ParserRegistry(
            parsers = setOf(FreeWebNovelParser()),
            fallbackParser = GenericFallbackParser(),
            mhtParser = MhtParser()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        foreign.shutdown()
    }

    @Test
    fun fetch_returnsContentFromRealChapter() = runBlocking {
        val port = server.url("").port
        val chapterHtml = """
            <html><head><title>Test Novel - Chapter 1 The Beginning | Free Web Novel</title></head>
            <body><h1 class="tit"><a href="/novel/test" title="Test Novel">Test Novel</a></h1>
            <div class="chapter-start"></div>
            <p>First paragraph of real content.</p>
            <p>Second paragraph of real content.</p>
            <div class="chapter-end"></div>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(chapterHtml))

        val fetcher = ChapterFetcher(parserRegistry, httpClient, requireHttps = false)
        val fetched = fetcher.fetch("http://www.freewebnovel.com:$port/novel/test/chapter-1", "chapter-1", "Chapter 1", "freewebnovel.com")

        assertThat(fetched.content).contains("First paragraph of real content")
        assertThat(fetched.title).isEqualTo("Chapter 1 The Beginning")
    }

    @Test
    fun fetch_doesNotReturn404BodyWhenParserSignalsNotFound() = runBlocking {
        val port = server.url("").port
        val notFoundHtml = """
            <html><head><title>Page not found | Free Web Novel</title></head>
            <body>
            <h1><div style="font-size:60px">404</div><div>Page not found</div></h1>
            <ul class="navbar"><li>Novel list</li><li>Your Library</li><li>Latest Novels</li></ul>
            <p>Welcome to Freewebnovel</p>
            <p>Privacy Policy & Terms of use</p>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(notFoundHtml))

        val fetcher = ChapterFetcher(parserRegistry, httpClient, requireHttps = false)
        val fetched = fetcher.fetch("http://www.freewebnovel.com:$port/novel/test/chapter-1", "chapter-1", "Chapter 1 The Beginning", "freewebnovel.com")

        assertThat(fetched.content).doesNotContain("Page not found")
        assertThat(fetched.content).doesNotContain("Novel list")
        assertThat(fetched.content).doesNotContain("Your Library")
    }

    @Test
    fun fetch_429sRateLimited_throwsRateLimitedAfterMaxRetries() = runBlocking {
        val port = server.url("").port
        repeat(5) {
            server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        }

        val fetcher = ChapterFetcher(parserRegistry, httpClient, requireHttps = false)
        fetcher.retryDelayFn = { _, _ -> 0L }

        val ex = runCatching {
            fetcher.fetch(
                "http://www.freewebnovel.com:$port/novel/test/chapter-1",
                "chapter-1",
                "Chapter 1",
                "freewebnovel.com"
            )
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(RateLimitedException::class.java)
        val rle = ex as RateLimitedException
        assertThat(rle.url).contains("chapter-1")
        assertThat(rle.attempts).isEqualTo(5)
        assertThat(rle.lastStatusCode).isEqualTo(429)
        assertThat(server.requestCount).isEqualTo(5)
    }

    @Test
    fun fetch_429sThenSuccess_returnsContent() = runBlocking {
        val port = server.url("").port
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        }
        val chapterHtml = """
            <html><head><title>Test Novel - Chapter 1 Real Content | Free Web Novel</title></head>
            <body><h1 class="tit"><a href="/novel/test" title="Test Novel">Test Novel</a></h1>
            <div class="chapter-start"></div>
            <p>Recovered after 429s.</p>
            <p>Another paragraph of content.</p>
            <div class="chapter-end"></div>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(chapterHtml))

        val fetcher = ChapterFetcher(parserRegistry, httpClient, requireHttps = false)
        fetcher.retryDelayFn = { _, _ -> 0L }
        val fetched = fetcher.fetch(
            "http://www.freewebnovel.com:$port/novel/test/chapter-1",
            "chapter-1",
            "Chapter 1",
            "freewebnovel.com"
        )

        assertThat(fetched.content).contains("Recovered after 429s")
        assertThat(server.requestCount).isEqualTo(4)
    }

    @Test
    fun fetch_rejectsCrossDomainRedirectBeforeContact() = runBlocking {
        val port = server.url("").port
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "http://evil.example:${foreign.port}/chapter-1")
        )

        val fetcher = ChapterFetcher(parserRegistry, httpClient, requireHttps = false)
        fetcher.maxRetries = 1

        val ex = runCatching {
            fetcher.fetch(
                "http://www.freewebnovel.com:$port/novel/test/chapter-1",
                "chapter-1",
                "Chapter 1",
                "freewebnovel.com"
            )
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(RemoteRequestRejectedException::class.java)
        assertThat(foreign.requestCount).isEqualTo(0)
    }

    @Test
    fun fetch_buildsThePolicyFromTheExpectedHostNotFromTheChapterUrl() = runBlocking {
        val port = server.url("").port
        server.enqueue(MockResponse().setResponseCode(200).setBody("must-not-be-read"))

        val fetcher = ChapterFetcher(parserRegistry, httpClient, requireHttps = false)
        fetcher.maxRetries = 1

        val ex = runCatching {
            fetcher.fetch(
                "http://www.freewebnovel.com:$port/novel/test/chapter-1",
                "chapter-1",
                "Chapter 1",
                "evil.example"
            )
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(RemoteRequestRejectedException::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }
}
