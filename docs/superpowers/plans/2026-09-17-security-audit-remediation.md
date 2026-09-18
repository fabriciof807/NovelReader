# Security Audit Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remediate the four open Medium findings in `piolium/final-audit-report.md` by centralizing outbound policy, pinning page-derived destinations, and bounding remote-controlled work.

**Architecture:** Extend the existing `HttpClient` into the single production remote-read boundary with explicit destination policies, pre-contact redirect validation, and per-request byte limits. Pass an independently derived novel host through crawler and Cloudflare flows, route raw cover/character egress through the hardened client, and share a 50-request budget across chapter-list crawling and augmenters.

**Tech Stack:** Kotlin 2.2.10, Android SDK 26+, coroutines, OkHttp 4.12.0, Jsoup 1.23.2, Hilt, JUnit 4, Robolectric, MockK, MockWebServer, Truth.

**Spec:** `docs/superpowers/specs/2026-09-17-security-audit-remediation-design.md`

## Global Constraints

- Production remote reads require HTTPS.
- `SameNovelDomain` uses the host independently derived from the user-entered novel URL; never derive it from a challenge, redirect, or page-discovered URL.
- Page-discovered next pages, chapters, covers, and Cloudflare challenges stay on the novel domain.
- Manual cover URLs and MVLEMPYR character images may use any public HTTPS host.
- `PublicOnlyDns` remains installed on the production OkHttp client.
- Redirects are validated before the destination request and stop after five hops.
- Chapter-list crawling and all augmenters share one 50-request budget.
- FreeWebNovel accepts `pageSize` only in `1..200`, stops after three consecutive failures, and stores at most 10,000 augmented links.
- MVLEMPYR reads 100 entries per page, at most 50 pages/5,000 entries.
- No new dependency, repository layer, or networking package migration.
- Preserve the pre-existing untracked `.agents/` and `AGENTS.md.bak-1788753396` paths.

## File Structure

**Create**

- `app/src/main/java/com/novelreader/domain/usecase/webimport/RemoteRequestPolicy.kt` — destination authorization and URL validation.
- `app/src/main/java/com/novelreader/domain/usecase/webimport/RequestBudget.kt` — shared bounded request counter.
- `app/src/test/java/com/novelreader/ui/webimport/WebImportViewModelTest.kt` — independent challenge-host propagation.
- `app/src/test/java/com/novelreader/data/storage/CoverStorageTest.kt` — hardened cover download behavior.
- `app/src/test/java/com/novelreader/data/remote/MvlempyrCharacterImporterTest.kt` — bounded character import egress.

**Modify**

- `app/src/main/java/com/novelreader/domain/usecase/webimport/HttpClient.kt` — manual redirects, policies, and request-specific limits.
- `app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt` — same-domain discovery and shared budget.
- `app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterFetcher.kt` — same-domain redirect policy.
- `app/src/main/java/com/novelreader/domain/usecase/webimport/NovelListAugmenter.kt` — explicit budget parameter.
- `app/src/main/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenter.kt` — consume budget and pin AJAX egress.
- `app/src/main/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenter.kt` — bounded pagination, pacing, failures, and links.
- `app/src/main/java/com/novelreader/domain/usecase/webimport/CoverDownloader.kt` — same-domain policy for discovered covers.
- `app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt` — carry independent expected challenge host.
- `app/src/main/java/com/novelreader/ui/import_novel/ImportScreen.kt` — stop deriving host from challenge URL.
- `app/src/main/java/com/novelreader/ui/webimport/CloudflareChallengeDialog.kt` — navigation/content/cookie hardening.
- `app/src/main/java/com/novelreader/data/storage/CoverStorage.kt` — replace raw URL egress.
- `app/src/main/java/com/novelreader/data/remote/MvlempyrCharacterImporter.kt` — hardened page/API/image reads and bounded pagination.
- Existing tests under `app/src/test/java/com/novelreader/domain/usecase/webimport/` — policy, redirect, crawler, fetcher, augmenter, and downloader regressions.
- `docs/security-residual-risks.md` and `AGENTS.md` — accurate post-remediation controls and test count.

---

### Task 1: Make `HttpClient` the enforceable outbound boundary

**Files:**
- Create: `app/src/main/java/com/novelreader/domain/usecase/webimport/RemoteRequestPolicy.kt`
- Modify: `app/src/main/java/com/novelreader/domain/usecase/webimport/HttpClient.kt`
- Test: `app/src/test/java/com/novelreader/domain/usecase/webimport/HttpClientTest.kt`

**Interfaces:**
- Produces: `sealed interface RemoteRequestPolicy` with `AnyPublicHttps` and `SameNovelDomain(expectedHost: String)`.
- Produces: `HttpClient.get(url, referrer, extraHeaders, policy, maxBodyBytes, maxDecompressedBytes): HttpResponse`.
- Produces: `RemoteRequestRejectedException` for scheme, host, redirect, and policy failures.
- Later tasks consume these exact policy types and named arguments.

- [ ] **Step 1: Add failing policy and redirect tests**

Add tests that make the required behavior explicit:

