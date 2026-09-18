package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ChapterCrawlerTest {

    private lateinit var server: MockWebServer
    private lateinit var foreign: MockWebServer
    private lateinit var client: HttpClient
    private lateinit var homeUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        foreign = MockWebServer()
        foreign.start()
        val port = server.url("").port
        homeUrl = "http://readnovelfull.com:$port/sample.html"
        val loopback = InetAddress.getByName("127.0.0.1")
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname == "readnovelfull.com" || hostname == "www.readnovelfull.com") {
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
        client = HttpClient(InMemoryCloudflareCookieStore(), okClient)
    }

    @After
    fun tearDown() {
        server.shutdown()
        foreign.shutdown()
    }

    private fun crawlerForMappedHosts(
        augmenters: Set<NovelListAugmenter> = emptySet()
    ): ChapterCrawler {
        val loopback = InetAddress.getByName("127.0.0.1")
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname in setOf("readnovelfull.com", "www.readnovelfull.com", "evil.example", "www.evil.example")) {
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
        return ChapterCrawler(
            HttpClient(InMemoryCloudflareCookieStore(), okClient),
            augmenters,
            requireHttps = false
        )
    }

    private fun crawlerServingHttps(html: String, requestedUrls: MutableList<String>): ChapterCrawler {
        val okClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrls.add(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(html.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()
        return ChapterCrawler(
            HttpClient(InMemoryCloudflareCookieStore(), okClient),
            emptySet(),
            requireHttps = true
        )
    }

    @Test
    fun makeAbsolute_upgradesHttpLinksWhenTheBaseIsHttps() {
        val crawler = ChapterCrawler(client, emptySet(), requireHttps = false)

        assertThat(crawler.makeAbsolute("http://example.com/ch1", "https://example.com/novel"))
            .isEqualTo("https://example.com/ch1")
        assertThat(crawler.makeAbsolute("http://example.com/ch1", "http://example.com/novel"))
            .isEqualTo("http://example.com/ch1")
    }

    @Test
    fun makeAbsolute_resolvesRootRelativeAndRelativeLinksAgainstTheBase() {
        val crawler = ChapterCrawler(client, emptySet(), requireHttps = false)

        assertThat(crawler.makeAbsolute("/ch1", "https://example.com/novel"))
            .isEqualTo("https://example.com/ch1")
        assertThat(crawler.makeAbsolute("ch2", "https://example.com/novel"))
            .isEqualTo("https://example.com/ch2")
    }

    @Test
    fun resolveSameDomain_resolvesRelativeLinksAndRejectsForeignHosts() {
        val crawler = ChapterCrawler(client, emptySet(), requireHttps = false)

        assertThat(crawler.resolveSameDomain("ch2", "https://example.com/novel", "example.com"))
            .isEqualTo("https://example.com/ch2")
        assertThat(
            crawler.resolveSameDomain("http://example.com/ch1", "https://example.com/novel", "example.com")
        ).isEqualTo("https://example.com/ch1")
        assertThat(
            crawler.resolveSameDomain("https://evil.example/ch1", "https://example.com/novel", "example.com")
        ).isNull()
        assertThat(
            crawler.resolveSameDomain("https://cdn.example.com/ch1", "https://example.com/novel", "example.com")
        ).isEqualTo("https://cdn.example.com/ch1")
    }

    @Test
    fun requestBudget_neverConsumesPastItsLimit() {
        val budget = RequestBudget(2)

        assertThat(budget.tryConsume()).isTrue()
        assertThat(budget.tryConsume()).isTrue()
        assertThat(budget.tryConsume()).isFalse()
        assertThat(budget.used).isEqualTo(2)
        assertThat(budget.remaining).isEqualTo(0)
    }

    @Test
    fun crawlChapterList_keepsHomePagesAndAugmenterRequestsInsideOneBudget() = runTest {
        val homeHtml = """
            <html><body>
            <div data-novel-id="4321"></div>
            <a href="/sample/chapter-1.html">Chapter 1</a>
            <a href="/sample/page-2.html" rel="next">Next</a>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(homeHtml))
        for (page in 2..60) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """<a href="/sample/chapter-$page.html">Chapter $page</a><a href="/sample/page-${page + 1}.html" rel="next">Next</a>"""
                )
            )
        }

        crawlerForMappedHosts(setOf(ReadNovelFullListAugmenter())).crawlChapterList(homeUrl)

        assertThat(server.requestCount).isEqualTo(50)
        val requestedPaths = (1..50).mapNotNull { server.takeRequest(1, TimeUnit.SECONDS)?.path }
        assertThat(requestedPaths).hasSize(50)
        assertThat(requestedPaths.any { it.contains("ajax/chapter-archive") }).isFalse()
    }

    @Test
    fun crawlChapterList_rejectsCrossDomainNextPageBeforeContact() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """<a href="http://evil.example:${foreign.port}/page-2" rel="next">Next</a>"""
            )
        )

        val result = crawlerForMappedHosts().crawlChapterList(homeUrl)

        assertThat(result.links).isEmpty()
        assertThat(foreign.requestCount).isEqualTo(0)
    }

    @Test
    fun crawlChapterList_rejectsCrossDomainRedirectFromAugmenterBeforeContact() = runBlocking<Unit> {
        val homeHtml = java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText()
        server.enqueue(MockResponse().setResponseCode(200).setBody(homeHtml))
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "http://evil.example:${foreign.port}/ajax/chapter-archive")
        )

        val ex = runCatching {
            crawlerForMappedHosts(setOf(ReadNovelFullListAugmenter())).crawlChapterList(homeUrl)
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(RemoteRequestRejectedException::class.java)
        assertThat(foreign.requestCount).isEqualTo(0)
    }

    @Test
    fun crawlChapterList_upgradesHttpChapterLinksDiscoveredOnAnHttpsPage() = runBlocking<Unit> {
        val requestedUrls = mutableListOf<String>()
        val html = """
            <a href="http://readnovelfull.com/novel/sample/chapter-1.html">Chapter 1</a>
            <a href="http://readnovelfull.com/novel/sample/chapter-2.html">Chapter 2</a>
            <a href="http://evil.example/novel/sample/chapter-9.html">Chapter 9</a>
        """.trimIndent()

        val result = crawlerServingHttps(html, requestedUrls)
            .crawlChapterList("https://readnovelfull.com/novel/sample.html")

        assertThat(result.links.map { it.url }).containsExactly(
            "https://readnovelfull.com/novel/sample/chapter-1.html",
            "https://readnovelfull.com/novel/sample/chapter-2.html"
        )
        assertThat(requestedUrls).containsExactly("https://readnovelfull.com/novel/sample.html")
    }

    @Test
    fun crawlChapterList_omitsCrossDomainDiscoveredCover() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """<meta property="og:image" content="http://evil.example:${foreign.port}/cover.jpg">"""
            )
        )

        val result = crawlerForMappedHosts().crawlChapterList(homeUrl)

        assertThat(result.coverUrl).isNull()
        assertThat(foreign.requestCount).isEqualTo(0)
    }

    @Test
    fun crawlChapterList_followsSameDomainRelativeNextPage() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """<a href="/sample/chapter-1.html">Chapter 1</a><a href="/sample/page-2.html" rel="next">Next</a>"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """<a href="/sample/chapter-2.html">Chapter 2</a>"""
            )
        )

        val result = crawlerForMappedHosts().crawlChapterList(homeUrl)

        assertThat(server.requestCount).isEqualTo(2)
        assertThat(result.links.map { it.url })
            .contains("http://readnovelfull.com:${server.port}/sample/chapter-2.html")
    }

    @Test
    fun crawlChapterList_dispatchesToReadNovelFullAugmenter() = runBlocking<Unit> {
        val homeHtml = java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText()
        val archiveHtml = java.io.File("src/test/resources/readnovelfull/chapter_archive_sample.html").readText()
        server.enqueue(MockResponse().setResponseCode(200).setBody(homeHtml))
        server.enqueue(MockResponse().setResponseCode(200).setBody(archiveHtml))

        val crawler = ChapterCrawler(client, setOf(ReadNovelFullListAugmenter()), requireHttps = false)
        val result = crawler.crawlChapterList(homeUrl)

        assertThat(result.links.size).isAtLeast(10)
    }

    @Test
    fun crawlChapterList_worksWithEmptyAugmenterSet() = runBlocking<Unit> {
        val homeHtml = "<html><head><title>Sample</title></head><body>" +
            "<a href=\"/sample/chapter-1-12.html\">Chapter 1</a>" +
            "<a href=\"/sample/chapter-2-22.html\">Chapter 2</a>" +
            "</body></html>"
        server.enqueue(MockResponse().setResponseCode(200).setBody(homeHtml))

        val crawler = ChapterCrawler(client, emptySet(), requireHttps = false)
        val result = crawler.crawlChapterList(homeUrl)

        assertThat(result.links.size).isAtLeast(2)
    }
}
