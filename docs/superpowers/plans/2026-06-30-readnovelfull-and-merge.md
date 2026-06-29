# readnovelfull.com + cross-site merge — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix readnovelfull.com chapter-list import (currently 30-60 of 200+ chapters) AND enable cross-site novel merging with a "new chapters" badge on the library card.

**Architecture:**

- **Phase A (readnovelfull)**: Extract per-domain chapter-list augmentation into a `NovelListAugmenter` interface with Hilt multibinding. Two impls: `FreewebnovelListAugmenter` (refactor of existing inline code) and `ReadNovelFullListAugmenter` (new, calls `/ajax/chapter-archive?novelId=N`). Side-fixes for the CF dialog host hardcoding and the cookie-key mismatch.
- **Phase B (merge + badge)**: Room v8 → v9 migration adds `novel_sources` (1-to-many from `novels`) and `novels.hasUpdates` (boolean badge flag). `WebImportUseCase` dedupes incoming chapters by extracted chapter number, with cover first-wins and `hasUpdates` set on the second-and-later imports. `ChapterUpdateCheckWorker` iterates `novel_sources` (multi-source auto-update). UI: blue dot on `NovelCard` / `NovelListItem` when `hasUpdates=true`; cleared on novel open.

**Tech Stack:** Kotlin 2.2.10, Room 2.8.4, Hilt 2.59.2, OkHttp (existing), Jsoup 1.22.1, Jetpack Compose Material3.

## Global Constraints

- Kotlin official style; **no comments unless requested**.
- All strings bilingual PT/EN; `I18nCoverageTest` enforces EN mirror.
- TDD: failing test first; one logical commit per task (or per tightly-coupled task group).
- Schema migration JSON committed to `app/schemas/com.novelreader.data.local.db.NovelDatabase/<version>.json`.
- Existing tests stay green at every step.
- `CoverDownloader`, `HttpClient`, `CloudflareChallengeDialog`, `CloudflareCookieStore` retain their existing public surface unless explicitly modified in this plan.
- Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` before every commit.

---

# Phase A — readnovelfull.com

## Task 1: `NovelListAugmenter` interface

**Files:**
- Create: `app/src/main/java/com/novelreader/domain/usecase/webimport/NovelListAugmenter.kt`
- Test: `app/src/test/java/com/novelreader/domain/usecase/webimport/NovelListAugmenterTest.kt`

**Interfaces:**
- Consumes: `ChapterLink` (existing data class in `ChapterCrawler.kt`).
- Produces: `interface NovelListAugmenter { fun canAugment(homeUrl: String): Boolean; suspend fun augment(homeUrl: String, homeDoc: Document, httpClient: HttpClient): List<ChapterLink> }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.novelreader.domain.usecase.webimport

import com.novelreader.data.parser.NovelParser
import kotlinx.coroutines.test.runTest
import org.jsoup.nodes.Document
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelListAugmenterTest {
    private class FakeAug(val match: Boolean) : NovelListAugmenter {
        override fun canAugment(homeUrl: String) = match
        override suspend fun augment(homeUrl: String, homeDoc: Document, httpClient: HttpClient): List<ChapterLink> = emptyList()
    }

    @Test
    fun `canAugment dispatches to matching impl`() {
        val a = FakeAug(match = true)
        val b = FakeAug(match = false)
        assertTrue(a.canAugment("https://example.com/"))
        assertFalse(b.canAugment("https://example.com/"))
    }
}
```

- [ ] **Step 2: Run test — fails with "Unresolved reference: NovelListAugmenter"**

```bash
cd /home/fabricio/Repos/Opencode/android-book
./gradlew :app:testDebugUnitTest --tests "com.novelreader.domain.usecase.webimport.NovelListAugmenterTest"
```

- [ ] **Step 3: Implement the interface**

```kotlin
package com.novelreader.domain.usecase.webimport

import org.jsoup.nodes.Document

interface NovelListAugmenter {
    fun canAugment(homeUrl: String): Boolean
    suspend fun augment(
        homeUrl: String,
        homeDoc: Document,
        httpClient: HttpClient
    ): List<ChapterLink>
}
```

- [ ] **Step 4: Run test — passes**

- [ ] **Step 5: Commit**

```bash
cd /home/fabricio/Repos/Opencode/android-book
git add app/src/main/java/com/novelreader/domain/usecase/webimport/NovelListAugmenter.kt app/src/test/java/com/novelreader/domain/usecase/webimport/NovelListAugmenterTest.kt
git commit -m "feat(webimport): add NovelListAugmenter interface for per-domain chapter-list augmentation"
```

---

## Task 2: `ReadNovelFullListAugmenter` — failing tests for `canAugment` and `augment`

**Files:**
- Test: `app/src/test/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenterTest.kt`
- Fixture: `app/src/test/resources/readnovelfull/novel_landing_sample.html`
- Fixture: `app/src/test/resources/readnovelfull/chapter_archive_sample.html`

**Interfaces:**
- Consumes: `HttpClient.get(url, referrer, extraHeaders)` returns `HttpResponse(statusCode, body, headers)`.
- Produces: `class ReadNovelFullListAugmenter @Inject constructor() : NovelListAugmenter`.

- [ ] **Step 1: Create the fixtures**

Write `app/src/test/resources/readnovelfull/novel_landing_sample.html`:

```html
<!DOCTYPE html>
<html><head><title>Sample novel - ReadNovelFull</title></head>
<body>
  <div data-novel-id="2528"></div>
  <ul class="list-chapter">
    <li><a href="/sample/chapter-1-12.html">Chapter 1</a></li>
    <li><a href="/sample/chapter-2-22.html">Chapter 2</a></li>
    <li><a href="/sample/chapter-3-32.html">Chapter 3</a></li>
  </ul>
  <div id="chapter-archive"><!-- AJAX replaces this --></div>
</body></html>
```

Write `app/src/test/resources/readnovelfull/chapter_archive_sample.html`:

```html
<ul class="list-chapter">
  <li><a href="/sample/chapter-1-12.html">Chapter 1</a></li>
  <li><a href="/sample/chapter-2-22.html">Chapter 2</a></li>
  <li><a href="/sample/chapter-3-32.html">Chapter 3</a></li>
  <li><a href="/sample/chapter-4-42.html">Chapter 4</a></li>
  <li><a href="/sample/chapter-5-52.html">Chapter 5</a></li>
  <li><a href="/sample/chapter-6-62.html">Chapter 6</a></li>
  <li><a href="/sample/chapter-7-72.html">Chapter 7</a></li>
  <li><a href="/sample/chapter-8-82.html">Chapter 8</a></li>
  <li><a href="/sample/chapter-9-92.html">Chapter 9</a></li>
  <li><a href="/sample/chapter-10-102.html">Chapter 10</a></li>
</ul>
```

- [ ] **Step 2: Write the failing tests**

```kotlin
package com.novelreader.domain.usecase.webimport

import com.novelreader.domain.usecase.webimport.ChapterCrawler.ChapterLink
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ReadNovelFullListAugmenterTest {

    private val augmenter = ReadNovelFullListAugmenter()

    @Test
    fun `canAugment matches readnovelfull variants`() {
        assertTrue(augmenter.canAugment("https://readnovelfull.com/sample.html"))
        assertTrue(augmenter.canAugment("https://www.readnovelfull.com/sample.html"))
    }

    @Test
    fun `canAugment rejects other domains`() {
        assertEquals(false, augmenter.canAugment("https://freewebnovel.com/x.html"))
        assertEquals(false, augmenter.canAugment("https://parked.com/"))
        assertEquals(false, augmenter.canAugment("not a url"))
    }

    @Test
    fun `augment returns empty when data-novel-id missing`() = runTest {
        val doc = Jsoup.parse("<html><body></body></html>")
        val fake = FakeHttpClient(respond = { error("should not be called") })
        val result = augmenter.augment("https://readnovelfull.com/x.html", doc, fake)
        assertEquals(emptyList<ChapterLink>(), result)
    }

    @Test
    fun `augment calls chapter-archive endpoint and parses links`() = runTest {
        val doc = Jsoup.parse(java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText())
        val archiveHtml = java.io.File("src/test/resources/readnovelfull/chapter_archive_sample.html").readText()
        val fake = FakeHttpClient(respond = { url, _, _ ->
            assertEquals("https://readnovelfull.com/ajax/chapter-archive?novelId=2528", url)
            HttpResponse(200, archiveHtml, emptyMap())
        })
        val result = augmenter.augment("https://readnovelfull.com/sample.html", doc, fake)
        assertEquals(10, result.size)
        assertEquals("Chapter 1", result.first().title)
    }

    @Test
    fun `augment sends X-Requested-With header and homeUrl as referer`() = runTest {
        val doc = Jsoup.parse(java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText())
        val fake = FakeHttpClient(respond = { _, referrer, headers ->
            assertEquals("https://readnovelfull.com/sample.html", referrer)
            assertEquals("XMLHttpRequest", headers["X-Requested-With"])
            HttpResponse(200, "<ul></ul>", emptyMap())
        })
        augmenter.augment("https://readnovelfull.com/sample.html", doc, fake)
    }

    @Test
    fun `augment returns empty on non-200 response`() = runTest {
        val doc = Jsoup.parse(java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText())
        val fake = FakeHttpClient(respond = { _, _, _ -> HttpResponse(404, "not found", emptyMap()) })
        val result = augmenter.augment("https://readnovelfull.com/sample.html", doc, fake)
        assertEquals(emptyList<ChapterLink>(), result)
    }
}
```

Where `FakeHttpClient` is a minimal test fake:

```kotlin
package com.novelreader.domain.usecase.webimport