```kotlin
@Test
fun sameNovelDomain_rejectsForeignHostsAndCleartext() {
    val policy = RemoteRequestPolicy.SameNovelDomain("freewebnovel.com")

    assertThat(policy.allows("https://www.freewebnovel.com/chapter")).isTrue()
    assertThat(policy.allows("https://cdn.freewebnovel.com/chapter")).isTrue()
    assertThat(policy.allows("https://evil.example/chapter")).isFalse()
    assertThat(policy.allows("http://freewebnovel.com/chapter")).isFalse()
}

@Test
fun get_rejectsCrossDomainRedirectBeforeContactingTarget() = runBlocking {
    val foreign = MockWebServer().apply { start() }
    try {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "http://evil.example:${foreign.port}/landing")
        )
        val guarded = testClient(
            hosts = mapOf(
                "novel.example" to InetAddress.getByName("127.0.0.1"),
                "evil.example" to InetAddress.getByName("127.0.0.1")
            )
        )

        assertThrows(RemoteRequestRejectedException::class.java) {
            runBlocking {
                guarded.get(
                    "http://novel.example:${server.port}/start",
                    policy = RemoteRequestPolicy.SameNovelDomain("novel.example")
                )
            }
        }
        assertThat(foreign.requestCount).isEqualTo(0)
    } finally {
        foreign.shutdown()
    }
}

@Test
fun get_stopsAfterFiveRedirects() = runBlocking<Unit> {
    repeat(6) { index ->
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "/hop-${index + 1}")
        )
    }

    assertThrows(RemoteRequestRejectedException::class.java) {
        runBlocking { client.get("http://127.0.0.1:${server.port}/start") }
    }
    assertThat(server.requestCount).isEqualTo(6)
}
```

Retain the existing oversized-body test and change its response to chunked transfer so it proves actual-byte counting rather than header trust:

```kotlin
server.enqueue(
    MockResponse()
        .setChunkedBody("x".repeat(4096), 128)
        .setResponseCode(200)
)
```

Use a private `testClient(hosts)` helper whose injected OkHttp DNS maps the named hosts to loopback. The existing visible-for-testing constructor is the only path that permits cleartext test URLs.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.domain.usecase.webimport.HttpClientTest"
```

Expected: compilation fails because `RemoteRequestPolicy`, the new `policy` argument, and `RemoteRequestRejectedException` do not exist.

- [ ] **Step 3: Implement destination policies**

Create `RemoteRequestPolicy.kt`:

```kotlin
package com.novelreader.domain.usecase.webimport

import com.novelreader.util.StringUtils
import java.net.URI

sealed interface RemoteRequestPolicy {
    fun allows(url: String): Boolean

    data object AnyPublicHttps : RemoteRequestPolicy {
        override fun allows(url: String): Boolean = httpsHost(url) != null
    }

    data class SameNovelDomain(val expectedHost: String) : RemoteRequestPolicy {
        override fun allows(url: String): Boolean {
            val host = httpsHost(url) ?: return false
            return StringUtils.hostMatchesDomain(host, expectedHost)
        }
    }
}

class RemoteRequestRejectedException(message: String) : SecurityException(message)

private fun httpsHost(url: String): String? = try {
    val uri = URI(url)
    if (uri.scheme?.lowercase() != "https") null else uri.host
} catch (_: Exception) {
    null
}
```

Add an internal validation overload used only by the test-injected client so MockWebServer can remain HTTP without weakening production policy:

```kotlin
internal fun RemoteRequestPolicy.allows(url: String, allowCleartextForTests: Boolean): Boolean {
    if (!allowCleartextForTests) return allows(url)
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host ?: return false
    return when (this) {
        RemoteRequestPolicy.AnyPublicHttps -> uri.scheme in setOf("http", "https")
        is RemoteRequestPolicy.SameNovelDomain ->
            uri.scheme in setOf("http", "https") && StringUtils.hostMatchesDomain(host, expectedHost)
    }
}
```

- [ ] **Step 4: Implement pre-contact manual redirects and per-request limits**

In `HttpClient.kt`:

- force both OkHttp clients to `followRedirects(false)` and `followSslRedirects(false)`;
- set `allowCleartextForTests = true` only in the visible-for-testing constructor;
- validate the initial URL and each resolved `Location` before `newCall`;
- cap redirect hops at five;
- pass request-specific limits to `readBounded` and decompression helpers.

Use this public shape:

```kotlin
suspend fun get(
    url: String,
    referrer: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
    policy: RemoteRequestPolicy = RemoteRequestPolicy.AnyPublicHttps,
    maxBodyBytes: Int = this.maxBodyBytes,
    maxDecompressedBytes: Int = this.maxDecompressedBytes
): HttpResponse
```

Use a loop whose redirect branch closes the response before continuing:

```kotlin
var currentUrl = url
var redirects = 0
while (true) {
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
        continue
    }
    return readResponse(response, maxBodyBytes, maxDecompressedBytes)
}
```

Keep cookie handling, headers, compression decoding, and `finalUrl` behavior intact.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.domain.usecase.webimport.HttpClientTest" --tests "com.novelreader.util.PublicOnlyDnsTest"
```

Expected: all selected tests pass; the foreign redirect server receives zero requests.

