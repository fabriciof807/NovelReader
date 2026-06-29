package com.novelreader.domain.usecase.webimport

import android.util.Log
import com.novelreader.BuildConfig
import com.novelreader.data.parser.HtmlSanitizer
import com.novelreader.data.parser.ParserRegistry
import kotlinx.coroutines.delay
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

data class FetchedChapter(
    val title: String,
    val content: String,
    val fileName: String
)

private const val DIAGNOSTIC_TAG = "WebFetchProbe"
private const val BODY_SNIPPET_MAX = 256

@Singleton
class ChapterFetcher @Inject constructor(
    private val parserRegistry: ParserRegistry,
    private val httpClient: HttpClient
) {
    @androidx.annotation.VisibleForTesting
    constructor(parserRegistry: ParserRegistry, httpClient: HttpClient, requireHttps: Boolean) : this(parserRegistry, httpClient) {
        this.requireHttps = requireHttps
    }

    private var requireHttps: Boolean = true
    suspend fun fetch(url: String, fileName: String, chapterTitle: String): FetchedChapter {
        val chapterDoc = fetchWithRetry(url)
        val parser = parserRegistry.getParserForUrl(url)
        val parsed = parser.parse(chapterDoc, fileName)

        val resultTitle = parsed.chapterTitle.ifBlank {
            chapterTitle.ifBlank { fileName }
        }
        val content = if (parsed.content.isBlank()) {
            val bodyText = chapterDoc.body().html()
            HtmlSanitizer.sanitizeHtml(bodyText)
        } else parsed.content

        return FetchedChapter(title = resultTitle, content = content, fileName = fileName)
    }

    private suspend fun fetchWithRetry(url: String, maxRetries: Int = 3): Document {
        if (requireHttps && !url.startsWith("https://")) throw SecurityException("Apenas HTTPS permitido")
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                if (attempt > 1) delay(attempt * 2000L)
                val response = httpClient.get(url, referrer = url.substringBeforeLast("/"))
                val statusCode = response.statusCode
                if (statusCode in 200..299) {
                    return Jsoup.parse(response.body, url)
                }
                if (isCloudflareChallenge(statusCode, response.body, response.headers)) {
                    if (BuildConfig.DEBUG) {
                        val diag = extractWebFetchDiagnostic(
                            throwable = HttpStatusException("HTTP error fetching URL", statusCode, url),
                            callSite = "fetcher",
                            serverHeader = response.headers["Server"],
                            cfRayHeader = response.headers["cf-ray"],
                            setCookieHeader = response.headers["Set-Cookie"],
                            bodySnippet = response.body.take(BODY_SNIPPET_MAX)
                        )
                        Log.w(DIAGNOSTIC_TAG, formatWebFetchDiagnostic(diag))
                    }
                    throw CloudflareChallengeRequiredException(
                        url = url,
                        evidence = response.body.take(BODY_SNIPPET_MAX)
                    )
                }
                if (BuildConfig.DEBUG) {
                    val diag = extractWebFetchDiagnostic(
                        throwable = HttpStatusException("HTTP error fetching URL", statusCode, url),
                        callSite = "fetcher",
                        serverHeader = response.headers["Server"],
                        cfRayHeader = response.headers["cf-ray"],
                        setCookieHeader = response.headers["Set-Cookie"],
                        bodySnippet = response.body.take(BODY_SNIPPET_MAX)
                    )
                    Log.w(DIAGNOSTIC_TAG, formatWebFetchDiagnostic(diag))
                }
                val hse = HttpStatusException("HTTP error fetching URL", statusCode, url)
                lastException = hse
                val retryDelay = nextRetryDelayMs(attempt, statusCode)
                if (retryDelay > 0L) {
                    delay(retryDelay)
                } else {
                    throw hse
                }
            } catch (e: CloudflareChallengeRequiredException) {
                throw e
            } catch (e: HttpStatusException) {
                lastException = e
                val retryDelay = nextRetryDelayMs(attempt, e.statusCode)
                if (retryDelay > 0L && attempt < maxRetries) {
                    delay(retryDelay)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) delay(attempt * 2000L)
            }
        }
        throw lastException ?: Exception("Request failed after $maxRetries retries")
    }
}