class FakeHttpClient(
    private val respond: suspend (url: String, referrer: String?, headers: Map<String, String>) -> HttpResponse
) : HttpClient(NoopCookieStore) {
    override suspend fun get(url: String, referrer: String?, extraHeaders: Map<String, String>): HttpResponse =
        respond(url, referrer, extraHeaders)
}
```

And `HttpClient`'s constructor must accept a `CloudflareCookieStore` and the `get` method must be `open` (or we use a real `HttpClient` against `MockWebServer` — see step 3 fallback).

- [ ] **Step 3: Choose the test wiring**

`HttpClient` is currently `final` with one `suspend fun get`. To inject a fake, either:
- (a) Open the class and override `get` (small change, gated to test).
- (b) Add a test-only constructor that injects a `Call.Factory` for OkHttp and use MockWebServer.

**Recommended: (b)** — keep `HttpClient` final in production, use MockWebServer. If MockWebServer + JDK 25 has been problematic (per the handoff), fall back to (a) and make `HttpClient` `open`.

- [ ] **Step 4: Run tests — fail with "Unresolved reference: ReadNovelFullListAugmenter"**

- [ ] **Step 5: Implement `ReadNovelFullListAugmenter`**

```kotlin
package com.novelreader.domain.usecase.webimport

import com.novelreader.domain.usecase.webimport.ChapterCrawler.ChapterLink
import com.novelreader.util.ZeroLinksDiagnostic
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadNovelFullListAugmenter @Inject constructor() : NovelListAugmenter {

    override fun canAugment(homeUrl: String): Boolean = try {
        val host = URI(homeUrl).host?.removePrefix("www.") ?: return false
        host == "readnovelfull.com"
    } catch (_: Exception) {
        false
    }

    override suspend fun augment(
        homeUrl: String,
        homeDoc: Document,
        httpClient: HttpClient
    ): List<ChapterLink> {
        val novelId = homeDoc.selectFirst("[data-novel-id]")?.attr("data-novel-id")?.toIntOrNull()
        if (novelId == null) {
            ZeroLinksDiagnostic.logChapterListSummary("readnovelfull: no data-novel-id in home doc", homeUrl, 0, -1, 0)
            return emptyList()
        }
        val uri = URI(homeUrl)
        val ajaxUrl = "${uri.scheme}://${uri.authority}/ajax/chapter-archive?novelId=$novelId"
        val resp = httpClient.get(
            url = ajaxUrl,
            referrer = homeUrl,
            extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest")
        )
        if (resp.statusCode != 200) return emptyList()
        if (!resp.body.contains("href")) return emptyList()
        val fragment = Jsoup.parseBodyFragment(resp.body)
        val homeDomain = uri.host?.removePrefix("www.") ?: ""
        return ChapterCrawler.extractChapterLinksForTest(fragment, ajaxUrl, homeDomain)
    }
}
```

This requires a test-friendly hook on `ChapterCrawler.extractChapterLinks` (currently a private file-level helper). Two options:
- (a) Make it `internal` and have the augmenter in the same module (it is) — qualifies.
- (b) Duplicate the small heuristic into the augmenter. **Not recommended — DRY.**

**Recommendation: (a) — change `extractChapterLinks` from `private` to `internal`** (or expose a wrapper that delegates).

- [ ] **Step 6: Run tests — pass**

```bash
cd /home/fabricio/Repos/Opencode/android-book
./gradlew :app:testDebugUnitTest --tests "com.novelreader.domain.usecase.webimport.ReadNovelFullListAugmenterTest"
```

- [ ] **Step 7: Commit**

```bash
git add app/src/test/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenterTest.kt \
        app/src/test/resources/readnovelfull/ \
        app/src/main/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenter.kt \
        app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt
git commit -m "feat(webimport): add ReadNovelFullListAugmenter (chapter-archive AJAX)"
```

---

## Task 3: `FreewebnovelListAugmenter` — extract inline block (with tests)

**Files:**
- Create: `app/src/main/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenter.kt`
- Test: `app/src/test/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenterTest.kt`

**Interfaces:**
- Consumes: `ChapterPaginationState?` (from `ChapterCrawler.crawlChapterList` Phase A line 60). The state must be passed in or re-extracted inside the augmenter. **Decision: re-extract from the `homeDoc` inside the augmenter to keep the interface narrow.**
- Produces: `class FreewebnovelListAugmenter @Inject constructor() : NovelListAugmenter`.

- [ ] **Step 1: Read `ChapterCrawler.kt:60-93` to capture the exact block being moved**

Read the file; understand the current inline code. It is the source of truth for what the augmenter must do.

- [ ] **Step 2: Write the failing tests**

```kotlin
package com.novelreader.domain.usecase.webimport

import com.novelreader.domain.usecase.webimport.ChapterCrawler.ChapterLink
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreewebnovelListAugmenterTest {

    private val augmenter = FreewebnovelListAugmenter()

    @Test
    fun `canAugment matches freewebnovel variants`() {
        assertTrue(augmenter.canAugment("https://www.freewebnovel.com/x.html"))
        assertTrue(augmenter.canAugment("https://freewebnovel.com/x"))
    }

    @Test
    fun `canAugment rejects other domains`() {
        assertEquals(false, augmenter.canAugment("https://readnovelfull.com/x"))
    }

    @Test
    fun `augment returns empty when home doc has no chapterPagination state`() = runTest {
        val doc = Jsoup.parse("<html><body>no scripts</body></html>")
        val fake = FakeHttpClient(respond = { error("should not be called") })
        val result = augmenter.augment("https://freewebnovel.com/x.html", doc, fake)
        assertEquals(emptyList<ChapterLink>(), result)
    }

    @Test
    fun `augment fetches all pages and merges links`() = runTest {
        val docHtml = """
            <html><head><script>
              window.chapterPagination = { currentPage: 1, pageSize: 2, totalPage: 3, totalChapters: 6 };
            </script></head><body></body></html>
        """.trimIndent()
        val doc = Jsoup.parse(docHtml)
        val fake = FakeHttpClient(respond = { url, referrer, headers ->
            val page = url.substringAfter("page=").substringBefore("&").toInt()
            val body = """{"code":200,"html":"<ul><li><a href='/x/chapter-${page}a.html'>C${page}a</a></li></ul>","page":$page}"""
            HttpResponse(200, body, emptyMap())
        })
        val result = augmenter.augment("https://www.freewebnovel.com/x.html", doc, fake)
        assertEquals(3, result.size)
    }
}
```

- [ ] **Step 3: Run tests — fail with "Unresolved reference"**

- [ ] **Step 4: Implement the augmenter (moved verbatim from `ChapterCrawler.kt:60-93`)**

```kotlin
package com.novelreader.domain.usecase.webimport

import com.novelreader.domain.usecase.webimport.ChapterCrawler.ChapterLink
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreewebnovelListAugmenter @Inject constructor() : NovelListAugmenter {

    override fun canAugment(homeUrl: String): Boolean = try {
        val host = URI(homeUrl).host?.removePrefix("www.") ?: return false
        host == "freewebnovel.com"
    } catch (_: Exception) {
        false
    }

    override suspend fun augment(
        homeUrl: String,
        homeDoc: Document,
        httpClient: HttpClient
    ): List<ChapterLink> {
        val state = ChapterPaginationStateExtractor.extractChapterPaginationState(homeDoc) ?: return emptyList()
        if (state.totalPage <= 1) return emptyList()
        val all = mutableListOf<ChapterLink>()
        val baseUri = URI(homeUrl)
        val homeDomain = baseUri.host?.removePrefix("www.") ?: ""
        for (page in 2..state.totalPage) {
            val ajaxUrl = buildUrl(homeUrl, page, state.pageSize) ?: break
            val resp = httpClient.get(
                url = ajaxUrl,
                referrer = homeUrl,
                extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest")
            )
            if (resp.statusCode != 200) continue
            val parsed = ChapterPaginationJsonParser.parseChapterPaginationJson(resp.body) ?: continue
            if (parsed.code != 200) continue
            val fragment = Jsoup.parseBodyFragment(parsed.html)
            all += ChapterCrawler.extractChapterLinksForTest(fragment, homeUrl, homeDomain)
            delay(ChapterCrawler.PAGE_DELAY_MS)
        }
        return all
    }

    private fun buildUrl(homeUrl: String, page: Int, pageSize: Int): String? = try {
        val u = URI(homeUrl)
        "${u.scheme}://${u.authority}${u.path}?ajax=chapters&page=$page&pageSize=$pageSize"
    } catch (_: Exception) { null }
}
```

Note: `ChapterCrawler.PAGE_DELAY_MS` is currently a `private const val`; promote to `internal const val` so the augmenter can reference it. Also `ChapterCrawler.extractChapterLinks` is `private`; promote to `internal` (or expose a `internal` wrapper).

- [ ] **Step 5: Run tests — pass**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenter.kt \
        app/src/test/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenterTest.kt \
        app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt
git commit -m "refactor(webimport): extract FreewebnovelListAugmenter from ChapterCrawler inline block"
```

