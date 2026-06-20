package com.novelreader.domain.usecase.webimport

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

private val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.113 Mobile Safari/537.36"

@Singleton
class ChapterFetcher @Inject constructor(
    private val parserRegistry: ParserRegistry
) {
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
        if (!url.startsWith("https://")) throw SecurityException("Apenas HTTPS permitido")
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                if (attempt > 1) delay(attempt * 2000L)
                return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .referrer(url.substringBeforeLast("/"))
                    .timeout(30_000)
                    .followRedirects(true)
                    .get()
            } catch (e: HttpStatusException) {
                lastException = e
                if (e.statusCode == 429) {
                    delay(attempt * 3000L)
                } else if (e.statusCode in 500..599) {
                    delay(attempt * 2000L)
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
