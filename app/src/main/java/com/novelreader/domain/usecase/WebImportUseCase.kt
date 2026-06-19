package com.novelreader.domain.usecase

import android.content.Context
import android.net.Uri
import com.novelreader.R
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.parser.HtmlSanitizer
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.data.parser.ChapterNumberExtractor
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import com.novelreader.util.StringUtils
import com.novelreader.di.qualifiers.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class ChapterLink(
    val title: String,
    val url: String,
    val chapterNumber: Int = Int.MAX_VALUE
)

data class FetchResult(
    val chapters: List<ChapterLink>,
    val coverUrl: String? = null,
    val novelTitle: String? = null
)

private val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.113 Mobile Safari/537.36"
private const val MAX_PAGES = 50
private const val PAGE_DELAY_MS = 500L

@Singleton
class WebImportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val parserRegistry: ParserRegistry,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun fetchChapterList(homeUrl: String): Result<FetchResult> = withContext(io) {
        try {
            val homeDomain = Uri.parse(homeUrl).host ?: ""
            val allLinks = mutableListOf<ChapterLink>()
            var currentUrl: String? = homeUrl
            var coverUrl: String? = null
            var novelTitle: String? = null
            var pageCount = 0

            while (currentUrl != null && pageCount < MAX_PAGES) {
                val doc = fetchPage(currentUrl)
                if (pageCount == 0) {
                    coverUrl = extractCoverUrl(doc, homeUrl)
                    novelTitle = extractNovelTitle(doc)
                }
                allLinks.addAll(extractChapterLinks(doc, currentUrl, homeDomain))
                currentUrl = findNextPageUrl(doc, currentUrl)
                pageCount++
                if (currentUrl != null) delay(PAGE_DELAY_MS)
            }

            val links = allLinks.distinctBy { it.url }
            if (links.isEmpty()) {
                return@withContext Result.failure(Exception(context.getString(R.string.web_import_no_chapters_found)))
            }
            Result.success(FetchResult(chapters = links, coverUrl = coverUrl, novelTitle = novelTitle))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importChapters(
        novelTitle: String,
        links: List<ChapterLink>,
        coverUrl: String? = null,
        filesDir: File? = null,
        orderIndexOffset: Int = 0,
        sourceUrl: String = "",
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        onError: ((url: String, error: String) -> Unit)? = null
    ): Result<Long> = withContext(io) {
        try {
            var existingNovel = novelRepository.getNovelByTitle(novelTitle)
            var novelId: Long
            val existingFileNames: MutableSet<String>

            if (existingNovel != null) {
                novelId = existingNovel.id
                existingFileNames = chapterRepository.getChaptersByNovelSync(novelId)
                    .map { it.fileName }.toMutableSet()
            } else {
                val novelEntity = NovelEntity(
                    title = novelTitle,
                    sourceFolder = "",
                    totalChapters = 0
                )
                novelId = novelRepository.insert(novelEntity)
                existingFileNames = mutableSetOf()
            }

            if (sourceUrl.isNotBlank()) {
                novelRepository.updateSourceUrl(novelId, sourceUrl)
            }

            if (coverUrl != null && filesDir != null && existingNovel?.coverPath == null) {
                saveCoverImage(novelId, coverUrl, filesDir)
            }

            val sorted = links.sortedBy { it.chapterNumber }
            val inserts = mutableListOf<ChapterEntity>()
            var successCount = 0
            var consecutiveErrors = 0

            for ((index, link) in sorted.withIndex()) {
                val fileName = fileNameFromUrl(link.url, link.chapterNumber)
                if (fileName in existingFileNames) {
                    successCount++
                    onProgress?.invoke(successCount, sorted.size)
                    continue
                }

                try {
                    if (consecutiveErrors > 0) delay(consecutiveErrors * 1000L)
                    val chapterDoc = fetchWithRetry(link.url)
                    consecutiveErrors = 0

                    val parser = parserRegistry.getParserForUrl(link.url)
                    val parsed = parser.parse(chapterDoc, fileName)

                    val chapterTitle = parsed.chapterTitle.ifBlank {
                        link.title.ifBlank { fileNameFromUrl(link.url, link.chapterNumber) }
                    }
                    val content = if (parsed.content.isBlank()) {
                        val bodyText = chapterDoc.body().html()
                        HtmlSanitizer.sanitizeHtml(bodyText)
                    } else parsed.content

                    inserts.add(ChapterEntity(
                        novelId = novelId,
                        title = chapterTitle,
                        fileName = fileName,
                        orderIndex = orderIndexOffset + index,
                        content = content
                    ))
                    existingFileNames.add(fileName)
                    successCount++
                } catch (e: Exception) {
                    consecutiveErrors++
                    onError?.invoke(link.url, e.message ?: "Erro desconhecido")
                }
                onProgress?.invoke(successCount, sorted.size)
            }

            if (inserts.isNotEmpty()) {
                chapterRepository.insertAll(inserts)
                chapterRepository.reNormalizeOrderIndices(novelId)
                val totalChapters = chapterRepository.getChaptersByNovelSync(novelId).size
                novelRepository.updateChapterCount(novelId, totalChapters)
            }

            Result.success(novelId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchPage(url: String): Document {
        if (!url.startsWith("https://")) throw SecurityException("Apenas HTTPS permitido")
        return Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(30_000)
            .followRedirects(true)
            .get()
    }

    private fun findNextPageUrl(doc: Document, currentUrl: String): String? {
        val nextTexts = listOf("next", "próxima", "proximo", ">", "»", "›")
        val nextSelectors = listOf("a.next", "a[rel=next]", ".pagination a", ".pager a", "a[aria-label*=next]", "a[aria-label*=Next]")

        for (selector in nextSelectors) {
            val el = doc.selectFirst(selector) ?: continue
            val href = el.attr("abs:href").ifEmpty { el.attr("href") }
            if (href.isNotBlank() && href != currentUrl) {
                return makeAbsolute(href, currentUrl)
            }
        }

        for (link in doc.select("a[href]")) {
            val text = link.text().trim().lowercase()
            val ariaLabel = (link.attr("aria-label") ?: "").lowercase()
            if (nextTexts.any { text == it || ariaLabel.contains(it) }) {
                val href = link.attr("abs:href").ifEmpty { link.attr("href") }
                if (href.isNotBlank() && href != currentUrl) {
                    return makeAbsolute(href, currentUrl)
                }
            }
        }

        return null
    }

    private fun extractChapterLinks(doc: Document, homeUrl: String, homeDomain: String): List<ChapterLink> {
        val allLinks = doc.select("a[href]")
        val candidateLinks = mutableListOf<Pair<String, String>>()

        for (link in allLinks) {
            val href = link.attr("abs:href").ifEmpty { link.attr("href") }
            val text = link.text().trim()

            if (href.isBlank() || href == "#" || href.startsWith("javascript:") || href.startsWith("mailto:")) continue
            if (text.isBlank()) continue

            val linkDomain = Uri.parse(href).host
            if (linkDomain != null && linkDomain != homeDomain && !href.startsWith("/")) continue

            val normalizedHref = if (href.startsWith("/")) {
                val base = homeUrl.trimEnd('/')
                "$base$href"
            } else href

            val lowerHref = normalizedHref.lowercase()
            val lowerText = text.lowercase()

            val chapterKeywords = listOf("chapter", "ch-", "ch_", "capitulo", "cap-", "vol-", "book-", "part-")
            val hasChapterKeyword = chapterKeywords.any { it in lowerHref || it in lowerText }
            val hasNumber = Regex("""\d+""").containsMatchIn(text)

            val excludeKeywords = listOf("home", "login", "register", "signup", "contact", "about", "privacy", "terms", "profile", "settings", "search", "forum", "discord", "facebook", "twitter", "instagram", "youtube")
            val isExcluded = excludeKeywords.any { it in lowerHref || it in lowerText }

            if (isExcluded) continue

            if (hasChapterKeyword && hasNumber) {
                candidateLinks.add(text to normalizedHref)
            }
        }

        if (candidateLinks.size < 3) {
            for (link in allLinks) {
                val href = link.attr("abs:href").ifEmpty { link.attr("href") }
                val text = link.text().trim()

                if (href.isBlank() || href == "#" || href.startsWith("javascript:") || href.startsWith("mailto:")) continue
                if (text.isBlank()) continue

                val linkDomain = Uri.parse(href).host
                if (linkDomain != null && linkDomain != homeDomain && !href.startsWith("/")) continue

                val normalizedHref = if (href.startsWith("/")) {
                    val base = homeUrl.trimEnd('/')
                    "$base$href"
                } else href

                val lowerHref = normalizedHref.lowercase()
                val lowerText = text.lowercase()

                val isExcluded = listOf("home", "login", "register", "signup", "contact", "about", "privacy", "terms", "profile", "settings", "search", "forum", "discord", "facebook", "twitter", "instagram", "youtube")
                    .any { it in lowerHref || it in lowerText }
                if (isExcluded) continue

                val hasNumber = Regex("""\d+""").containsMatchIn(text) || Regex("""/\d+/""").containsMatchIn(normalizedHref)
                if (hasNumber) {
                    candidateLinks.add(text to normalizedHref)
                }
            }
        }

        val seenUrls = mutableSetOf<String>()
        val result = mutableListOf<ChapterLink>()
        for ((title, url) in candidateLinks) {
            val normalized = url.trimEnd('/')
            if (normalized in seenUrls) continue
            seenUrls.add(normalized)
            val num = extractChapterNumber(title, url)
            result.add(ChapterLink(title = title, url = normalized, chapterNumber = num))
        }
        return result.distinctBy { it.url }
    }

    private fun extractChapterNumber(title: String, url: String): Int {
        val fileName = fileNameFromUrl(url, Int.MAX_VALUE)
        return ChapterNumberExtractor.extract(title = title, url = url, fileName = fileName)
    }

    private fun fileNameFromUrl(url: String, chapterNumber: Int): String {
        return StringUtils.fileNameFromUrl(url, "chapter_$chapterNumber")
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

    private fun extractCoverUrl(doc: Document, homeUrl: String): String? {
        val ogImage = doc.select("meta[property=og:image]").first()?.attr("content")
        if (ogImage != null) return makeAbsolute(ogImage, homeUrl)
        val twitterImage = doc.select("meta[name=twitter:image]").first()?.attr("content")
        if (twitterImage != null) return makeAbsolute(twitterImage, homeUrl)
        val firstImage = doc.select("img[src]").firstOrNull {
            val src = it.attr("src")
            src.isNotBlank() && !src.contains("logo", ignoreCase = true)
                    && !src.contains("icon", ignoreCase = true)
                    && !src.contains("avatar", ignoreCase = true)
                    && !src.contains("banner", ignoreCase = true)
        }
        if (firstImage != null) return makeAbsolute(firstImage.attr("src"), homeUrl)
        return null
    }

    private fun makeAbsolute(url: String, base: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val baseUrl = base.trimEnd('/')
        return if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/$url"
    }

    private fun extractNovelTitle(doc: Document): String? {
        val ogTitle = doc.select("meta[property=og:title]").first()?.attr("content")?.trim()
        if (!ogTitle.isNullOrBlank()) return ogTitle
        val twitterTitle = doc.select("meta[name=twitter:title]").first()?.attr("content")?.trim()
        if (!twitterTitle.isNullOrBlank()) return twitterTitle
        val titleTag = doc.title().trim()
        if (titleTag.isNotBlank()) {
            val cleaned = titleTag
                .replace(Regex("""\s*[-–—|•·:]\s*(web)?novel.*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*[-–—|•·:]\s*(read|ler|online|free|gratis).*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (cleaned.isNotBlank()) return cleaned
        }
        val h1 = doc.select("h1").firstOrNull { it.text().trim().length > 2 }
        if (h1 != null) return h1.text().trim()
        return null
    }

    private suspend fun saveCoverImage(novelId: Long, coverUrl: String, filesDir: File) {
        try {
            if (!coverUrl.startsWith("https://")) return
            val dir = File(filesDir, "covers")
            dir.mkdirs()
            val dest = File(dir, "novel_$novelId.jpg")
            val connection = URL(coverUrl).openConnection()
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.getInputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (dest.exists()) {
                novelRepository.updateCoverPath(novelId, dest.absolutePath)
            }
        } catch (_: Exception) { }
    }
}