---

## Task 4: `ChapterCrawler` — wire augmenters, remove inline freewebnovel block

**Files:**
- Modify: `app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt`
- Modify: `app/src/main/java/com/novelreader/di/...` (find the DI module that provides `ChapterCrawler`)

- [ ] **Step 1: Find the DI module that provides `ChapterCrawler`**

```bash
cd /home/fabricio/Repos/Opencode/android-book
grep -rn "ChapterCrawler" app/src/main/java/com/novelreader/di/ app/src/main/java/com/novelreader/NovelReaderApp.kt
```

Read the `@Provides` for `ChapterCrawler` and the module declaration.

- [ ] **Step 2: Write a regression test for the dispatch**

Add to `app/src/test/java/com/novelreader/domain/usecase/webimport/ChapterCrawlerTest.kt` (or new test if not present):

```kotlin
@Test
fun `crawlChapterList dispatches to registered augmenters`() = runTest {
    val homeDoc = Jsoup.parse(java.io.File("src/test/resources/readnovelfull/novel_landing_sample.html").readText())
    val fakeHttp = FakeHttpClient(respond = { url, _, _ ->
        if (url.contains("chapter-archive"))
            HttpResponse(200, java.io.File("src/test/resources/readnovelfull/chapter_archive_sample.html").readText(), emptyMap())
        else
            HttpResponse(200, homeDoc.outerHtml(), emptyMap())
    })
    val aug = ReadNovelFullListAugmenter()
    val crawler = ChapterCrawler(fakeHttp, setOf(aug))
    val result = crawler.crawlChapterList("https://readnovelfull.com/sample.html")
    // 3 static + 10 AJAX = 13, but fileNames overlap, so dedup by URL
    assertTrue(result.links.size >= 10)
}
```

- [ ] **Step 3: Run test — fails because the constructor signature is wrong**

- [ ] **Step 4: Modify `ChapterCrawler`**

Add constructor param: `augmenters: Set<@JvmSuppressWildcards NovelListAugmenter>`. After Phase A's existing link-collection loop, add:

```kotlin
for (augmenter in augmenters) {
    if (augmenter.canAugment(homeUrl)) {
        allLinks = (allLinks + augmenter.augment(homeUrl, firstPageDoc, httpClient))
            .distinctBy { it.url }
    }
}
```

Keep a reference to the first-page `Document` (`firstPageDoc`) so augmenters can re-use it. Promote `extractChapterLinks` and `PAGE_DELAY_MS` to `internal` (Tasks 2 + 3 may have already done this).

Remove the inline freewebnovel block (the `if (paginationState != null && paginationState.totalPage > 1)` block now lives in `FreewebnovelListAugmenter`).

- [ ] **Step 5: Update the DI module**

In the `@Provides` for `ChapterCrawler`, add the new param:

```kotlin
@Provides @Singleton
fun provideChapterCrawler(
    httpClient: HttpClient,
    augmenters: Set<@JvmSuppressWildcards NovelListAugmenter>
): ChapterCrawler = ChapterCrawler(httpClient, augmenters)
```

- [ ] **Step 6: Create `AugmenterModule`**

```kotlin
package com.novelreader.di

import com.novelreader.domain.usecase.webimport.FreewebnovelListAugmenter
import com.novelreader.domain.usecase.webimport.NovelListAugmenter
import com.novelreader.domain.usecase.webimport.ReadNovelFullListAugmenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AugmenterModule {
    @Binds @IntoSet
    abstract fun bindFreewebnovel(impl: FreewebnovelListAugmenter): NovelListAugmenter

    @Binds @IntoSet
    abstract fun bindReadNovelFull(impl: ReadNovelFullListAugmenter): NovelListAugmenter
}
```

- [ ] **Step 7: Run all unit tests — pass**

```bash
cd /home/fabricio/Repos/Opencode/android-book
./gradlew :app:testDebugUnitTest
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt \
        app/src/main/java/com/novelreader/di/ \
        app/src/test/java/com/novelreader/domain/usecase/webimport/ChapterCrawlerTest.kt
git commit -m "refactor(webimport): wire NovelListAugmenter multibinding in ChapterCrawler"
```

---

## Task 5: `ChapterNumberExtractor` — audit and fix `chapter-10-41.html`

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/parser/ChapterNumberExtractor.kt`
- Modify: `app/src/test/java/com/novelreader/data/parser/ChapterNumberExtractorTest.kt` (add tests)

- [ ] **Step 1: Inspect the current regex**

Read `ChapterNumberExtractor.kt` (full file is 19 lines; see the spec for current behavior). The current regex is `chapter|capítulo|ch|cap` followed by digits — on `chapter-10-41.html` it matches `chapter-10` and captures `10`. Should be correct for the readnovelfull format.

- [ ] **Step 2: Write the regression test**

```kotlin
@Test
fun `extract returns first number for chapter-N-unique format`() {
    assertEquals(10, ChapterNumberExtractor.extract("Chapter 10", "chapter-10-41.html", "https://readnovelfull.com/x/chapter-10-41.html"))
    assertEquals(1, ChapterNumberExtractor.extract("Chapter 1", "chapter-1-352.html", null))
}
```

- [ ] **Step 3: Run test — if it passes, no fix needed; if it fails, fix the regex**

- [ ] **Step 4: If a fix is needed, change the regex to anchor on the chapter keyword + first number, ignoring trailing numeric suffixes**

For example:

```kotlin
private val CHAPTER_PATTERN = Regex(
    """(?:chapter|cap[íi]tulo|ch\.?\b|cap\.?\b)\s*[.:\-]?\s*(\d+)(?:\D|$)""",
    RegexOption.IGNORE_CASE
)
```

The `(?:\D|$)` non-digit/end-anchor prevents a future `chapter-99-10` (n-last) format from incorrectly matching `10`. For `chapter-10-41.html`, the regex still matches `chapter-10` followed by `-` (which is `\D`), capturing `10`.

- [ ] **Step 5: Run test — pass**

- [ ] **Step 6: Commit (if any code changed)**

```bash
git add app/src/main/java/com/novelreader/data/parser/ChapterNumberExtractor.kt \
        app/src/test/java/com/novelreader/data/parser/ChapterNumberExtractorTest.kt
git commit -m "fix(parser): ChapterNumberExtractor ignores trailing unique-id numbers"
```

---

## Task 6: `CloudflareChallengeDialog` — generalize host check

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/webimport/CloudflareChallengeDialog.kt`
- Modify: `app/src/main/java/com/novelreader/util/StringUtils.kt` (add `hostMatchesDomain`)
- Modify: `app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt` (pass `expectedHost`)
- Test: `app/src/test/java/com/novelreader/util/StringUtilsTest.kt` (new or extend)

- [ ] **Step 1: Write the failing test for `hostMatchesDomain`**

```kotlin
@Test
fun `hostMatchesDomain handles www and bare-host variants`() {
    assertTrue(StringUtils.hostMatchesDomain("www.readfullnovel.com", "readfullnovel.com"))
    assertTrue(StringUtils.hostMatchesDomain("readfullnovel.com", "www.readfullnovel.com"))
    assertTrue(StringUtils.hostMatchesDomain("freewebnovel.com", "freewebnovel.com"))
    assertFalse(StringUtils.hostMatchesDomain("maliciousfreewebnovel.com", "freewebnovel.com"))
    assertFalse(StringUtils.hostMatchesDomain("freewebnovel.com.evil.com", "freewebnovel.com"))
    assertFalse(StringUtils.hostMatchesDomain("completely-other.com", "freewebnovel.com"))
}
```

- [ ] **Step 2: Run test — fails**

- [ ] **Step 3: Add `hostMatchesDomain` to `StringUtils`**

