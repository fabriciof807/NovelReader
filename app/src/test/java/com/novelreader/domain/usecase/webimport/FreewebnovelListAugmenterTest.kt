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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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
    fun augment_fetchesAttributePaginationPagesTwoThroughEight() = runBlocking<Unit> {
        val doc = Jsoup.parse(
            """
                <div id="indexListPage" data-page-size="40" data-total-page="8" data-total-chapters="291"></div>
            """.trimIndent()
        )
        repeat(7) { index ->
            val page = index + 2
            val body = """{"code":200,"html":"<a href='/x/c$page.html'>C$page</a>","page":$page}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        }

        val result = augmenter.augment(homeUrl, doc, client)
        val requests = (2..8).map { server.takeRequest() }

        assertThat(result.map { it.title }).containsExactly(
            "C2", "C3", "C4", "C5", "C6", "C7", "C8"
        ).inOrder()
        requests.forEachIndexed { index, request ->
            assertThat(request.path).contains("page=${index + 2}")
            assertThat(request.path).contains("pageSize=40")
        }
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

    @Test
    fun augment_extractsLinksFromRealHomeFixture() = runBlocking<Unit> {
        val homeHtml = """
            <html><head><script>
              window.chapterPagination = { currentPage: 1, pageSize: 40, totalPage: 3, totalChapters: 120 };
            </script></head><body><a href='/novel/child-of-destiny/chapter-1'>C1</a></body></html>
        """.trimIndent()
        val homeDoc = Jsoup.parse(homeHtml)
        val port = server.url("").port
        val page2Body = """{"code":200,"html":"<ul><li><a href='/novel/child-of-destiny/chapter-41' class='con'>Chapter 41</a></li></ul>","page":2}"""
        val page3Body = """{"code":200,"html":"<ul><li><a href='/novel/child-of-destiny/chapter-81' class='con'>Chapter 81</a></li></ul>","page":3}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(page2Body))
        server.enqueue(MockResponse().setResponseCode(200).setBody(page3Body))

        val result = augmenter.augment(
            "http://www.freewebnovel.com:$port/novel/child-of-destiny",
            homeDoc,
            client
        )

        assertThat(result).hasSize(2)
        assertThat(result.map { it.title }).containsExactly("Chapter 41", "Chapter 81")
    }
}
