package com.novelreader.domain.usecase.webimport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import com.novelreader.util.PublicOnlyDns
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpClient @Inject constructor(
    private val cookieStore: CloudflareCookieStore
) {
    @androidx.annotation.VisibleForTesting
    constructor(cookieStore: CloudflareCookieStore, okHttpClient: OkHttpClient) : this(cookieStore) {
        this.ok = okHttpClient.newBuilder().cookieJar(CloudflareCookieJar(cookieStore)).build()
    }

    private var ok: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(CloudflareCookieJar(cookieStore))
        .dns(PublicOnlyDns())
        .build()

    @androidx.annotation.VisibleForTesting
    internal var maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES

    @androidx.annotation.VisibleForTesting
    internal var maxDecompressedBytes: Int = DEFAULT_MAX_DECOMPRESSED_BYTES

    suspend fun get(
        url: String,
        referrer: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): HttpResponse = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,application/signed-exchange;v=b3;q=0.7,*/*;q=0.6")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-User", "?1")
            .header("Upgrade-Insecure-Requests", "1")
        if (referrer != null) {
            requestBuilder.header("Referer", referrer)
        }
        for ((name, value) in extraHeaders) {
            requestBuilder.header(name, value)
        }
        val response = ok.newCall(requestBuilder.build()).execute()
        response.use { r ->
            val finalUrl = r.request.url.toString()
            val bodySource = r.body ?: return@use HttpResponse(
                statusCode = r.code,
                body = "",
                headers = r.headers.toMap(),
                finalUrl = finalUrl
            )
            val raw = bodySource.byteStream().use { readBounded(it, maxBodyBytes) }
            val contentEncoding = r.header("Content-Encoding")?.lowercase()
            val decompressed = when (contentEncoding) {
                "gzip" -> decompressGzip(raw)
                "br" -> decompressBrotli(raw)
                "deflate" -> decompressDeflate(raw)
                else -> raw
            }
            HttpResponse(
                statusCode = r.code,
                body = decompressed.toString(Charsets.UTF_8),
                headers = r.headers.toMap(),
                finalUrl = finalUrl,
                bodyBytes = decompressed
            )
        }
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > limit) throw java.io.IOException("Response body exceeds $limit bytes")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun decompressGzip(bytes: ByteArray): ByteArray =
        java.util.zip.GZIPInputStream(bytes.inputStream()).use { readBounded(it, maxDecompressedBytes) }

    private fun decompressBrotli(bytes: ByteArray): ByteArray =
        org.brotli.dec.BrotliInputStream(bytes.inputStream()).use { readBounded(it, maxDecompressedBytes) }

    private fun decompressDeflate(bytes: ByteArray): ByteArray =
        java.util.zip.InflaterInputStream(bytes.inputStream()).use { readBounded(it, maxDecompressedBytes) }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
        const val DEFAULT_MAX_BODY_BYTES = 8 * 1024 * 1024
        const val DEFAULT_MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024
    }
}

private class CloudflareCookieJar(
    private val store: CloudflareCookieStore
) : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val stored = runBlocking { store.cookiesFor(url.toString()) }
        return stored.mapNotNull { sc ->
            runCatching {
                Cookie.Builder()
                    .name(sc.name)
                    .value(sc.value)
                    .domain(sc.domain)
                    .path(sc.path)
                    .build()
            }.getOrNull()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val stored = cookies.map { c ->
            StoredCookie(
                name = c.name,
                value = c.value,
                domain = c.domain,
                path = c.path,
                expiresAt = c.expiresAt
            )
        }
        runBlocking { store.putCookies(url.toString(), stored) }
    }
}

private fun okhttp3.Headers.toMap(): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for (i in 0 until size) {
        result[name(i)] = value(i)
    }
    return result
}