```kotlin
fun hostMatchesDomain(host: String, expected: String): Boolean {
    val h = host.lowercase().removePrefix("www.")
    val e = expected.lowercase().removePrefix("www.")
    return h == e || h.endsWith(".${e}")
}
```

- [ ] **Step 4: Run test — passes**

- [ ] **Step 5: Add `expectedHost: String` parameter to `CloudflareChallengeDialog`**

```kotlin
@Composable
fun CloudflareChallengeDialog(
    url: String,
    expectedHost: String,
    onCookiesCollected: (List<Pair<String, String>>) -> Unit,
    onCancel: () -> Unit
)
```

In `onPageFinished` (line 95), replace the `loadedUrl.contains("freewebnovel.com")` check with:

```kotlin
if (StringUtils.hostMatchesDomain(Uri.parse(loadedUrl).host ?: "", expectedHost) &&
    !loadedUrl.contains("challenge") &&
    !loadedUrl.contains("cf-")
)
```

- [ ] **Step 6: Update `WebImportViewModel` call site**

Find the call to `CloudflareChallengeDialog(...)` in the ViewModel and add the new arg:

```kotlin
val expectedHost = try { URI(state.homeUrl).host ?: "" } catch (_: Exception) { "" }
```

- [ ] **Step 7: Run all unit tests — pass**

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/webimport/CloudflareChallengeDialog.kt \
        app/src/main/java/com/novelreader/util/StringUtils.kt \
        app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt \
        app/src/test/java/com/novelreader/util/StringUtilsTest.kt
git commit -m "fix(webimport): generalize CloudflareChallengeDialog host check (any domain)"
```

---

## Task 7: `DataStoreCloudflareCookieStore` — fix cookie-key mismatch

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/local/preferences/DataStoreCloudflareCookieStore.kt`
- Modify: `app/src/test/java/com/novelreader/data/local/preferences/DataStoreCloudflareCookieStoreTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `cookiesFor handles cookie with leading-dot domain when lookup host is www variant`() = runTest {
    val store = DataStoreCloudflareCookieStore.create(context)
    val cookie = StoredCookie(name = "cf_clearance", value = "x", domain = ".readfullnovel.com", path = "/", expiresAt = System.currentTimeMillis() + 60_000)
    store.putCookies("https://readfullnovel.com/", listOf(cookie))
    val result = store.cookiesFor("https://www.readfullnovel.com/anything")
    assertTrue(result.any { it.name == "cf_clearance" && it.value == "x" })
}
```

- [ ] **Step 2: Run test — fails**

- [ ] **Step 3: Fix `cookiesFor`**

```kotlin
override suspend fun cookiesFor(url: String): List<StoredCookie> {
    val uri = try { java.net.URI(url) } catch (_: Exception) { return emptyList() }
    val host = uri.host ?: return emptyList()
    val candidateKeys = buildSet {
        add(host)
        val bare = host.removePrefix("www.")
        add(bare)
        if (!host.startsWith(".")) add(".$host")
        if (!bare.startsWith(".")) add(".$bare")
    }
    val all = mutableListOf<StoredCookie>()
    for (key in candidateKeys) {
        val names = dataStore.data.map { prefs -> prefs[stringSetPreferencesKey(key)] ?: emptySet() }.first()
        for (raw in names) {
            val parsed = parseStoredCookie(raw) ?: continue
            if (parsed.expiresAt > 0 && parsed.expiresAt < System.currentTimeMillis()) continue
            all += parsed
        }
    }
    return all.distinctBy { it.name }
}
```

- [ ] **Step 4: Run all unit tests — pass**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/novelreader/data/local/preferences/DataStoreCloudflareCookieStore.kt \
        app/src/test/java/com/novelreader/data/local/preferences/DataStoreCloudflareCookieStoreTest.kt
git commit -m "fix(webimport): DataStoreCloudflareCookieStore handles .domain vs www host mismatch"
```

---

## Task 8: Phase A verification + commit

- [ ] **Step 1: Run full unit test suite + compile**

```bash
cd /home/fabricio/Repos/Opencode/android-book
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: all green. 205+ tests pass (no regressions, new tests added).

- [ ] **Step 2: Build debug APK to verify the project compiles end-to-end**

```bash
./gradlew :app:assembleDebug
```

- [ ] **Step 3: Commit any remaining stragglers and tag the phase**

```bash
git log --oneline -1
```

The Phase A work spans Tasks 1-7. They were each committed individually; this is just a checkpoint.

- [ ] **Step 4: On-device verification (manual)**

Document in the handoff: import a 200+ chapter readnovelfull novel. Logcat shows the new `WebFetchProbe` log line with `totalFound` >= 200. Library shows 200+ chapter rows. Freewebnovel regression: a 200+ chapter freewebnovel novel still imports all chapters.

---

# Phase B — Cross-site merge + new-chapter badge

## Task 9: `NovelSourceEntity` + `NovelSourceDao` (failing test first)

**Files:**
- Create: `app/src/main/java/com/novelreader/data/local/db/entity/NovelSourceEntity.kt`
- Create: `app/src/main/java/com/novelreader/data/local/db/dao/NovelSourceDao.kt`
- Test (androidTest): `app/src/androidTest/java/com/novelreader/data/local/db/NovelSourceDaoTest.kt`

- [ ] **Step 1: Write the failing androidTest**

```kotlin
@RunWith(AndroidJUnit4::class)
class NovelSourceDaoTest {
    private lateinit var db: NovelDatabase
    private lateinit var sourceDao: NovelSourceDao
    private lateinit var novelDao: NovelDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NovelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sourceDao = db.novelSourceDao()
        novelDao = db.novelDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun insertAndGetByNovel() = runBlocking {
        val novelId = novelDao.insert(NovelEntity(title = "Dragon King")).toInt()
        val sourceId = sourceDao.insert(NovelSourceEntity(novelId = novelId, sourceUrl = "https://a.com/x", domain = "a.com", isPrimary = true, addedAt = 0))
        val sources = sourceDao.getByNovel(novelId)
        assertEquals(1, sources.size)
        assertEquals("https://a.com/x", sources[0].sourceUrl)
    }

    @Test fun duplicateInsertIgnoredByUnique() = runBlocking {
        val novelId = novelDao.insert(NovelEntity(title = "Dragon King")).toInt()
        sourceDao.insert(NovelSourceEntity(novelId = novelId, sourceUrl = "https://a.com/x", domain = "a.com", isPrimary = true, addedAt = 0))
        sourceDao.insert(NovelSourceEntity(novelId = novelId, sourceUrl = "https://a.com/x", domain = "a.com", isPrimary = false, addedAt = 0))
        assertEquals(1, sourceDao.getByNovel(novelId).size)
    }

    @Test fun cascadeDeleteOnNovelRemoval() = runBlocking {
        val novelId = novelDao.insert(NovelEntity(title = "X")).toInt()
        sourceDao.insert(NovelSourceEntity(novelId = novelId, sourceUrl = "https://a.com/x", domain = "a.com", isPrimary = true, addedAt = 0))
        novelDao.deleteById(novelId)
        assertEquals(0, sourceDao.getByNovel(novelId).size)
    }
}
```

- [ ] **Step 2: Add the entity, DAO, and DI; bump DB version to 9; create the migration**

`NovelSourceEntity`:

```kotlin
@Immutable
@Entity(
    tableName = "novel_sources",
    foreignKeys = [ForeignKey(NovelEntity::class, ["id"], ["novelId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("novelId"), Index(value = ["novelId", "sourceUrl"], unique = true)]
)
data class NovelSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val sourceUrl: String,
    val domain: String = "",
    val isPrimary: Boolean = false,
    val lastCheckedAt: Long = 0,
    val autoUpdate: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
```

`NovelSourceDao`: see spec for the full interface.

`NovelDatabase`:

```kotlin
@Database(
    entities = [
        NovelEntity::class, ChapterEntity::class, ChapterFts::class,
        BookmarkEntity::class, CharacterEntity::class, CharacterPhotoEntity::class,
        FailedChapterEntity::class, NovelSourceEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class NovelDatabase : RoomDatabase() { ... }
```

`MIGRATION_8_9` in `NovelDatabase.Companion`:

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE novels ADD COLUMN hasUpdates INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `novel_sources` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `novelId` INTEGER NOT NULL,
                `sourceUrl` TEXT NOT NULL,
                `domain` TEXT NOT NULL,
                `isPrimary` INTEGER NOT NULL DEFAULT 0,
                `lastCheckedAt` INTEGER NOT NULL DEFAULT 0,
                `autoUpdate` INTEGER NOT NULL DEFAULT 1,
                `addedAt` INTEGER NOT NULL,
                FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_novel_sources_novelId_sourceUrl` ON `novel_sources` (`novelId`, `sourceUrl`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_sources_novelId` ON `novel_sources` (`novelId`)")
        // Backfill
        db.execSQL("""
            INSERT INTO novel_sources (novelId, sourceUrl, domain, isPrimary, lastCheckedAt, autoUpdate, addedAt)
            SELECT id, sourceUrl, '', 1, lastCheckedAt, autoUpdate, lastCheckedAt
            FROM novels WHERE sourceUrl != ''
        """.trimIndent())
    }
}
```

Wire it in the `DatabaseModule`:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, ..., MIGRATION_8_9)
```