- [ ] **Step 6: Commit the outbound boundary**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/webimport/RemoteRequestPolicy.kt \
  app/src/main/java/com/novelreader/domain/usecase/webimport/HttpClient.kt \
  app/src/test/java/com/novelreader/domain/usecase/webimport/HttpClientTest.kt
git commit -m "fix(security): validate outbound destinations and redirects"
```

---

### Task 2: Carry an independent Cloudflare challenge host and harden navigation

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt`
- Modify: `app/src/main/java/com/novelreader/ui/import_novel/ImportScreen.kt`
- Modify: `app/src/main/java/com/novelreader/ui/webimport/CloudflareChallengeDialog.kt`
- Modify: `app/src/main/java/com/novelreader/util/CloudflareChallengePolicy.kt`
- Create: `app/src/test/java/com/novelreader/ui/webimport/WebImportViewModelTest.kt`
- Modify: `app/src/test/java/com/novelreader/util/CloudflareChallengePolicyTest.kt`

**Interfaces:**
- Produces: `CloudflareChallenge(url: String, expectedHost: String)`.
- Consumes: `RemoteRequestPolicy.SameNovelDomain` semantics through the existing `CloudflareChallengePolicy` host check.
- `ImportScreen` passes `challenge.expectedHost` unchanged.

- [ ] **Step 1: Write the failing ViewModel challenge-host test**

Create `WebImportViewModelTest.kt` with an unconfined Main dispatcher, `ApplicationProvider`, and relaxed mocks. Configure `BackgroundImportManager.state` as `MutableStateFlow(BackgroundImportState())`, then add:

```kotlin
@Test
fun `challenge expectation comes from the typed source url`() = runTest {
    val sourceUrl = "https://freewebnovel.com/novel/one"
    val foreignChallenge = "https://evil.example/cdn-cgi/challenge"
    coEvery { webImportUseCase.fetchChapterList(sourceUrl) } returns
        Result.failure(CloudflareChallengeRequiredException(foreignChallenge, "challenge"))

    viewModel.updateUrl(sourceUrl)
    viewModel.fetchChapters()
    advanceUntilIdle()

    assertThat(viewModel.state.value.cloudflareChallenge).isEqualTo(
        CloudflareChallenge(
            url = foreignChallenge,
            expectedHost = "freewebnovel.com"
        )
    )
}
```

Also add a malformed-source test asserting `expectedHost` is blank so the dialog fails closed.

- [ ] **Step 2: Run the ViewModel test and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.webimport.WebImportViewModelTest"
```

Expected: compilation fails because `CloudflareChallenge` has no `expectedHost`.

- [ ] **Step 3: Carry the independently derived host**

Change the model and failure branch:

```kotlin
data class CloudflareChallenge(
    val url: String,
    val expectedHost: String
)

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
```

Capture the submitted source URL at the start of `fetchChapters`, and construct:

```kotlin
cloudflareChallenge = CloudflareChallenge(
    url = e.url,
    expectedHost = hostOf(url)
)
```

In `ImportScreen.kt`, remove the `remember(challenge.url)` block and pass:

```kotlin
url = challenge.url,
expectedHost = challenge.expectedHost
```

- [ ] **Step 4: Write failing navigation-policy tests**

Extend `CloudflareChallengePolicyTest.kt`:

```kotlin
@Test
fun `navigation rejects a foreign https host`() {
    assertThat(
        CloudflareChallengePolicy.shouldBlockNavigation(
            "https://evil.example/landing",
            "freewebnovel.com"
        )
    ).isTrue()
}

@Test
fun `navigation permits the expected host`() {
    assertThat(
        CloudflareChallengePolicy.shouldBlockNavigation(
            "https://www.freewebnovel.com/landing",
            "freewebnovel.com"
        )
    ).isFalse()
}
```

- [ ] **Step 5: Run policy tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.util.CloudflareChallengePolicyTest"
```

Expected: compilation fails because `shouldBlockNavigation` does not exist.

- [ ] **Step 6: Harden the solver WebView**

Add the policy helper:

```kotlin
fun shouldBlockNavigation(url: String, expectedHost: String): Boolean =
    !isAllowed(url, expectedHost)
```

In `CloudflareChallengeDialog.kt`:

```kotlin
settings.allowContentAccess = false
settings.allowFileAccess = false
CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
```

Override navigation before `onPageFinished`:

```kotlin
override fun shouldOverrideUrlLoading(
    view: WebView,
    request: android.webkit.WebResourceRequest
): Boolean = CloudflareChallengePolicy.shouldBlockNavigation(
    request.url.toString(),
    expectedHost
)
```

Retain the pre-load `isAllowed` branch so `AndroidView` is not constructed when the initial URL is foreign or the expected host is blank.

- [ ] **Step 7: Run challenge tests and verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.ui.webimport.WebImportViewModelTest" \
  --tests "com.novelreader.util.CloudflareChallengePolicyTest"
