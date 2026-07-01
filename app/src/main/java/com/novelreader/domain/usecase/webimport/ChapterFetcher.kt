package com.novelreader.domain.usecase.webimport

import android.util.Log
import com.novelreader.BuildConfig
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
private const val FAILURE_TAG = "WebImportFailure"
private const val BODY_SNIPPET_MAX = 256
private const val DEFAULT_MAX_RETRIES = 5

@Singleton
class ChapterFetcher @Inject constructor(
    private val parserRegistry: ParserRegistry,
    private val httpClient: HttpClient
) {
    @androidx.annotation.VisibleForTesting
    constructor(parserRegistry: ParserRegistry, httpClient: HttpClient, requireHttps: Boolean) : this(parserRegistry, httpClient) {
        this.requireHttps = requireHttps
    }

    @androidx.annotation.VisibleForTesting
    var retryDelayFn: (Int, Int) -> Long = ::nextRetryDelayMs

    @androidx.annotation.VisibleForTesting
    var maxRetries: Int = DEFAULT_MAX_RETRIES

    private var requireHttps: Boolean = true

    suspend fun fetch(url: String, fileName: String, chapterTitle: String): FetchedChapter {
        val chapterDoc = fetchWithRetry(url)
        val parser = parserRegistry.getParserForUrl(url)
        val parsed = parser.parse(chapterDoc, fileName)

        val resultTitle = parsed.chapterTitle.ifBlank {
            chapterTitle.ifBlank { fileName }
        }

        return FetchedChapter(title = resultTitle, content = parsed.content, fileName = fileName)
    }

    private suspend fun fetchWithRetry(url: String): Document {
        if (requireHttps && !url.startsWith("https://")) throw SecurityException("Apenas HTTPS permitido")
        var lastException: Exception? = null
        var lastStatusCode: Int = 0
        for (attempt in 1..maxRetries) {
            try {
                if (attempt > 1) delay(attempt * 2000L)
                val response = httpClient.get(url, referrer = url.substringBeforeLast("/"))
                val statusCode = response.statusCode
                lastStatusCode = statusCode
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
                if (attempt >= maxRetries) {
                    if (statusCode == 429) {
                        Log.w(
                            FAILURE_TAG,
                            "url=$url attempt=$attempt/$maxRetries status=$statusCode errType=rate_limited errMsg=giving_up"
                        )
                        throw RateLimitedException(url = url, attempts = attempt, lastStatusCode = statusCode)
                    }
                    Log.w(
                        FAILURE_TAG,
                        "url=$url attempt=$attempt/$maxRetries status=$statusCode errType=network errMsg=giving_up"
                    )
                    throw hse
                }
                Log.w(
                    FAILURE_TAG,
                    "url=$url attempt=$attempt/$maxRetries status=$statusCode errType=network errMsg=will_retry"
                )
                val retryDelay = retryDelayFn(attempt, statusCode)
                if (retryDelay > 0L) delay(retryDelay)
            } catch (e: CloudflareChallengeRequiredException) {
                throw e
            } catch (e: RateLimitedException) {
                throw e
            } catch (e: HttpStatusException) {
                lastException = e
                lastStatusCode = e.statusCode
                if (attempt >= maxRetries) {
                    if (e.statusCode == 429) {
                        Log.w(
                            FAILURE_TAG,
                            "url=$url attempt=$attempt/$maxRetries status=${e.statusCode} errType=rate_limited errMsg=giving_up"
                        )
                        throw RateLimitedException(url = url, attempts = attempt, lastStatusCode = e.statusCode)
                    }
                    Log.w(
                        FAILURE_TAG,
                        "url=$url attempt=$attempt/$maxRetries status=${e.statusCode} errType=network errMsg=giving_up"
                    )
                    throw e
                }
                Log.w(
                    FAILURE_TAG,
                    "url=$url attempt=$attempt/$maxRetries status=${e.statusCode} errType=network errMsg=will_retry"
                )
                val retryDelay = retryDelayFn(attempt, e.statusCode)
                if (retryDelay > 0L) delay(retryDelay)
            } catch (e: Exception) {
                lastException = e
                Log.w(
                    FAILURE_TAG,
                    "url=$url attempt=$attempt/$maxRetries errType=other errMsg=${e.message ?: e::class.java.simpleName}"
                )
                if (attempt >= maxRetries) throw e
                delay(attempt * 2000L)
            }
        }
        if (lastStatusCode == 429) {
            throw RateLimitedException(url = url, attempts = maxRetries, lastStatusCode = lastStatusCode)
        }
        throw lastException ?: Exception("Request failed after $maxRetries retries")
    }
}