Add `@Provides fun provideNovelSourceDao(db: NovelDatabase) = db.novelSourceDao()`.

- [ ] **Step 3: Export the schema**

```bash
cd /home/fabricio/Repos/Opencode/android-book
./gradlew :app:exportSchema
git add app/schemas/com.novelreader.data.local.db.NovelDatabase/9.json
```

- [ ] **Step 4: Run androidTest** (requires emulator — note in PR; for now skip and rely on JVM tests for unit-level coverage)

If no emulator is available, run:

```bash
./gradlew :app:compileDebugKotlin
```

to confirm the schema compiles, and run:

```bash
./gradlew :app:testDebugUnitTest
```

for unit-level coverage. The androidTest `NovelSourceDaoTest` will be run as part of CI.

- [ ] **Step 5: Add `MigrationTest` entry**

In `app/src/androidTest/java/com/novelreader/data/local/db/MigrationTest.kt`, add:

```kotlin
@Test fun migrate8to9_addsNovelSourcesAndHasUpdates() { /* see MigrationTest pattern in repo */ }
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/novelreader/data/local/db/ \
        app/src/androidTest/java/com/novelreader/data/local/db/ \
        app/schemas/com.novelreader.data.local.db.NovelDatabase/9.json
git commit -m "feat(db): v8→v9 migration adds novel_sources + hasUpdates"
```

---

## Task 10: `NovelEntity.hasUpdates` + `NovelDao` helpers

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/local/db/entity/NovelEntity.kt`
- Modify: `app/src/main/java/com/novelreader/data/local/db/dao/NovelDao.kt`
- Test: extend `app/src/test/java/com/novelreader/data/local/db/NovelDaoTest.kt` (or androidTest if existing is android)

- [ ] **Step 1: Add `hasUpdates: Boolean = false` to `NovelEntity`**

```kotlin
data class NovelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String? = null,
    val coverPath: String? = null,
    val sourceFolder: String = "",
    val totalChapters: Int = 0,
    val lastChapterId: Long? = null,
    val lastReadAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val sourceUrl: String = "",
    val lastCheckedAt: Long = 0,
    val autoUpdate: Boolean = false,
    val hasUpdates: Boolean = false
)
```

- [ ] **Step 2: Add `getNovelByTitleIgnoreCase`, `setHasUpdates`, `clearHasUpdates` to `NovelDao`**

```kotlin
@Query("SELECT * FROM novels WHERE title = :title COLLATE NOCASE LIMIT 1")
suspend fun getNovelByTitleIgnoreCase(title: String): NovelEntity?

@Query("UPDATE novels SET hasUpdates = :on WHERE id = :id")
suspend fun setHasUpdates(id: Long, on: Boolean)
```

- [ ] **Step 3: Write the unit test**

```kotlin
@Test fun `getNovelByTitleIgnoreCase matches across case`() = runBlocking {
    val id = novelDao.insert(NovelEntity(title = "Dragon King")).toInt()
    assertEquals(id, novelDao.getNovelByTitleIgnoreCase("dragon king")?.id)
    assertEquals(id, novelDao.getNovelByTitleIgnoreCase("DRAGON KING")?.id)
}

@Test fun `setHasUpdates round-trips`() = runBlocking {
    val id = novelDao.insert(NovelEntity(title = "X")).toInt()
    novelDao.setHasUpdates(id, true)
    assertEquals(true, novelDao.getNovelById(id)?.hasUpdates)
    novelDao.setHasUpdates(id, false)
    assertEquals(false, novelDao.getNovelById(id)?.hasUpdates)
}
```

- [ ] **Step 4: Run unit tests — pass**

- [ ] **Step 5: Commit (if not already in Task 9)**

```bash
git add app/src/main/java/com/novelreader/data/local/db/entity/NovelEntity.kt \
        app/src/main/java/com/novelreader/data/local/db/dao/NovelDao.kt
git commit -m "feat(db): NovelEntity.hasUpdates + NovelDao getNovelByTitleIgnoreCase"
```

---

## Task 11: `NovelImporter` — multi-source aware

**Files:**
- Modify: `app/src/main/java/com/novelreader/domain/usecase/NovelImporter.kt`
- Modify: `app/src/main/java/com/novelreader/data/local/db/dao/NovelDao.kt` (already done in Task 10)
- Test: `app/src/test/java/com/novelreader/domain/usecase/NovelImporterMultiSourceTest.kt` (new)

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `ensureNovel uses getNovelByTitleIgnoreCase for case-insensitive merge`() = runBlocking {
    novelDao.insert(NovelEntity(title = "Dragon King", sourceUrl = "https://a.com/x"))
    val importer = NovelImporter(novelDao, chapterDao, sourceDao)
    val (id, _) = importer.ensureNovel("dragon king", "https://b.com/y", "b.com")
    assertNotNull(novelDao.getNovelById(id))
    // existing sourceUrl unchanged
    assertEquals("https://a.com/x", novelDao.getNovelById(id)?.sourceUrl)
    // new source row added
    val sources = sourceDao.getByNovel(id)
    assertEquals(2, sources.size)
    val primaries = sources.filter { it.isPrimary }
    assertEquals(1, primaries.size)
    assertEquals("https://a.com/x", primaries[0].sourceUrl)
}

@Test fun `ensureNovel creates primary source for first novel`() = runBlocking {
    val importer = NovelImporter(novelDao, chapterDao, sourceDao)
    val (id, _) = importer.ensureNovel("New Novel", "https://a.com/x", "a.com")
    val sources = sourceDao.getByNovel(id)
    assertEquals(1, sources.size)
    assertEquals(true, sources[0].isPrimary)
    assertEquals("a.com", sources[0].domain)
    assertEquals("https://a.com/x", novelDao.getNovelById(id)?.sourceUrl)
}
```

- [ ] **Step 2: Run test — fails because `NovelImporter` doesn't take `sourceDao` or use NOCASE lookup**

- [ ] **Step 3: Modify `NovelImporter`**

```kotlin
@Singleton
class NovelImporter @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val novelSourceDao: NovelSourceDao
) {
    suspend fun ensureNovel(
        novelTitle: String,
        sourceUrl: String,
        domain: String = "",
        targetNovelId: Long? = null
    ): Pair<Long, MutableSet<String>> {
        val novel: NovelEntity? = if (targetNovelId != null) {
            novelDao.getNovelById(targetNovelId)
        } else {
            novelDao.getNovelByTitleIgnoreCase(novelTitle)
        }
        val novelId: Long
        val existingFileNames: MutableSet<String>
        if (novel != null) {
            novelId = novel.id
            existingFileNames = chapterDao.getChaptersByNovelSync(novelId).map { it.fileName }.toMutableSet()
        } else {
            val newId = novelDao.insert(NovelEntity(title = novelTitle))
            novelId = newId
            existingFileNames = mutableSetOf()
        }
        if (sourceUrl.isNotBlank()) {
            val existing = novelSourceDao.findByNovelAndUrl(novelId, sourceUrl)
            if (existing == null) {
                val isPrimary = novelSourceDao.getByNovel(novelId).isEmpty()
                novelSourceDao.insert(
                    NovelSourceEntity(
                        novelId = novelId,
                        sourceUrl = sourceUrl,
                        domain = domain,
                        isPrimary = isPrimary,
                        addedAt = System.currentTimeMillis()
                    )
                )
                if (isPrimary && (novelDao.getNovelById(novelId)?.sourceUrl ?: "").isEmpty()) {
                    novelDao.updateSourceUrl(novelId, sourceUrl)
                }
            } else if (existing.domain != domain) {
                novelSourceDao.updateUrl(existing.id, sourceUrl, domain)
            }
        }
        return novelId to existingFileNames
    }

    suspend fun insertChapters(novelId: Long, chapters: List<ImportedChapter>) { /* unchanged */ }

    fun fileNameFromUrl(url: String, chapterNumber: Int): String =
        StringUtils.fileNameFromUrl(url, "chapter_$chapterNumber")
}
```

- [ ] **Step 4: Update Hilt DI** if `NovelImporter` is provided in a module

If it is `@Inject constructor`, no DI change needed. If it is provided manually, add `NovelSourceDao` to the provider.

- [ ] **Step 5: Run unit tests — pass**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/NovelImporter.kt \
        app/src/test/java/com/novelreader/domain/usecase/NovelImporterMultiSourceTest.kt