```

Expected: all selected tests pass.

- [ ] **Step 8: Commit the challenge fix**

```bash
git add app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt \
  app/src/main/java/com/novelreader/ui/import_novel/ImportScreen.kt \
  app/src/main/java/com/novelreader/ui/webimport/CloudflareChallengeDialog.kt \
  app/src/main/java/com/novelreader/util/CloudflareChallengePolicy.kt \
  app/src/test/java/com/novelreader/ui/webimport/WebImportViewModelTest.kt \
  app/src/test/java/com/novelreader/util/CloudflareChallengePolicyTest.kt
git commit -m "fix(security): bind Cloudflare challenges to the source host"
```

---

### Task 3: Pin crawler, chapters, and discovered covers to the novel domain

**Files:**
- Modify: `app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt`
- Modify: `app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterFetcher.kt`
- Modify: `app/src/main/java/com/novelreader/domain/usecase/webimport/CoverDownloader.kt`
- Modify: `app/src/test/java/com/novelreader/domain/usecase/webimport/ChapterCrawlerTest.kt`
- Modify: `app/src/test/java/com/novelreader/domain/usecase/webimport/ChapterFetcherTest.kt`
- Modify: `app/src/test/java/com/novelreader/domain/usecase/webimport/CoverDownloaderTest.kt`

**Interfaces:**
- Consumes: `RemoteRequestPolicy.SameNovelDomain(expectedHost)` from Task 1.
- Produces: crawler helper `resolveSameDomain(url, baseUrl, expectedHost): String?`.
- The crawler's expected host is derived once from `homeUrl` and reused for pages, links, covers, and redirects.

- [ ] **Step 1: Add failing cross-domain discovery tests**

In `ChapterCrawlerTest.kt`, use a second MockWebServer/DNS mapping and add:

```kotlin
@Test
fun crawlChapterList_rejectsCrossDomainNextPageBeforeContact() = runBlocking {
    server.enqueue(
        MockResponse().setBody(
            """<a href="http://evil.example:${foreign.port}/page-2" rel="next">Next</a>"""
        )
    )

    val result = crawlerForHosts(server, foreign).crawlChapterList(homeUrl)

    assertThat(result.links).isEmpty()
    assertThat(foreign.requestCount).isEqualTo(0)
}

@Test
fun crawlChapterList_omitsCrossDomainDiscoveredCover() = runBlocking {
    server.enqueue(
        MockResponse().setBody(
            """<meta property="og:image" content="http://evil.example:${foreign.port}/cover.jpg">"""
        )
    )

    val result = crawlerForHosts(server, foreign).crawlChapterList(homeUrl)

    assertThat(result.coverUrl).isNull()
    assertThat(foreign.requestCount).isEqualTo(0)
}
```

Add a same-domain relative-next control that returns page 2 and proves URI resolution still works.

- [ ] **Step 2: Run crawler tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.domain.usecase.webimport.ChapterCrawlerTest"
```

Expected: the foreign next-page test observes a foreign request or policy failure after the crawler accepts the URL; the cover test returns the foreign URL.

- [ ] **Step 3: Implement same-domain URL resolution and page policy**

In `ChapterCrawler.kt`, derive:

```kotlin
val expectedHost = hostOf(homeUrl)
require(expectedHost.isNotBlank()) { "Invalid novel host" }
val policy = RemoteRequestPolicy.SameNovelDomain(expectedHost)
```

Replace string concatenation for discovered URLs with URI resolution. Preserve the crawler's existing test-only cleartext switch without weakening production:

```kotlin
internal fun resolveSameDomain(url: String, baseUrl: String, expectedHost: String): String? {
    val resolved = runCatching { URI(baseUrl).resolve(url) }.getOrNull() ?: return null
    val scheme = resolved.scheme?.lowercase()
    if (scheme !in setOf("http", "https")) return null
    if (requireHttps && scheme != "https") return null
    val host = resolved.host ?: return null
    if (!StringUtils.hostMatchesDomain(host, expectedHost)) return null
    return resolved.toString()
}
```

Use it in next-page and cover extraction. Pass `policy` to every crawler `httpClient.get`; the test-injected client from Task 1 permits cleartext only for MockWebServer. Keep the existing `makeAbsolute` entry point only while tests or callers require it, and implement it through `URI.resolve`.

- [ ] **Step 4: Pin chapter redirects and discovered cover downloads**

In `ChapterFetcher.fetchWithRetry`, derive the URL host and call:

```kotlin
httpClient.get(
    url = url,
    referrer = url.substringBeforeLast("/"),
    policy = RemoteRequestPolicy.SameNovelDomain(host)
)
```

In `CoverDownloader.downloadCover`, derive the cover host and call:

```kotlin
httpClient.get(
    url = coverUrl,
    policy = RemoteRequestPolicy.SameNovelDomain(host),
    maxBodyBytes = MAX_COVER_BYTES,
    maxDecompressedBytes = MAX_COVER_BYTES
)
```

Update `CoverDownloaderTest` to verify the named policy and 10 MiB limits. Add a `ChapterFetcherTest` case whose first server redirects to a mapped foreign server and assert the foreign request count remains zero.

- [ ] **Step 5: Run origin tests and verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.domain.usecase.webimport.ChapterCrawlerTest" \
  --tests "com.novelreader.domain.usecase.webimport.ChapterFetcherTest" \
  --tests "com.novelreader.domain.usecase.webimport.CoverDownloaderTest"
