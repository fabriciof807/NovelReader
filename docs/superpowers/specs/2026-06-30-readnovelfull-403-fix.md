# readnovelfull.com import fix — design

**Date:** 2026-06-30
**Phase:** A (cross-site novel merge is Phase B)
**Status:** Approved 2026-06-29

## Problem

`readnovelfull.com` is one of the two primary novel sources the user actively imports. The app's `ReadNovelFullParser` correctly parses single-chapter pages, but `ChapterCrawler` only sees the static HTML of the novel landing page, which contains **~30 chapter links**. The remaining 300+ chapters for a typical novel are behind an AJAX endpoint, so the user can only ever import 30-60 chapters per novel.

### Investigation findings (in-vitro probes)

- readnovelfull.com serves novel landing pages with `<div data-novel-id="N">` and ~30 anchors of the form `/<slug>/chapter-<n>-<unique_id>.html`.
- The complete chapter list is delivered in a **single** GET: `https://readnovelfull.com/ajax/chapter-archive?novelId=<N>` (header `X-Requested-With: XMLHttpRequest`). Response is **raw HTML** (an `<ul class="list-chapter">` fragment) that replaces the `#chapter-archive` element on the page.
- Verified across 3 novels: `data-novel-id` always present in static HTML; ~30-32 static chapter links per page; AJAX returns 344 / 270 / 144 chapters for the three test novels.
- The site is behind Cloudflare (cf-ray present, `server: cloudflare`) but **does not** currently challenge direct OkHttp requests (curl succeeds with a fresh PHPSESSID cookie). Defense-in-depth is provided by the existing `CloudflareChallengeDialog` for future Under-Attack mode.
- **Naming-conflation note**: the parked domain `readfullnovel.com` (parklogic.com redirector) is a different site and out of scope. The target here is `readnovelfull.com` (existing `ReadNovelFullParser`).

## Goal

Import the **full** chapter list (not just the static-HTML subset) from `readnovelfull.com`, with a code path that is reusable for future per-domain chapter-list pagers.

## Architecture

Extract the per-domain "augment the static chapter list with AJAX-loaded chapters" logic into a **`NovelListAugmenter`** interface, dispatched by domain in `ChapterCrawler` after Phase A. Two implementations:

- **`FreewebnovelListAugmenter`** (refactor): moves the existing inline block (`ChapterCrawler.kt:70-93`) into its own class. Same behavior, same tests still pass.
- **`ReadNovelFullListAugmenter`** (new): extracts `data-novel-id` from the home page document, calls `/ajax/chapter-archive?novelId=N`, parses the response with `Jsoup.parseBodyFragment` + `extractChapterLinks`.

The interface is wired via Hilt multibinding (`Set<NovelListAugmenter>`) so future sites can be added by adding a new augmenter + a `@Binds @IntoSet` line.

## Side-fixes (in-scope)

1. **`CloudflareChallengeDialog` host hardcoding** (line 95): replace `loadedUrl.contains("freewebnovel.com")` with a parameter-driven expected-host check, so the dialog works for any domain (readnovelfull today, future sites tomorrow).
2. **`DataStoreCloudflareCookieStore` cookie-key mismatch** (line 28 vs line 38-45): `putCookies` keys on the cookie's own domain (often `.readnovelfull.com`), `cookiesFor` looks up by `URI(url).host` (often `www.readnovelfull.com`). Fix by making `cookiesFor` gather candidate hosts and union all matching sets. Today this does not block the happy path (Cloudflare is not challenging), but it is a latent regression when CF re-enables challenges.
3. **`ChapterNumberExtractor` and `chapter-10-41.html`**: confirm it captures the **first** number (10), not the unique-id (41). If it captures the wrong number, fix the regex. Test TDD covers the regression.

## Out of scope

- `HttpClient` POST support (readnovelfull chapter-archive uses GET).
- Touching the freewebnovel behavior beyond the extract-to-class refactor.
- Pushing any v2.4.x commits (the user has already pushed).
- Parked-domain `readfullnovel.com` (zero content; nothing to do).
- UI changes (no new screens, no new dialogs).
- The cloudflare challenge itself (re-uses the existing infrastructure).

## Files

### New
- `app/src/main/java/com/novelreader/domain/usecase/webimport/NovelListAugmenter.kt` — interface
- `app/src/main/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenter.kt`
- `app/src/main/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenter.kt`
- `app/src/main/java/com/novelreader/di/AugmenterModule.kt` — Hilt multibinding
- `app/src/test/java/com/novelreader/domain/usecase/webimport/NovelListAugmenterTest.kt`
- `app/src/test/java/com/novelreader/domain/usecase/webimport/FreewebnovelListAugmenterTest.kt`
- `app/src/test/java/com/novelreader/domain/usecase/webimport/ReadNovelFullListAugmenterTest.kt`
- `app/src/test/resources/readnovelfull/novel_landing_sample.html`
- `app/src/test/resources/readnovelfull/chapter_archive_sample.html`