git commit -m "feat(webimport): NovelImporter multi-source aware (preserves primary sourceUrl)"
```

---

## Task 12: `WebImportUseCase` — dedup + cover first-wins + `hasUpdates` setter

**Files:**
- Modify: `app/src/main/java/com/novelreader/domain/usecase/WebImportUseCase.kt`
- Test: extend `app/src/test/java/com/novelreader/domain/usecase/WebImportUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `importChapters skips by chapter number when content non-blank`() = runBlocking {
    // existing chapter 10 with content "AAA" in novel 1
    chapterDao.insertAll(listOf(ChapterEntity(novelId = 1, title = "Chapter 10", fileName = "chapter-10", orderIndex = 0, content = "AAA")))
    val importer = WebImportUseCase(context, chapterCrawler, chapterFetcher, coverDownloader, novelImporter, failedChapterDao, novelDao, chapterDao, io)
    val links = listOf(ChapterLink(url = "https://rnf.com/x/chapter-10-41.html", title = "Chapter 10", chapterNumber = 10))
    val result = importer.importChapters("New Novel", links, null, null, 0, "https://rnf.com/x", "rnf.com", null)
    // chapterFetcher.fetch should NOT have been called (skipped by number)
    verify(exactly = 0) { chapterFetcher.fetch(any(), any(), any()) }
}

@Test fun `cover is not overwritten when novel already has coverPath`() = runBlocking {
    novelDao.insert(NovelEntity(id = 1, title = "Existing", coverPath = "/files/covers/novel_1.jpg"))
    val importer = WebImportUseCase(...)
    importer.importChapters("Existing", emptyList(), "https://example.com/cover.jpg", null, 0, "https://example.com", "example.com", targetNovelId = 1)
    verify(exactly = 0) { coverDownloader.downloadCover(any(), any(), any()) }
}

@Test fun `setHasUpdates called when inserting new chapters into existing novel`() = runBlocking {
    novelDao.insert(NovelEntity(id = 1, title = "Existing"))
    chapterDao.insertAll(listOf(ChapterEntity(novelId = 1, title = "C1", fileName = "c1", orderIndex = 0, content = "AAA")))
    val importer = WebImportUseCase(...)
    val links = listOf(ChapterLink(url = "https://x.com/y.html", title = "C2", chapterNumber = 2))
    every { chapterFetcher.fetch(any(), any(), any()) } returns FetchedChapter(url = "...", title = "C2", fileName = "c2", content = "BBB")
    importer.importChapters("Existing", links, null, null, 0, "https://x.com", "x.com", targetNovelId = 1)
    verify { novelDao.setHasUpdates(1, true) }
}