```

Expected: all selected tests pass; foreign servers receive zero requests.

- [ ] **Step 6: Commit origin pinning**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt \
  app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterFetcher.kt \
  app/src/main/java/com/novelreader/domain/usecase/webimport/CoverDownloader.kt \
  app/src/test/java/com/novelreader/domain/usecase/webimport/ChapterCrawlerTest.kt \
  app/src/test/java/com/novelreader/domain/usecase/webimport/ChapterFetcherTest.kt \
  app/src/test/java/com/novelreader/domain/usecase/webimport/CoverDownloaderTest.kt
git commit -m "fix(security): pin imported content to the novel domain"
```

---

### Task 4: Share one bounded request budget across crawling and augmentation

**Files:**
- Create: `app/src/main/java/com/novelreader/domain/usecase/webimport/RequestBudget.kt`
- Modify: `app/src/main/java/com/novelreader/domain/usecase/webimport/NovelListAugmenter.kt`
- Modify: `app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt`
- Modify: `app/src/main/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenter.kt`
- Modify: `app/src/main/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenter.kt`
- Modify: `app/src/test/java/com/novelreader/domain/usecase/webimport/ChapterCrawlerTest.kt`
- Modify: `app/src/test/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenterTest.kt`
- Modify: `app/src/test/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenterTest.kt`

**Interfaces:**
- Produces: `RequestBudget(limit: Int)` with `remaining`, `used`, and `tryConsume(): Boolean`.
- Changes: `NovelListAugmenter.augment(homeUrl, homeDoc, httpClient, requestBudget)`.
- Consumes: Task 1 request policies and Task 3 expected-host derivation.

- [ ] **Step 1: Add failing budget and hostile-pagination tests**

Add a focused budget unit inside `ChapterCrawlerTest` or a new `RequestBudgetTest`:

```kotlin
@Test
fun requestBudget_neverConsumesPastItsLimit() {
    val budget = RequestBudget(2)

    assertThat(budget.tryConsume()).isTrue()
    assertThat(budget.tryConsume()).isTrue()
    assertThat(budget.tryConsume()).isFalse()
    assertThat(budget.used).isEqualTo(2)
    assertThat(budget.remaining).isEqualTo(0)
}
```

In `FreewebnovelListAugmenterTest`, set `delayFn` to record delays without sleeping and add:

```kotlin
@Test
fun augment_capsRemoteTotalPageToTheSharedBudget() = runBlocking {
    val doc = paginationDocument(totalPage = Int.MAX_VALUE, pageSize = 40, totalChapters = Int.MAX_VALUE)
    repeat(4) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validPage(it + 2)))
    }
    val budget = RequestBudget(4)

    augmenter.augment(homeUrl, doc, client, budget)

    assertThat(server.requestCount).isEqualTo(4)
    assertThat(budget.remaining).isEqualTo(0)
}

@Test
fun augment_pacesFailuresAndStopsAfterThree() = runBlocking {
    val delays = mutableListOf<Long>()
    augmenter.delayFn = { delays += it }
    repeat(10) { server.enqueue(MockResponse().setResponseCode(500)) }

    augmenter.augment(
        homeUrl,
        paginationDocument(totalPage = 100, pageSize = 40, totalChapters = 4000),
        client,
        RequestBudget(50)
    )

    assertThat(server.requestCount).isEqualTo(3)
    assertThat(delays).containsExactly(1_500L, 1_500L, 1_500L)
}

@Test
fun augment_rejectsImplausiblePageSize() = runBlocking {
    val result = augmenter.augment(
        homeUrl,
        paginationDocument(totalPage = 2, pageSize = 15_000, totalChapters = 30_000),
        client,
        RequestBudget(50)
    )

    assertThat(result).isEmpty()
    assertThat(server.requestCount).isEqualTo(0)
}
```

- [ ] **Step 2: Run augmenter tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.domain.usecase.webimport.FreewebnovelListAugmenterTest" \
  --tests "com.novelreader.domain.usecase.webimport.ChapterCrawlerTest"
```

Expected: compilation fails because `RequestBudget`, the new augmenter parameter, and `delayFn` do not exist.

- [ ] **Step 3: Implement the shared budget**

Create `RequestBudget.kt`:

```kotlin
package com.novelreader.domain.usecase.webimport

class RequestBudget(private val limit: Int) {
    init { require(limit >= 0) }

    var used: Int = 0
        private set

    val remaining: Int
        get() = (limit - used).coerceAtLeast(0)

