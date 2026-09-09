package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
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

class ChapterCrawlerTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient
    private lateinit var homeUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
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
    }

    @Test
    fun makeAbsolute_upgradesHttpLinksWhenTheBaseIsHttps() {
        val crawler = ChapterCrawler(client, emptySet(), requireHttps = false)

        assertThat(crawler.makeAbsolute("http://example.com/ch1", "https://example.com/novel"))
            .isEqualTo("https://example.com/ch1")
        assertThat(crawler.makeAbsolute("http://example.com/ch1", "http://example.com/novel"))
            .isEqualTo("http://example.com/ch1")
        assertThat(crawler.makeAbsolute("/ch1", "https://example.com/novel"))
            .isEqualTo("https://example.com/novel/ch1")
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