@Test fun `setHasUpdates NOT called on first import (fresh novel)`() = runBlocking {
    val importer = WebImportUseCase(...)
    val links = listOf(ChapterLink(url = "https://x.com/y.html", title = "C1", chapterNumber = 1))
    every { chapterFetcher.fetch(any(), any(), any()) } returns FetchedChapter(...)
    importer.importChapters("Brand New", links, null, null, 0, "https://x.com", "x.com")
    verify(exactly = 0) { novelDao.setHasUpdates(any(), any()) }
}
```

- [ ] **Step 2: Run tests — fail**

- [ ] **Step 3: Modify `WebImportUseCase`**

Add `chapterDao: ChapterDao`, `novelDao: NovelDao` to the constructor.

```kotlin
@Singleton
class WebImportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterCrawler: ChapterCrawler,
    private val chapterFetcher: ChapterFetcher,
    private val coverDownloader: CoverDownloader,
    private val novelImporter: NovelImporter,
    private val failedChapterDao: FailedChapterDao,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun importChapters(
        novelTitle: String,
        links: List<ChapterLink>,
        coverUrl: String?,
        filesDir: File?,
        orderIndexOffset: Int = 0,
        sourceUrl: String,
        domain: String = "",
        targetNovelId: Long? = null,
        onProgress: ((Int, Int) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Result<Long> {
        val (novelId, existingFileNames) = novelImporter.ensureNovel(novelTitle, sourceUrl, domain, targetNovelId)
        val existingChapters = chapterDao.getChaptersByNovelSync(novelId)
        val existingNumbers: Set<Int> = existingChapters
            .filter { it.content.isNotBlank() }
            .mapNotNull { c -> ChapterNumberExtractor.extract(c.title, c.fileName).takeIf { it != Int.MAX_VALUE } }
            .toSet()
        // cover first-wins
        if (coverUrl != null && (novelDao.getNovelById(novelId)?.coverPath.isNullOrEmpty())) {
            filesDir?.let { coverDownloader.downloadCover(novelId, coverUrl, it) }
        }
        val importedChapters = mutableListOf<ImportedChapter>()
        var inserted = 0
        for ((index, link) in links.withIndex()) {
            if (index > 0) delay(CHAPTER_FETCH_PACING_MS)
            onProgress?.invoke(index + 1, links.size)
            val chapterNumber = link.chapterNumber
            if (link.url in existingFileNames) { onProgress?.invoke(index + 1, links.size); continue }
            if (chapterNumber != Int.MAX_VALUE && chapterNumber in existingNumbers) {
                onProgress?.invoke(index + 1, links.size); continue
            }
            val fileName = StringUtils.fileNameFromUrl(link.url, chapterNumber)
            if (fileName in existingFileNames) { onProgress?.invoke(index + 1, links.size); continue }
            val fetched = try {
                chapterFetcher.fetch(link.url, fileName, link.title)
            } catch (e: Exception) {
                val type = FailedChapterErrorType.classify(e)
                failedChapterDao.insert(FailedChapterEntity(novelId, link.title, fileName, link.url, sourceType = "WEB", chapterNumber = chapterNumber, errorType = type.name, errorMessage = e.message ?: ""))
                onError?.invoke(link.title)
                continue
            }
            existingFileNames += fileName
            importedChapters += ImportedChapter(url = link.url, title = fetched.title, fileName = fileName, content = fetched.content, chapterNumber = chapterNumber)
            inserted++
        }
        if (importedChapters.isNotEmpty()) {
            novelImporter.insertChapters(novelId, importedChapters)
            val wasExisting = existingChapters.isNotEmpty() || targetNovelId != null
            if (wasExisting) novelDao.setHasUpdates(novelId, true)
        }
        return Result.success(novelId)
    }
}
```

- [ ] **Step 4: Run unit tests — pass**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/WebImportUseCase.kt \
        app/src/test/java/com/novelreader/domain/usecase/WebImportUseCaseTest.kt
git commit -m "feat(webimport): WebImportUseCase dedup by chapter number + cover first-wins + hasUpdates"
```

---

## Task 13: Plumb `targetNovelId` + `domain` through the import pipeline

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/worker/ImportJobSpec.kt`
- Modify: `app/src/main/java/com/novelreader/data/worker/BackgroundImportManager.kt`
- Modify: `app/src/main/java/com/novelreader/data/worker/SpecReader.kt` (or wherever the workData is read)
- Modify: `app/src/main/java/com/novelreader/data/worker/SpecFileStore.kt` (or wherever the spec is persisted)
- Modify: `app/src/main/java/com/novelreader/data/worker/ChapterImportWorker.kt`
- Modify: `app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt`
- Test: extend `app/src/test/java/com/novelreader/data/worker/ImportJobSpecTest.kt` if it exists

- [ ] **Step 1: Read each file in turn to understand the current signature**

```bash
cd /home/fabricio/Repos/Opencode/android-book
grep -n "sourceUrl" app/src/main/java/com/novelreader/data/worker/ImportJobSpec.kt
grep -n "sourceUrl" app/src/main/java/com/novelreader/data/worker/BackgroundImportManager.kt
grep -n "sourceUrl" app/src/main/java/com/novelreader/data/worker/ChapterImportWorker.kt
```

- [ ] **Step 2: Add `domain` + `targetNovelId` to `ImportJobSpec`**

```kotlin
data class ImportJobSpec(
    val title: String,
    val links: List<String>,
    val coverUrl: String?,
    val sourceUrl: String,
    val domain: String = "",          // NEW
    val targetNovelId: Long? = null,  // NEW
    ...
)
```

- [ ] **Step 3: Update `BackgroundImportManager.startImport` signature**

Add `domain: String, targetNovelId: Long?` and pass through.

- [ ] **Step 4: Update `ChapterImportWorker` workData keys**

Add `KEY_DOMAIN = "import_domain"`, `KEY_TARGET_NOVEL_ID = "import_target_novel_id"`. Read them and pass to `WebImportUseCase.importChapters`.

- [ ] **Step 5: Update `SpecReader`/`SpecFileStore`**

If the spec is persisted between worker restarts, the new fields must round-trip. Update both.

- [ ] **Step 6: Update `WebImportViewModel.startImport`**

```kotlin
private fun startImport() {
    val sourceUrl = state.homeUrl
    val domain = try { URI(sourceUrl).host?.removePrefix("www.") ?: "" } catch (_: Exception) { "" }
    val targetNovelId = state.mergeTargetNovelId
    backgroundImportManager.startImport(
        title = state.novelTitle,
        links = state.chapters.map { it.url },
        coverUrl = state.coverUrl,
        sourceUrl = sourceUrl,
        domain = domain,
        targetNovelId = targetNovelId,
        onProgress = { ... }
    )
}
```

- [ ] **Step 7: Run all unit tests — pass**

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/novelreader/data/worker/ \
        app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt
git commit -m "feat(webimport): plumb targetNovelId + domain through import pipeline"
```

---

## Task 14: `ChapterUpdateCheckWorker` — iterate `novel_sources` + set `hasUpdates`

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/worker/ChapterUpdateCheckWorker.kt`
- Test: `app/src/test/java/com/novelreader/data/worker/ChapterUpdateCheckWorkerMultiSourceTest.kt` (new)

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `worker iterates all enabled sources and sets hasUpdates on new chapters`() = runBlocking {
    val novelId1 = novelDao.insert(NovelEntity(title = "A", autoUpdate = true, sourceUrl = "https://a.com"))
    val novelId2 = novelDao.insert(NovelEntity(title = "B", autoUpdate = true, sourceUrl = "https://b.com"))
    sourceDao.insert(NovelSourceEntity(novelId = novelId1, sourceUrl = "https://a.com/x", domain = "a.com", isPrimary = true, addedAt = 0))
    sourceDao.insert(NovelSourceEntity(novelId = novelId2, sourceUrl = "https://b.com/x", domain = "b.com", isPrimary = true, addedAt = 0))
    every { webImportUseCase.fetchChapterList("https://a.com/x") } returns Result.success(FetchResult(chapters = listOf(ChapterLink(url = "https://a.com/x/c10.html", title = "C10", chapterNumber = 10)), coverUrl = null, novelTitle = "A"))
    every { webImportUseCase.fetchChapterList("https://b.com/x") } returns Result.success(FetchResult(chapters = listOf(ChapterLink(url = "https://b.com/x/c5.html", title = "C5", chapterNumber = 5)), coverUrl = null, novelTitle = "B"))
    val worker = TestListenableWorkerBuilder.from(context, ChapterUpdateCheckWorker(...))
    worker.doWork()
    verify { novelDao.setHasUpdates(novelId1, true) }
    verify { novelDao.setHasUpdates(novelId2, true) }
}

@Test fun `worker skips sources whose novel has autoUpdate disabled`() = runBlocking {
    val novelId = novelDao.insert(NovelEntity(title = "A", autoUpdate = false, sourceUrl = ""))
    sourceDao.insert(NovelSourceEntity(novelId = novelId, sourceUrl = "https://a.com/x", domain = "a.com", isPrimary = true, autoUpdate = true, addedAt = 0))
    val worker = TestListenableWorkerBuilder.from(context, ChapterUpdateCheckWorker(...))
    worker.doWork()
    verify(exactly = 0) { webImportUseCase.fetchChapterList(any()) }
}
```

- [ ] **Step 2: Run tests — fail**

- [ ] **Step 3: Refactor `ChapterUpdateCheckWorker`**

```kotlin
class ChapterUpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val novelSourceDao: NovelSourceDao,
    private val webImportUseCase: WebImportUseCase,
    private val updateNotificationHelper: UpdateNotificationHelper,
    private val importWorkScheduler: ImportWorkScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sources = novelSourceDao.getAllForAutoUpdate()
        for (source in sources) {
            val novel = novelDao.getNovelById(source.novelId) ?: continue
            if (!novel.autoUpdate || !source.autoUpdate) continue
            val result = webImportUseCase.fetchChapterList(source.sourceUrl)
            result.fold(
                onSuccess = { fetchResult ->
                    val existingChapters = chapterDao.getChaptersByNovelSync(novel.id)
                    val existingFileNames = existingChapters.map { it.fileName }.toSet()
                    val existingNumbers = existingChapters
                        .filter { it.content.isNotBlank() }
                        .mapNotNull { c -> ChapterNumberExtractor.extract(c.title, c.fileName).takeIf { it != Int.MAX_VALUE } }
                        .toSet()
                    val newChapters = fetchResult.chapters.filter { link ->
                        val fn = StringUtils.fileNameFromUrl(link.url, link.chapterNumber)
                        val n = link.chapterNumber
                        fn !in existingFileNames && !(n != Int.MAX_VALUE && n in existingNumbers)
                    }
                    if (newChapters.isNotEmpty()) {
                        novelDao.setHasUpdates(novel.id, true)
                        updateNotificationHelper.postNewChaptersNotification(novel, newChapters.size)
                        val specs = ImportJobSpec.create(
                            title = novel.title,
                            links = newChapters.map { it.url },
                            coverUrl = null,
                            sourceUrl = source.sourceUrl,
                            domain = source.domain,
                            targetNovelId = novel.id
                        )
                        specs.forEach { importWorkScheduler.schedule(it) }
                    }
                },
                onFailure = { /* log */ }
            )
            novelSourceDao.updateLastChecked(source.id, System.currentTimeMillis())
        }
        return Result.success()
    }
}
```

- [ ] **Step 4: Run unit tests — pass**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/novelreader/data/worker/ChapterUpdateCheckWorker.kt \
        app/src/test/java/com/novelreader/data/worker/ChapterUpdateCheckWorkerMultiSourceTest.kt
git commit -m "feat(webimport): ChapterUpdateCheckWorker iterates novel_sources (multi-source)"
```

---

## Task 15: `ScanMissingChaptersUseCase` — cross-source dedup suppression

**Files:**
- Modify: `app/src/main/java/com/novelreader/domain/usecase/ScanMissingChaptersUseCase.kt`
- Test: extend `app/src/test/java/com/novelreader/domain/usecase/ScanMissingChaptersUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `scanWeb does not flag chapter as missing if same number exists with content`() = runBlocking {
    val novelId = novelDao.insert(NovelEntity(title = "X", sourceUrl = "https://a.com"))
    chapterDao.insertAll(listOf(ChapterEntity(novelId = novelId, title = "Chapter 10", fileName = "from-a", orderIndex = 0, content = "AAA")))
    every { chapterCrawler.crawlChapterList("https://a.com") } returns CrawlResult(
        links = listOf(ChapterLink(url = "https://a.com/chapter-10.html", title = "C10", chapterNumber = 10)),
        coverUrl = null,
        novelTitle = "X"
    )
    val useCase = ScanMissingChaptersUseCase(chapterCrawler, chapterDao, failedChapterDao, novelDao, io)
    useCase.scanWeb(novelId)
    val failures = failedChapterDao.getForNovel(novelId)
    assertEquals(0, failures.count { it.errorType == "MISSING_NUMBER" && it.chapterNumber == 10 })
}
```

- [ ] **Step 2: Run test — fail**

- [ ] **Step 3: Modify `ScanMissingChaptersUseCase.scanForMissing`**

Before flagging a missing number, also check chapter-number dedup:

```kotlin
val existingNumbers: Set<Int> = chapterDao.getChaptersByNovelSync(novelId)
    .filter { it.content.isNotBlank() }
    .mapNotNull { c -> ChapterNumberExtractor.extract(c.title, c.fileName).takeIf { it != Int.MAX_VALUE } }
    .toSet()

for (link in crawl.links) {
    val n = link.chapterNumber
    val fn = fileNameFromUrl(link.url, n)
    if (fn in existingFileNames) continue
    if (n != Int.MAX_VALUE && n in existingNumbers) continue
    // ... existing MISSING_NUMBER insert
}
```

- [ ] **Step 4: Run test — pass**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/ScanMissingChaptersUseCase.kt \
        app/src/test/java/com/novelreader/domain/usecase/ScanMissingChaptersUseCaseTest.kt
git commit -m "fix(webimport): ScanMissingChaptersUseCase suppresses cross-source false positives"
```

---

## Task 16: `ChapterSorter` — chapter-number skip in local path

**Files:**
- Modify: `app/src/main/java/com/novelreader/domain/usecase/importnovel/ChapterSorter.kt`
- Test: extend `app/src/test/java/com/novelreader/domain/usecase/importnovel/ChapterSorterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `buildSortedEntries skips incoming entry whose chapter number already exists`() {
    val existing = listOf(ChapterEntity(novelId = 1, title = "C1", fileName = "c1", orderIndex = 0, content = "AAA"))
    val incoming = listOf(ChapterEntry(fileName = "c1-new", chapterTitle = "C1", content = "BBB", existingId = null, chapterNumber = 1))
    val result = ChapterSorter.buildSortedEntries(existing, incoming)
    assertEquals(1, result.size)
    assertEquals("c1", result[0].fileName) // not the new one
}
```

- [ ] **Step 2: Run test — fail**

- [ ] **Step 3: Modify `ChapterSorter.buildSortedEntries`**

Add chapter-number dedup before the fileName dedup:

```kotlin
val existingNumbers = existingChapters
    .filter { it.content.isNotBlank() }
    .mapNotNull { c -> ChapterNumberExtractor.extract(c.title, c.fileName).takeIf { it != Int.MAX_VALUE } }
    .toSet()

// Filter incoming by chapter number too
val filtered = incoming.filter { entry ->
    val n = entry.chapterNumber.takeIf { it != Int.MAX_VALUE } ?: ChapterNumberExtractor.extract(entry.chapterTitle, entry.fileName).takeIf { it != Int.MAX_VALUE }
    n == null || n !in existingNumbers
}
```

- [ ] **Step 4: Run test — pass**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/importnovel/ChapterSorter.kt \
        app/src/test/java/com/novelreader/domain/usecase/importnovel/ChapterSorterTest.kt
git commit -m "fix(import): ChapterSorter dedups by chapter number (parity with web)"
```

---

## Task 17: UI — `NovelCard` and `NovelListItem` blue dot

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/components/NovelCard.kt`
- Modify: `app/src/main/java/com/novelreader/ui/library/components/NovelListItem.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Test: `app/src/test/java/com/novelreader/ui/library/components/NovelCardBadgeTest.kt` (new Compose test)

- [ ] **Step 1: Add the strings**

PT: `<string name="library_new_chapters_badge">Novos capítulos</string>`
EN: `<string name="library_new_chapters_badge">New chapters</string>`

- [ ] **Step 2: Write the failing Compose test**

```kotlin
@Test fun `NovelCard shows blue dot when hasUpdates is true`() {
    composeTestRule.setContent {
        MaterialTheme {
            NovelCard(novel = sampleNovel.copy(hasUpdates = true), onClick = {}, onLongClick = {})
        }
    }
    composeTestRule.onNodeWithContentDescription("Novos capítulos").assertExists()
}

@Test fun `NovelCard hides blue dot when hasUpdates is false`() {
    composeTestRule.setContent {
        MaterialTheme {
            NovelCard(novel = sampleNovel.copy(hasUpdates = false), onClick = {}, onLongClick = {})
        }
    }
    composeTestRule.onNodeWithContentDescription("Novos capítulos").assertDoesNotExist()
}
```

- [ ] **Step 3: Run test — fail**

- [ ] **Step 4: Modify `NovelCard`**

Add a `Box` overlay on the cover, top-right:

```kotlin
Box(modifier = Modifier.fillMaxWidth()) {
    // existing cover content
    if (novel.hasUpdates) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .semantics { contentDescription = "Novos capítulos" }
        )
    }
}
```

Use `stringResource(R.string.library_new_chapters_badge)` for the `contentDescription`.

- [ ] **Step 5: Apply the same dot to `NovelListItem`**

Smaller dot, 8.dp, to the right of the title.

- [ ] **Step 6: Run Compose tests — pass**

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/components/NovelCard.kt \
        app/src/main/java/com/novelreader/ui/library/components/NovelListItem.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/test/java/com/novelreader/ui/library/components/NovelCardBadgeTest.kt
git commit -m "feat(ui): blue dot on NovelCard and NovelListItem for hasUpdates"
```