    fun tryConsume(): Boolean {
        if (used >= limit) return false
        used++
        return true
    }
}
```

Change the interface:

```kotlin
suspend fun augment(
    homeUrl: String,
    homeDoc: Document,
    httpClient: HttpClient,
    requestBudget: RequestBudget
): List<ChapterLink>
```

Create one `RequestBudget(MAX_REQUESTS)` in `crawlChapterList`. Consume it immediately before every main-loop fetch and pass the same instance to every matching augmenter. Remove the independent `pageCount < MAX_PAGES` condition; the budget is authoritative.

`ReadNovelFullListAugmenter` returns empty when `tryConsume()` is false and uses `SameNovelDomain(homeHost)` for its AJAX request.

- [ ] **Step 4: Bound and pace FreeWebNovel augmentation**

Add:

```kotlin
@androidx.annotation.VisibleForTesting
internal var delayFn: suspend (Long) -> Unit = { delay(it) }
```

Before looping, reject invalid state:

```kotlin
if (state.pageSize !in 1..MAX_PAGE_SIZE || state.totalPage <= 1) return emptyList()
```

For each page:

```kotlin
if (!requestBudget.tryConsume() || all.size >= MAX_LINKS) break
val succeeded = runCatching {
    val response = httpClient.get(
        url = ajaxUrl,
        referrer = homeUrl,
        extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest"),
        policy = RemoteRequestPolicy.SameNovelDomain(homeDomain)
    )
    if (response.statusCode != 200) return@runCatching false
    val parsed = parseChapterPaginationJson(response.body) ?: return@runCatching false
    if (parsed.code != 200) return@runCatching false
    val links = extractChapterLinks(Jsoup.parseBodyFragment(parsed.html), homeUrl, homeDomain)
    all += links.take((MAX_LINKS - all.size).coerceAtLeast(0))
    true
}.getOrDefault(false)

consecutiveFailures = if (succeeded) 0 else consecutiveFailures + 1
delayFn(PAGE_DELAY_MS)
if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) break
```

Define `MAX_PAGE_SIZE = 200`, `MAX_LINKS = 10_000`, and `MAX_CONSECUTIVE_FAILURES = 3` next to the existing delay constant.

- [ ] **Step 5: Update existing callers and tests**

Pass `RequestBudget(50)` in direct augmenter tests. In crawler tests, assert one home request plus augmenter requests never exceeds 50. Keep fixtures and ordering assertions unchanged.

- [ ] **Step 6: Run crawl-budget tests and verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.domain.usecase.webimport.ChapterCrawlerTest" \
  --tests "com.novelreader.domain.usecase.webimport.ReadNovelFullListAugmenterTest" \
  --tests "com.novelreader.domain.usecase.webimport.FreewebnovelListAugmenterTest"
```

Expected: all selected tests pass without real 1.5-second waits in failure tests.

- [ ] **Step 7: Commit bounded crawl work**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/webimport/RequestBudget.kt \
  app/src/main/java/com/novelreader/domain/usecase/webimport/NovelListAugmenter.kt \
  app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt \
  app/src/main/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenter.kt \
  app/src/main/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenter.kt \
  app/src/test/java/com/novelreader/domain/usecase/webimport/ChapterCrawlerTest.kt \
  app/src/test/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenterTest.kt \
  app/src/test/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenterTest.kt
git commit -m "fix(security): bound chapter-list request work"
```

---

### Task 5: Route manual cover downloads through the hardened client

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/storage/CoverStorage.kt`
- Create: `app/src/test/java/com/novelreader/data/storage/CoverStorageTest.kt`

**Interfaces:**
- Consumes: `HttpClient.get` and `RemoteRequestPolicy.AnyPublicHttps` from Task 1.
- Changes: `CoverStorage` constructor adds `HttpClient`.
- Preserves: `saveFromUrl(novelId: Long, url: String): String?`.

- [ ] **Step 1: Write failing cover storage tests**

Create a Robolectric test with `ApplicationProvider`, `Dispatchers.Unconfined`, and a mocked `HttpClient`:

```kotlin
@Test
fun `remote cover uses the public https policy and actual byte limit`() = runTest {
    val bytes = byteArrayOf(1, 2, 3, 4)
    coEvery {
        httpClient.get(
            url = COVER_URL,
            policy = RemoteRequestPolicy.AnyPublicHttps,
            maxBodyBytes = CoverStorage.MAX_REMOTE_COVER_BYTES,
            maxDecompressedBytes = CoverStorage.MAX_REMOTE_COVER_BYTES
        )
    } returns HttpResponse(200, "", emptyMap(), COVER_URL, bytes)

    val path = storage.saveFromUrl(7L, COVER_URL)

    assertThat(requireNotNull(path).let(::File).readBytes().contentEquals(bytes)).isTrue()
}

@Test
fun `failed remote cover leaves no destination or temporary file`() = runTest {
    coEvery { httpClient.get(any(), any(), any(), any(), any(), any()) } throws
        IOException("Response body exceeds limit")

    assertThat(storage.saveFromUrl(7L, COVER_URL)).isNull()
    assertThat(File(context.filesDir, "covers").listFiles().orEmpty()).isEmpty()
}
```

Also assert a non-2xx response returns `null` without a file.