### Modified
- `app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt` — extract freewebnovel block, dispatch augmenters
- `app/src/main/java/com/novelreader/ui/webimport/CloudflareChallengeDialog.kt` — generalize host check
- `app/src/main/java/com/novelreader/data/local/preferences/DataStoreCloudflareCookieStore.kt` — fix cookie-key mismatch
- `app/src/main/java/com/novelreader/data/parser/ChapterNumberExtractor.kt` — fix if `chapter-10-41.html` regex bug
- `app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt` — pass `expectedHost` to dialog
- `app/src/main/java/com/novelreader/util/StringUtils.kt` — `hostMatchesDomain` helper for dialog
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` — no new strings expected, but verify with I18nCoverageTest

### Test modifications
- Existing `ChapterCrawlerTest` continues to pass with the freewebnovel behavior (block moved, not changed). New `FreewebnovelListAugmenterTest` covers the moved code.

## Per-component details

### `NovelListAugmenter`

```kotlin
interface NovelListAugmenter {
    fun canAugment(homeUrl: String): Boolean
    suspend fun augment(
        homeUrl: String,
        homeDoc: org.jsoup.nodes.Document,
        httpClient: HttpClient
    ): List<ChapterLink>
}
```

`ChapterCrawler.crawlChapterList(homeUrl)` signature stays the same. After Phase A, the loop becomes:

```kotlin
for (augmenter in augmenters) {
    if (augmenter.canAugment(homeUrl)) {
        allLinks = allLinks + augmenter.augment(homeUrl, firstPageDoc, httpClient)
    }
}
```

The `firstPageDoc` is the Jsoup document from the first page fetched in Phase A (already in memory, no extra HTTP call needed for the home page).

### `ReadNovelFullListAugmenter`

- `canAugment(homeUrl)`: `URI(homeUrl).host?.removePrefix("www.")?.endsWith("readnovelfull.com") == true`.
- `augment`:
  1. `val novelId = homeDoc.selectFirst("[data-novel-id]")?.attr("data-novel-id")?.toIntOrNull()`. If null → return empty + `ZeroLinksDiagnostic` log.
  2. `val ajaxUrl = buildChapterArchiveUrl(homeUrl, novelId)`. Helper: `${URI(homeUrl).scheme}://${URI(homeUrl).authority}/ajax/chapter-archive?novelId=$novelId`.
  3. `val resp = httpClient.get(ajaxUrl, referrer = homeUrl, extraHeaders = mapOf("XRequested-With" to "XMLHttpRequest"))`.
  4. If `resp.statusCode == 200 && resp.body.contains("href")`: `val fragment = Jsoup.parseBodyFragment(resp.body); val links = extractChapterLinks(fragment, ajaxUrl, homeDomain)`. Return links.
  5. Else return empty.
- Pacing: not paginated (single GET), no delay needed.
- The `extractChapterLinks` (existing, in `ChapterCrawler.kt:195-271`) is reused as-is. It already matches URLs containing `chapter` and a digit, which covers `chapter-10-41.html`.

### `FreewebnovelListAugmenter`

Refactor of `ChapterCrawler.kt:70-93` into a class. Reuses `ChapterPaginationStateExtractor`, `ChapterPaginationJsonParser`, `buildChapterPaginationUrl` (the helper can move into the class as a private method). Receives the `homeDoc` and the `paginationState` (already extracted in `ChapterCrawler.crawlChapterList` Phase A line 60). Pacing: 1.5s between AJAX pages, same as before.

