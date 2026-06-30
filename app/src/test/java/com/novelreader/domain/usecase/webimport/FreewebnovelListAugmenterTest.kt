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

class FreewebnovelListAugmenterTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient
    private val augmenter = FreewebnovelListAugmenter()
    private lateinit var homeUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val port = server.url("").port
        homeUrl = "http://www.freewebnovel.com:$port/sample.html"
        val loopback = InetAddress.getByName("127.0.0.1")
        val overrideDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname == "freewebnovel.com" || hostname == "www.freewebnovel.com") {
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

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun canAugment_matchesFreewebnovelVariants() {
        assertThat(augmenter.canAugment("https://www.freewebnovel.com/x.html")).isTrue()
        assertThat(augmenter.canAugment("https://freewebnovel.com/x")).isTrue()
    }

    @Test
    fun canAugment_rejectsOtherDomains() {
        assertThat(augmenter.canAugment("https://readnovelfull.com/x")).isFalse()
        assertThat(augmenter.canAugment("https://example.com/")).isFalse()
    }

    @Test
    fun augment_returnsEmptyWhenNoPaginationState() = runBlocking {
        val doc = Jsoup.parse("<html><body>no scripts</body></html>")
        val result = augmenter.augment(homeUrl, doc, client)
        assertThat(result).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun augment_fetchesAllPagesAndMergesLinks() = runBlocking<Unit> {
        val docHtml = """
            <html><head><script>
              window.chapterPagination = { currentPage: 1, pageSize: 2, totalPage: 3, totalChapters: 6 };
            </script></head><body></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(docHtml)
        repeat(2) { i ->
            val page = i + 2
            val body = """{"code":200,"html":"<ul><li><a href='/x/c$page.html'>C$page</a></li></ul>","page":$page}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        }

        val result = augmenter.augment(homeUrl, doc, client)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.title }).containsExactly("C2", "C3")
    }

    @Test
    fun augment_sendsXRequestedWithAndHomeUrlAsReferer() = runBlocking {
        val docHtml = """
            <html><head><script>
              window.chapterPagination = { currentPage: 1, pageSize: 1, totalPage: 2, totalChapters: 2 };
            </script></head><body></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(docHtml)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":200,"html":"<ul></ul>","page":2}"""))

        augmenter.augment(homeUrl, doc, client)

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("X-Requested-With")).isEqualTo("XMLHttpRequest")
        assertThat(recorded.getHeader("Referer")).isEqualTo(homeUrl)
        assertThat(recorded.path).contains("ajax=chapters")
        assertThat(recorded.path).contains("page=2")
    }

    @Test
    fun augment_skipsPageWithNon200Code() = runBlocking {
        val docHtml = """
            <html><head><script>
              window.chapterPagination = { currentPage: 1, pageSize: 1, totalPage: 2, totalChapters: 2 };
            </script></head><body></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(docHtml)
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val result = augmenter.augment(homeUrl, doc, client)

        assertThat(result).isEmpty()
    }
}