- [ ] **Step 2: Run cover storage tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.storage.CoverStorageTest"
```

Expected: compilation fails because the constructor has no client and the size constant does not exist.

- [ ] **Step 3: Replace raw URL egress**

Inject `HttpClient`. Remove `java.net.URL`. Define the test-visible limit on the class:

```kotlin
companion object {
    internal const val MAX_REMOTE_COVER_BYTES = 10 * 1024 * 1024
}
```

Fetch with:

```kotlin
val response = httpClient.get(
    url = url,
    policy = RemoteRequestPolicy.AnyPublicHttps,
    maxBodyBytes = MAX_REMOTE_COVER_BYTES,
    maxDecompressedBytes = MAX_REMOTE_COVER_BYTES
)
if (response.statusCode !in 200..299) return@withContext null
val bytes = response.bodyBytes ?: return@withContext null
```

Write to `novel_<id>.jpg.tmp`, then rename to the final cover only after the complete byte array is written. Delete the temporary file in `catch`/`finally` when the operation fails.

- [ ] **Step 4: Run cover tests and verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.storage.CoverStorageTest"
```

Expected: all selected tests pass and no partial files remain.

- [ ] **Step 5: Commit cover egress remediation**

```bash
git add app/src/main/java/com/novelreader/data/storage/CoverStorage.kt \
  app/src/test/java/com/novelreader/data/storage/CoverStorageTest.kt
git commit -m "fix(security): harden remote cover downloads"
```

---

### Task 6: Bound and harden MVLEMPYR character import

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/remote/MvlempyrCharacterImporter.kt`
- Create: `app/src/test/java/com/novelreader/data/remote/MvlempyrCharacterImporterTest.kt`

**Interfaces:**
- Consumes: `HttpClient.get` and `RemoteRequestPolicy.AnyPublicHttps` from Task 1.
- Changes: constructor adds `HttpClient`.
- Preserves: `fetchCharacters(url)` and `importCharacters(url, novelId)` public behavior.

- [ ] **Step 1: Write failing hardened-client tests**

Create a Robolectric test with real application context, relaxed DAO mocks, `Dispatchers.Unconfined`, and a mocked client. Add:

```kotlin
@Test
fun `fetch characters paginates the api with bounded responses`() = runTest {
    coEvery { httpClient.get(SOURCE_URL, any(), any(), any(), any(), any()) } returns
        response(SOURCE_URL, """filter(e => "42" === e.BookId)""")
    coEvery { httpClient.get(match { "page=1" in it }, any(), any(), any(), any(), any()) } returns
        response("api-1", """[{"BookId":"42","Name":"A"}]""")
    coEvery { httpClient.get(match { "page=2" in it }, any(), any(), any(), any(), any()) } returns
        response("api-2", "[]")

    val result = importer.fetchCharacters(SOURCE_URL)

    assertThat(result.map { it.name }).containsExactly("A")
    coVerify(exactly = 1) {
        httpClient.get(
            url = match { "per_page=100" in it && "page=1" in it },
            policy = RemoteRequestPolicy.AnyPublicHttps,
            maxBodyBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES,
            maxDecompressedBytes = MvlempyrCharacterImporter.MAX_API_PAGE_BYTES
        )
    }
}
```

Add tests for:

- exactly three consecutive HTTP/JSON failures stop API pagination;
- 50 non-empty pages stop at 5,000 records;
- `importCharacters` downloads an image through `AnyPublicHttps` with the 10 MiB image limit;
- image fetch failure leaves no `design.jpeg` or temporary file.

- [ ] **Step 2: Run importer tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.remote.MvlempyrCharacterImporterTest"
```

Expected: compilation fails because the importer has no `HttpClient` and no limit constants.

- [ ] **Step 3: Replace page and image raw egress**

Inject `HttpClient`. Remove `Jsoup.connect`, `java.net.URL`, and `readText` remote use. Fetch the selected page with `AnyPublicHttps` and the normal HTML limit, then parse `response.body` with `Jsoup.parse` or apply the existing regex directly.

Define:

```kotlin
internal const val API_PAGE_SIZE = 100
internal const val MAX_API_PAGES = 50
internal const val MAX_API_ENTRIES = 5_000
internal const val MAX_API_PAGE_BYTES = 1024 * 1024
internal const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
private const val MAX_CONSECUTIVE_FAILURES = 3
```

Build each API URL as:

```kotlin
"$MVLEMPYR_API_BASE_URL?per_page=$API_PAGE_SIZE&page=$page"
```

- [ ] **Step 4: Implement bounded API pagination**

Use a fixed loop and stop conditions:

```kotlin
val allCharacters = mutableListOf<org.json.JSONObject>()
var consecutiveFailures = 0
for (page in 1..MAX_API_PAGES) {
    val response = runCatching { fetchApiPage(page) }.getOrNull()
    val pageItems = response
        ?.takeIf { it.statusCode in 200..299 }
        ?.let { runCatching { JSONArray(it.body) }.getOrNull() }
    if (pageItems == null) {
        consecutiveFailures++
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) break
        continue
    }
    consecutiveFailures = 0
    if (pageItems.length() == 0) break
    for (index in 0 until pageItems.length()) {
        if (allCharacters.size >= MAX_API_ENTRIES) break
        allCharacters += pageItems.getJSONObject(index)
    }
    if (allCharacters.size >= MAX_API_ENTRIES) break
}
```

Do not trust a short non-empty page as terminal; continue until an empty page, three consecutive failures, 50 pages, or 5,000 accumulated entries. Filter the bounded list by `BookId` exactly as before.