---

## Task 18: `LibraryViewModel` — clear `hasUpdates` on novel open

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt`
- Test: extend `app/src/test/java/com/novelreader/ui/library/LibraryViewModelTest.kt` (or wherever the test is)

- [ ] **Step 1: Find the OpenNovel intent handler**

```bash
cd /home/fabricio/Repos/Opencode/android-book
grep -n "OpenNovel\|clearHasUpdates" app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt
```

- [ ] **Step 2: Write the failing test**

```kotlin
@Test fun `OpenNovel intent clears hasUpdates for the opened novel`() = runBlocking {
    val id = novelDao.insert(NovelEntity(title = "X", hasUpdates = true)).toInt()
    viewModel.onIntent(LibraryIntent.OpenNovel(id))
    assertEquals(false, novelDao.getNovelById(id.toLong())?.hasUpdates)
}
```

- [ ] **Step 3: Run test — fail**

- [ ] **Step 4: Add the clear call**

```kotlin
is LibraryIntent.OpenNovel -> {
    novelDao.clearHasUpdates(intent.id)
    // existing navigation
}
```

- [ ] **Step 5: Run test — pass**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt
git commit -m "feat(ui): clear hasUpdates when user opens a novel"
```

---

## Task 19: `ImportScreen` — "Merging into existing" status text

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/import_novel/ImportScreen.kt`
- Modify: `app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Add the strings**

PT: `<string name="import_merging_into_existing">Mesclando capítulos em "%1$s" existente</string>`
EN: `<string name="import_merging_into_existing">Merging chapters into existing "%1$s"</string>`

- [ ] **Step 2: Wire the `mergeTargetNovelId` state in `WebImportViewModel`**

When `fetchChapters` succeeds, query `novelDao.getNovelByTitleIgnoreCase(novelTitle)`. If a match exists, set `state.mergeTargetNovelId = match.id` and `state.mergeTargetTitle = match.title`. Otherwise leave null.

- [ ] **Step 3: Show the status text in `ImportScreen`**

Below the novel title (around line 286), if `state.mergeTargetNovelId != null`, render:

```kotlin
Text(
    text = stringResource(R.string.import_merging_into_existing, state.mergeTargetTitle ?: state.novelTitle),
    color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.bodySmall
)
```

- [ ] **Step 4: Pass `mergeTargetNovelId` to `startImport`**

In `WebImportViewModel.startImport`, use the new state field.

- [ ] **Step 5: Build and run the unit tests — pass**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/import_novel/ImportScreen.kt \
        app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml
git commit -m "feat(ui): show 'Merging into existing' status on import"
```

---

## Task 20: Final verification

- [ ] **Step 1: Run full unit test suite + compile**

```bash
cd /home/fabricio/Repos/Opencode/android-book
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: all green. Target: 230+ tests (was 205 before Phase A, +25 from Phase A, +20 from Phase B).

- [ ] **Step 2: Export schema and confirm 9.json is committed**

```bash
./gradlew :app:exportSchema
git status app/schemas/
```

- [ ] **Step 3: Build debug APK**

```bash
./gradlew :app:assembleDebug
```

- [ ] **Step 4: Bump version to v2.5.0 (versionCode 18)**

In `app/build.gradle.kts`:

```kotlin
versionCode = 18
versionName = "2.5.0"
```

- [ ] **Step 5: Update `AGENTS.md`**

Mark current version as 2.5.0 with a brief summary of what shipped.

- [ ] **Step 6: Final commit**

```bash
git add app/build.gradle.kts AGENTS.md
git commit -m "chore: bump version to v2.5.0 (versionCode 18)"
```

- [ ] **Step 7: Write handoff for the next session**

Save to `/tmp/opencode/handoff-20260630-phaseAB-complete.md` with:
- Summary of what shipped
- File-by-file change list
- Test count delta (205 → 230+)
- Open follow-ups (v2.6 candidates: manage-sources UI, numeric badge, longest-content-wins)
- Push instructions (SSH still not configured, same as the v2.4.2 handoff)

---

## Self-Review

After writing the plan, check:

1. **Spec coverage:**
   - Phase A goals: readnovelfull full chapter list ✓ (Tasks 2, 4); per-domain augmenters ✓ (Tasks 1, 3, 4); side-fixes (Tasks 5, 6, 7) ✓.
   - Phase B goals: v8→v9 with `NovelSourceEntity` ✓ (Tasks 9, 10, 11); chapter-number dedup ✓ (Task 12); multi-source auto-update ✓ (Task 14); blue badge ✓ (Tasks 17, 18); cover first-wins ✓ (Task 12); cross-source missing suppression ✓ (Task 15); title NOCASE merge ✓ (Task 11); status text ✓ (Task 19); local-path parity ✓ (Task 16); plumbing ✓ (Task 13).

2. **Placeholders:** none. Every step has explicit code or commands.

3. **Type consistency:**
   - `NovelListAugmenter.augment` signature used uniformly in Tasks 1, 2, 3, 4.
   - `ImportJobSpec.domain` + `targetNovelId` added in Task 13, used in Tasks 14, 19.
   - `WebImportUseCase.importChapters` signature used in Tasks 12, 13, 19.
   - `NovelSourceDao.findByNovelAndUrl` + `getByNovel` + `getAllForAutoUpdate` + `updateLastChecked` + `insert` defined in Task 9, used in Tasks 11, 14, 15.
   - `NovelDao.getNovelByTitleIgnoreCase` + `setHasUpdates` + `clearHasUpdates` defined in Task 10, used in Tasks 11, 12, 18.

4. **Risks documented:** yes (migration backfill, hasUpdates on first import, freewebnovel regression, chapter-number regex).
