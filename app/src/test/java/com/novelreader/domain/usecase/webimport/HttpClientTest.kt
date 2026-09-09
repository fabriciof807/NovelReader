package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val okClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        client = HttpClient(InMemoryCloudflareCookieStore(), okClient)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun get_sendsModernChromeUserAgentAndSecFetchHeaders() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>ok</html>").setResponseCode(200))

        client.get("http://127.0.0.1:${server.port}/page")

        val request = server.takeRequest()
        val ua = request.getHeader("User-Agent") ?: ""
        assertThat(ua).contains("Chrome/131")
        assertThat(ua).doesNotContain("Chrome/125")
        assertThat(request.getHeader("Accept-Encoding")).contains("br")
        assertThat(request.getHeader("Sec-Fetch-Site")).isNotNull()
        assertThat(request.getHeader("Sec-Fetch-Mode")).isNotNull()
        assertThat(request.getHeader("Sec-Fetch-Dest")).isNotNull()
        assertThat(request.getHeader("Upgrade-Insecure-Requests")).isEqualTo("1")
    }

    @Test
    fun get_rejectsBodiesLargerThanTheLimit() = runBlocking<Unit> {
        client.maxBodyBytes = 1024
        server.enqueue(MockResponse().setBody("x".repeat(4096)).setResponseCode(200))

        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            runBlocking { client.get("http://127.0.0.1:${server.port}/huge") }
        }
    }

    @Test
    fun get_rejectsDecompressionBombs() = runBlocking<Unit> {
        client.maxDecompressedBytes = 1024
        val bomb = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.GZIPOutputStream(out).use { it.write("x".repeat(64 * 1024).toByteArray()) }
        }.toByteArray()
        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(bomb))
                .setHeader("Content-Encoding", "gzip")
                .setResponseCode(200)
        )

        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            runBlocking { client.get("http://127.0.0.1:${server.port}/bomb") }
        }
    }

    @Test
    fun get_returns4xxResponseWithoutThrowing() = runBlocking {
        val body = "<!DOCTYPE html><html><title>Just a moment...</title></html>"
        server.enqueue(MockResponse().setBody(body).setResponseCode(403))

        val response = client.get("http://127.0.0.1:${server.port}/blocked")

        assertThat(response.statusCode).isEqualTo(403)
        assertThat(response.body).contains("Just a moment")
    }

    @Test
    fun get_replaysCloudflareClearanceCookieFromStore() = runBlocking {
        val store = InMemoryCloudflareCookieStore()
        store.putCookies(
            url = "http://127.0.0.1:${server.port}",
            cookies = listOf(StoredCookie(name = "cf_clearance", value = "abc123", domain = "127.0.0.1", path = "/", expiresAt = System.currentTimeMillis() + 3_600_000L))
        )
        val clientWithStore = HttpClient(store, OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build())
        server.enqueue(MockResponse().setBody("ok").setResponseCode(200))

        clientWithStore.get("http://127.0.0.1:${server.port}/page")

        val request = server.takeRequest()
        val cookieHeader = request.getHeader("Cookie") ?: ""
        assertThat(cookieHeader).contains("cf_clearance=abc123")
    }

    @Test
    fun get_decompressesBrotliEncodedBody() = runBlocking {
        val original = "<html><body>Hello, brotli world!</body></html>"
        val compressedHex = "1f2d0000c46d6c5dfb81e348bbb37b411044b001072e61f438e15d595f8a4b82717a4864b8efa8db9f863113714517"
        val compressed = compressedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val buffer = okio.Buffer().write(compressed)
        server.enqueue(
            MockResponse()
                .setBody(buffer)
                .addHeader("Content-Encoding", "br")
                .setResponseCode(200)
        )

        val response = client.get("http://127.0.0.1:${server.port}/br")

        assertThat(response.statusCode).isEqualTo(200)
        assertThat(response.body).isEqualTo(original)
        assertThat(response.bodyBytes?.contentEquals(original.toByteArray(Charsets.UTF_8))).isTrue()
    }

    @Test
    fun get_preservesKnownResponseBytesAlongsideUtf8Body() = runBlocking {
        val bytes = byteArrayOf(0x00, 0x01, 0x7f, 0x10, 0x20, 0xff.toByte(), 0x41, 0x42)
        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(bytes))
                .setResponseCode(200)
        )

        val response = client.get("http://127.0.0.1:${server.port}/binary")

        assertThat(response.body).isEqualTo(bytes.toString(Charsets.UTF_8))
        assertThat(response.bodyBytes?.contentEquals(bytes)).isTrue()
    }

    @Test
    fun get_persistsSetCookieAndSendsItOnSubsequentRequest() = runBlocking {
        val store = InMemoryCloudflareCookieStore()
        val clientWithStore = HttpClient(store, OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("home")
                .addHeader("Set-Cookie", "articlevisited=1; Path=/; Max-Age=31536000")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ajax")
        )

        clientWithStore.get("http://127.0.0.1:${server.port}/novel/foo")
        clientWithStore.get("http://127.0.0.1:${server.port}/novel/foo?ajax=chapters")

        val firstRequest = server.takeRequest()
        assertThat(firstRequest.getHeader("Cookie") ?: "").doesNotContain("articlevisited")

        val secondRequest = server.takeRequest()
        assertThat(secondRequest.getHeader("Cookie") ?: "").contains("articlevisited=1")
    }
}