Replace `downloadImage` with an `HttpClient` request using `AnyPublicHttps` and `MAX_IMAGE_BYTES`. Write bytes to a temporary sibling and rename after success; delete the temporary file on every failure.

- [ ] **Step 5: Run importer tests and verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.remote.MvlempyrCharacterImporterTest"
```

Expected: all selected tests pass; request count, record count, and file cleanup stay within the fixed limits.

- [ ] **Step 6: Commit character-import remediation**

```bash
git add app/src/main/java/com/novelreader/data/remote/MvlempyrCharacterImporter.kt \
  app/src/test/java/com/novelreader/data/remote/MvlempyrCharacterImporterTest.kt
git commit -m "fix(security): bound character import egress"
```

---

### Task 7: Close audit regressions and validate the complete remediation

**Files:**
- Modify: `docs/security-residual-risks.md`
- Modify: `AGENTS.md`
- Verify: all production and test files changed in Tasks 1–6
- Reference only: `piolium/final-audit-report.md` and compatible PoCs under `piolium/findings/`

**Interfaces:**
- Consumes all prior tasks.
- Produces validated source, accurate documentation, and final evidence for the four remediated findings.

- [ ] **Step 1: Run the raw-egress and policy-coverage scan**

Run:

```bash
rg -n 'URL\(|openConnection\(|Jsoup\.connect|\.readText\(' app/src/main/java --glob '*.kt'
rg -n 'httpClient\.get\(' app/src/main/java --glob '*.kt'
```

Expected:

- no production remote `URL`, `openConnection`, or `Jsoup.connect` call remains;
- local file `readText()` calls may remain;
- each remote client call has an explicit policy where its trust boundary is not already fixed by its caller.

- [ ] **Step 2: Run all focused security regressions**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.domain.usecase.webimport.HttpClientTest" \
  --tests "com.novelreader.util.PublicOnlyDnsTest" \
  --tests "com.novelreader.util.CloudflareChallengePolicyTest" \
  --tests "com.novelreader.ui.webimport.WebImportViewModelTest" \
  --tests "com.novelreader.domain.usecase.webimport.ChapterCrawlerTest" \
  --tests "com.novelreader.domain.usecase.webimport.ChapterFetcherTest" \
  --tests "com.novelreader.domain.usecase.webimport.ReadNovelFullListAugmenterTest" \
  --tests "com.novelreader.domain.usecase.webimport.FreewebnovelListAugmenterTest" \
  --tests "com.novelreader.domain.usecase.webimport.CoverDownloaderTest" \
  --tests "com.novelreader.data.storage.CoverStorageTest" \
  --tests "com.novelreader.data.remote.MvlempyrCharacterImporterTest"
```

Expected: all selected tests pass with zero failures.

- [ ] **Step 3: Assess historical PoC compatibility without replaying exploits**

Read the three executed PoC entry points and compare their embedded source lists and constructor calls with the fixed interfaces:

```bash
rg -n 'REAL_SOURCES|HttpClient\(|ChapterCrawler\(|FreewebnovelListAugmenter\(|CoverStorage\(|MvlempyrCharacterImporter\(' \
  piolium/findings/M2-page-controlled-crawler-outbound-destination/poc.py \
  piolium/findings/M3-unguarded-raw-url-egress-and-cap-bypass \
  piolium/findings/M4-remote-controlled-unbounded-pagination-loop/poc.py
```

Expected: these are historical exploit harnesses that assert vulnerable behavior and embed the pre-fix constructors/interfaces. Do not replay or rewrite them as passing tests. Preserve them as audit evidence, record their incompatibility in the completion report, and use the Task 1–6 JVM regressions as the post-fix evidence.

- [ ] **Step 4: Run the canonical full validation**

Run:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` and zero failed tests.

- [ ] **Step 5: Update security documentation**

In `docs/security-residual-risks.md`:

- replace the tautological Cloudflare-control claim with the now-independent source-host propagation and navigation containment;
- document pre-contact redirect validation and the two destination policies;
- state that cover/character paths now use the hardened client and actual-byte limits;
- document the shared 50-request crawl budget and bounded MVLEMPYR pagination;
- list all four balanced-audit Medium findings as remediated at the final implementation commits.

In `AGENTS.md`, update the current test count from the generated JUnit XML totals and add only durable security architecture facts that future changes must preserve.

- [ ] **Step 6: Review the final diff and repository state**

Run:

```bash
git diff --check
git diff 47ececb..HEAD -- app/src/main app/src/test docs/security-residual-risks.md AGENTS.md
git status --short --branch
```

Expected: no whitespace errors, no unrelated source changes, and only the pre-existing `.agents/` and `AGENTS.md.bak-1788753396` paths remain untracked.

- [ ] **Step 7: Commit documentation and validation record**

```bash
git add docs/security-residual-risks.md AGENTS.md
git commit -m "docs: record security audit remediation"
```

- [ ] **Step 8: Report completion without publishing**

Report:

- commits created;
- exact focused and full validation commands/results;
- PoC harnesses run or why a harness was not safely runnable;
- any residual risk or compatibility impact;
- branch state.

Do not bump the version, tag, push, merge, or publish in this task. Those operations require a separate explicit release authorization after the remediation is reviewed.
