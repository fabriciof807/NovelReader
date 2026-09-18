package com.novelreader.domain.usecase.webimport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
        this.ok = okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CloudflareCookieJar(cookieStore))
            .build()
        this.allowCleartextForTests = true
    }

    private var ok: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CloudflareCookieJar(cookieStore))
        .dns(PublicOnlyDns())
        .build()

    @androidx.annotation.VisibleForTesting
    internal var maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES

    @androidx.annotation.VisibleForTesting
    internal var maxDecompressedBytes: Int = DEFAULT_MAX_DECOMPRESSED_BYTES

    private var allowCleartextForTests: Boolean = false

    suspend fun get(
        url: String,
        referrer: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        policy: RemoteRequestPolicy = RemoteRequestPolicy.AnyPublicHttps,
        maxBodyBytes: Int = this.maxBodyBytes,
        maxDecompressedBytes: Int = this.maxDecompressedBytes
    ): HttpResponse = withContext(Dispatchers.IO) {
        var currentUrl = url
        var redirects = 0
        var result: HttpResponse? = null
        while (result == null) {
            if (!policy.allows(currentUrl, allowCleartextForTests)) {
                throw RemoteRequestRejectedException("Blocked remote destination")
            }
            val response = ok.newCall(buildRequest(currentUrl, referrer, extraHeaders)).execute()
            if (response.code in 300..399) {
                val location = response.header("Location")
                val next = location?.let { response.request.url.resolve(it)?.toString() }
                response.close()
                if (next == null || redirects >= MAX_REDIRECTS) {
                    throw RemoteRequestRejectedException("Blocked redirect")
                }
                if (!policy.allows(next, allowCleartextForTests)) {
                    throw RemoteRequestRejectedException("Blocked redirect destination")
                }
                redirects++
                currentUrl = next
            } else {
                result = readResponse(response, maxBodyBytes, maxDecompressedBytes)
            }
        }
        result ?: error("unreachable")
    }

    private fun buildRequest(
        url: String,
        referrer: String?,
        extraHeaders: Map<String, String>
    ): Request {
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
        return requestBuilder.build()
    }

    private fun readResponse(response: Response, maxBodyBytes: Int, maxDecompressedBytes: Int): HttpResponse {
        response.use { r ->
            val finalUrl = r.request.url.toString()
            val bodySource = r.body ?: return HttpResponse(
                statusCode = r.code,
                body = "",
                headers = r.headers.toMap(),
                finalUrl = finalUrl
            )
            val raw = bodySource.byteStream().use { readBounded(it, maxBodyBytes) }
            val contentEncoding = r.header("Content-Encoding")?.lowercase()
            val decompressed = when (contentEncoding) {
                "gzip" -> decompressGzip(raw, maxDecompressedBytes)
                "br" -> decompressBrotli(raw, maxDecompressedBytes)
                "deflate" -> decompressDeflate(raw, maxDecompressedBytes)
                else -> raw
            }
            return HttpResponse(
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

    private fun decompressGzip(bytes: ByteArray, limit: Int): ByteArray =
        java.util.zip.GZIPInputStream(bytes.inputStream()).use { readBounded(it, limit) }

    private fun decompressBrotli(bytes: ByteArray, limit: Int): ByteArray =
        org.brotli.dec.BrotliInputStream(bytes.inputStream()).use { readBounded(it, limit) }

    private fun decompressDeflate(bytes: ByteArray, limit: Int): ByteArray =
        java.util.zip.InflaterInputStream(bytes.inputStream()).use { readBounded(it, limit) }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
        const val DEFAULT_MAX_BODY_BYTES = 8 * 1024 * 1024
        const val DEFAULT_MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024
        const val MAX_REDIRECTS = 5
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