### `AugmenterModule` (Hilt)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class AugmenterModule {
    @Binds @IntoSet abstract fun bindFreewebnovel(impl: FreewebnovelListAugmenter): NovelListAugmenter
    @Binds @IntoSet abstract fun bindReadNovelFull(impl: ReadNovelFullListAugmenter): NovelListAugmenter
}
```

`ChapterCrawler` gains a new constructor param: `augmenters: Set<@JvmSuppressWildcards NovelListAugmenter>`.

### `CloudflareChallengeDialog`

- New param: `expectedHost: String` (host of the original `homeUrl`).
- Helper in `StringUtils.kt`: `fun hostMatchesDomain(host: String, expected: String): Boolean { val h = host.removePrefix("www."); val e = expected.removePrefix("www."); return h == e || h.endsWith(".$e") }`.
- Replace `loadedUrl.contains("freewebnovel.com")` with `hostMatchesDomain(Uri.parse(loadedUrl).host ?: "", expectedHost)`. Keep the existing `!contains("challenge")` and `!contains("cf-")` defensive excludes.
- Caller (ViewModel) derives `expectedHost` from `URI(homeUrl).host` and passes it.

### `DataStoreCloudflareCookieStore`

- `cookiesFor(url)` currently: `URI(url).host` → look up set.
- Fix: gather candidate hosts `{URI(url).host, ".${URI(url).host}", URI(url).host.removePrefix("www."), ".${URI(url).host.removePrefix("www.")}"}` and return the union of all non-expired cookies from any matching set.
- Test: add a unit test where a cookie was put with `domain=.readnovelfull.com` and lookup is for `www.readnovelfull.com` → returns the cookie.

### `ChapterNumberExtractor`

Audit the regex against `chapter-10-41.html`:
- Current: `(?:chapter|capítulo|ch|cap)\s*[.:\-]?\s*(\d+)` (case-insensitive).
- On input `chapter-10-41.html`: matches `chapter-10` → captures `10`. ✓
- On input `chapter-1-10.html` (readnovelfull's actual format per probe): matches `chapter-1` → captures `1`. ✗ — this would be wrong if the site numbered chapters like that, but the readnovelfull pattern is `chapter-<n>-<unique_id>.html` where the unique_id is arbitrary (we saw `-41`, `-352`, `-43`). So `10` is correct.
- The risk: if a future site has format `chapter-<unique_id>-<n>.html` (n last), our extractor returns the unique_id. Today not an issue.

## Tests

### `NovelListAugmenterTest` (skeleton, no real impl)
- The interface is a single-method marker; the tests live in the impls.

### `FreewebnovelListAugmenterTest`
- `canAugment` true for `https://www.freewebnovel.com/...`, false for `https://readnovelfull.com/...`.
- `augment` with `paginationState.totalPage=3` → 3 AJAX calls, each with `X-Requested-With: XMLHttpRequest`, returns union of page links.
- `augment` with `paginationState == null` → empty.
- `augment` with AJAX response `code != 200` → skips that page, continues.

### `ReadNovelFullListAugmenterTest`
- `canAugment` true for readnovelfull.com variants, false otherwise.
- `augment` with HTML containing `data-novel-id="2528"` + mocked HTTP returning 50 anchors → returns 50 `ChapterLink`s.
- `augment` with HTML missing `data-novel-id` → returns empty.
- `augment` with mocked HTTP 404 → returns empty.
- Verifies request URL: `https://readnovelfull.com/ajax/chapter-archive?novelId=2528`, header `X-Requested-With: XMLHttpRequest`, referrer = homeUrl.

### `CloudflareChallengeDialogTest` / `StringUtilsTest`
- `hostMatchesDomain("www.readfullnovel.com", "readfullnovel.com")` → true.
- `hostMatchesDomain("readfullnovel.com", "www.readfullnovel.com")` → true.
- `hostMatchesDomain("freewebnovel.com", "readfullnovel.com")` → false.
- `hostMatchesDomain("maliciousreadfullnovel.com", "readfullnovel.com")` → false (suffix-match protection).

### `DataStoreCloudflareCookieStoreTest`
- New: cookie with `domain=.readfullnovel.com` inserted → `cookiesFor("https://www.readfullnovel.com/...")` returns the cookie.
- Existing tests still pass.

### `ChapterNumberExtractorTest`
- New: `extract("chapter 10", "chapter-10-41.html", url)` → 10 (not 41).
- New: `extract("Chapter 10", "chapter-10-41.html", url)` → 10 (case-insensitive).
- New: `extract("", "", "https://example.com/x-7-y-13.html")` → 7 (falls back to first numeric).

### `ChapterCrawlerTest` regression
- Existing tests for the freewebnovel AJAX path continue to pass (the code moved, didn't change). The new `FreewebnovelListAugmenterTest` covers the moved block.
- New: `crawlChapterList` with a fake `ReadNovelFullAugmenter` registered → calls the augmenter, merges results.

## Risks

1. **Refactor regression of freewebnovel path**: the inline block is being moved, not changed. Mitigated by leaving the existing test contract intact and adding new augmenter tests that exercise the moved code with the same fixtures.
2. **readnovelfull enters Under-Attack mode**: handled by the existing CF dialog (now generalized by side-fix #1) + the existing `cf_clearance` cache in `DataStoreCloudflareCookieStore` (now correct by side-fix #2).
3. **`ChapterNumberExtractor` first-vs-last number bug**: only matters for sites with `chapter-<unique>-<n>.html` (n last). readnovelfull uses `chapter-<n>-<unique>.html` (n first), which the existing regex handles correctly. If a future site flips it, the extractor returns the unique_id. Documented, not addressed.

## Commit plan

Single logical commit: `fix(webimport): full readnovelfull.com chapter list via per-domain NovelListAugmenter` with PT/EN message body.

## Verification

- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → 100% green.
- On-device: import a 200+ chapter readnovelfull novel. Logcat shows `WebFetchProbe chapterList: ... totalFound=200+`. Library shows 200+ chapter rows.
- On-device: re-import the same novel → confirm dedup is unchanged (still fileName-based for now; phase B will add chapter-number dedup).
- On-device: import a 200+ chapter freewebnovel novel. Confirm same 200+ result (regression check).
