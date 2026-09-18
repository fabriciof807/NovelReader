package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ReadNovelFullListAugmenterTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient
    private val augmenter = ReadNovelFullListAugmenter()
    private val policy = RemoteRequestPolicy.SameNovelDomain("readnovelfull.com")
    private lateinit var homeUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val port = server.url("").port
        homeUrl = "http://readnovelfull.com:$port/sample.html"
        val loopback = InetAddress.getByName("127.0.0.1")
        val overrideDns = object : Dns {
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
            .dns(overrideDns)
            .build()
        client = HttpClient(InMemoryCloudflareCookieStore(), okClient)
    }

    private fun budget() = RequestBudget(50)

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun canAugment_matchesReadNovelFullVariants() {
        assertThat(augmenter.canAugment("https://readnovelfull.com/sample.html")).isTrue()
        assertThat(augmenter.canAugment("https://www.readnovelfull.com/sample.html")).isTrue()
    }

    @Test
    fun canAugment_rejectsOtherDomains() {
        assertThat(augmenter.canAugment("https://freewebnovel.com/x.html")).isFalse()
        assertThat(augmenter.canAugment("https://parked.com/")).isFalse()
        assertThat(augmenter.canAugment("not a url")).isFalse()
    }

    @Test
    fun augment_returnsEmptyWhenDataNovelIdMissing() = runBlocking {
        val doc = Jsoup.parse("<html><body></body></html>")
        server.enqueue(MockResponse().setResponseCode(200).setBody("should-not-be-called"))
        val result = augmenter.augment(homeUrl, doc, client, policy, budget())
        assertThat(result).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun augment_callsChapterArchiveAndParsesLinks() = runBlocking {
        val docHtml = java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText()
        val doc = Jsoup.parse(docHtml)
        val archiveHtml = java.io.File("src/test/resources/readnovelfull/chapter_archive_sample.html").readText()
        server.enqueue(MockResponse().setResponseCode(200).setBody(archiveHtml))

        val result = augmenter.augment(homeUrl, doc, client, policy, budget())

        assertThat(result).hasSize(10)
        assertThat(result.first().title).isEqualTo("Chapter 1")
        assertThat(result.first().chapterNumber).isEqualTo(1)
        assertThat(result.last().chapterNumber).isEqualTo(10)
    }

    @Test
    fun augment_sendsXRequestedWithAndHomeUrlAsReferer() = runBlocking {
        val docHtml = java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText()
        val doc = Jsoup.parse(docHtml)
        server.enqueue(MockResponse().setResponseCode(200).setBody("<ul></ul>"))

        augmenter.augment(homeUrl, doc, client, policy, budget())

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("X-Requested-With")).isEqualTo("XMLHttpRequest")
        assertThat(recorded.getHeader("Referer")).isEqualTo(homeUrl)
    }

    @Test
    fun augment_returnsEmptyWhenTheRequestBudgetIsExhausted() = runBlocking {
        val docHtml = java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText()
        val doc = Jsoup.parse(docHtml)

        val result = augmenter.augment(homeUrl, doc, client, policy, RequestBudget(0))

        assertThat(result).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun augment_returnsEmptyOnNon200Response() = runBlocking {
        val docHtml = java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText()
        val doc = Jsoup.parse(docHtml)
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val result = augmenter.augment(homeUrl, doc, client, policy, budget())

        assertThat(result).isEmpty()
    }
}
