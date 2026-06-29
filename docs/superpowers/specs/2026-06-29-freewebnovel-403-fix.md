# FreeWebNovel 403 — Cloudflare Managed-Challenge Fix

**Date:** 2026-06-29
**Scope:** Restore import + auto-update for novels from `freewebnovel.com` (and any other Cloudflare-managed-challenge-gated domain) without regressing `readnovelfull.com` or local MHT import.
**Approach:** One logical commit, direct to `main`. Isolated from the 32 unpushed v2.4.2 commits — do not push those in this workstream.

---

## Decisions

- **Two-stage fix is required.** OkHttp + Chrome-like TLS profile is necessary so Cloudflare is willing to *issue* a managed challenge to our fingerprint. WebView-backed challenge solver is necessary because the challenge page requires JavaScript execution to compute `cf_clearance`; a static HTTP client cannot solve it. The two are coupled: the HTTP client must emit the same UA as the WebView when it replays `cf_clearance`, because `cf_clearance` is keyed per `(IP, UA)`.
- **In-app WebView** (not Chrome Custom Tabs, not external browser). Custom Tabs store cookies in the *browser* profile, not the app's `CookieManager` — unreliable for our `cf_clearance` extraction.
- **Re-verify notification on background failure.** `ChapterUpdateCheckWorker` is foreground-less; it cannot open a WebView. When the cached cookie expires, the worker posts a notification; the user re-verifies once. The periodic schedule then resumes normally.
- **Cookie store keyed by `(domain, uaHash)` with `expiresAt`.** `cf_clearance` is per-IP and per-UA. Mobile network changes or a UA bump invalidate it; the solver dialog re-appears on a fresh 403.
- **Parsers untouched.** `FreeWebNovelParser`, `ParserRegistry`, `GenericFallbackParser` continue to receive `Document`. They never see a challenge page because the detector short-circuits before parsing.
- **Phase A diagnostic stays.** The `WebFetchProbe` debug log line is now even more useful — it confirms post-fix that we go straight to 200 (no challenge) once `cf_clearance` is seeded.

---

## Root cause (confirmed by Phase A on-device capture)

```
WebFetchProbe: callSite=crawler status=403
  url=https://freewebnovel.com/novel/cultivation-starting-from-a-mortal-bone
  server=cloudflare cfRay=a1350d671d1f00ea-GRU setCookie=(empty)
  body=<!DOCTYPE html>...<title>Just a moment...</title>...meta robots noindex,nofollow...
  exception=HttpStatusException
```

- `callSite=crawler` — crawler fails first; fetcher never reached. Update-check path (crawler-only) fails identically.
- `status=403` + body `<title>Just a moment...</title>` + `meta robots noindex,nofollow` — **Cloudflare managed-challenge interstitial**, not a static fingerprint block.
- `setCookie=(empty)` — `cf_clearance` is **not** issued in the 403; only after a successful JS challenge solve. Cannot be extracted from the 403 response alone.
- `cfRay=…-GRU` — same São Paulo edge curl with the app's exact UA gets 200 from. Difference is **TLS fingerprint** of Android's Conscrypt `HttpURLConnection` (vs. curl's OpenSSL); Cloudflare routes the JA3 to the challenge interstitial.

Earlier hypotheses ruled out: stale UA, missing `Sec-Fetch-*` / `Accept-Encoding` headers, region/IP, plain static fingerprint block (no body would say "Just a moment…").

---

## Architecture overview

### Components

| Component | File | Purpose |
|---|---|---|
| `HttpClient` | `app/src/main/.../webimport/HttpClient.kt` (new) | OkHttp wrapper, Chrome-like TLS profile, DataStore-backed `CookieJar` |
| `CloudflareCookieStore` | `app/src/main/.../data/local/preferences/CloudflareCookieStore.kt` (new) | Persists `cf_clearance` per `(domain, uaHash)` with `expiresAt` |
| `CloudflareChallengeDetector` | `app/src/main/.../webimport/CloudflareChallengeDetector.kt` (new) | Pure function: 4xx + body matches "Just a moment" / "cf-mitigated" / "challenge-platform" → challenge required |
| `CloudflareChallengeRequiredException` | same file as detector | Typed exception carrying URL + evidence |
| `CloudflareChallengeDialog` | `app/src/main/.../ui/webimport/CloudflareChallengeDialog.kt` (new) | Compose dialog with in-app `WebView`; auto-detects challenge-clear, extracts cookie, persists, retries |
| `WebImportViewModel` (extend) | existing file | New state `CloudflareChallengeRequired(url, novelId?)`; new intent `ChallengeSolved` / `ChallengeCancelled` |
| `ChapterCrawler` (refactor) | existing | Calls `httpClient.get(url)`, parses with `Jsoup.parse(body, url)`; on challenge → throws `CloudflareChallengeRequiredException` |
| `ChapterFetcher` (refactor) | existing | Same refactor; preserves retry matrix (429 → 3s×attempt, 5xx → 2s×attempt, other 4xx → rethrow) |
| `CoverDownloader` (refactor) | existing | Routes through `httpClient.get` |
| `ChapterUpdateCheckWorker` (extend) | existing | On `CloudflareChallengeRequiredException` → post notification, return `Result.success()` (do not retry) |
| `UpdateNotificationHelper` (extend) | existing | New `postCloudflareReverifyNotification(novelId, novelTitle)`; new channel |
| `DeepLinkBus` (extend) | existing | New `OpenCloudflareSolver(novelId?)` action |
| `NavGraph` (extend) | existing | Route the deep link to the dialog |
| `MainActivity` (verify) | existing | Already emits deep-link actions via `EXTRA_DEEP_LINK_ACTION` — confirm wiring, no code change if already correct |
| `strings.xml` + `values-en/strings.xml` | resources | 7 new PT/EN keys |

### Data flow

**User-driven import (foreground):**
```
LibraryScreen → WebImportScreen → paste URL → tap Import
  → WebImportViewModel.import(url)
    → WebImportUseCase.fetchChapterList(url)
      → ChapterCrawler.crawlChapterList(url)
        → HttpClient.get(url)                     // OkHttp
          → okHttpClient.newCall(request).execute()
            → response: if 4xx + body is challenge → throw CloudflareChallengeRequiredException
            → else → return response
        → catch CloudflareChallengeRequiredException
          → propagate as UI state CloudflareChallengeRequired(url)
    → ViewModel state → Compose shows CloudflareChallengeDialog
  → Dialog: in-app WebView → onPageFinished polls challenge-clear (3-8s) → CookieManager.flush → getCookie(url) → CloudflareCookieStore.putCookies
  → dialog emits ChallengeSolved intent
  → ViewModel retries fetchChapterList → okHttpClient replays cf_clearance → 200 → import continues
```

**Background update check:**
```
ChapterUpdateCheckWorker.doWork()
  → WebImportUseCase.fetchChapterList(novel.sourceUrl)
    → ChapterCrawler.crawlChapterList(...)
      → HttpClient.get(url)
        → 4xx + challenge body → CloudflareChallengeRequiredException
  → worker catches → UpdateNotificationHelper.postCloudflareReverifyNotification(novelId, novelTitle) → Result.success()
  → notification tap → MainActivity intent → DeepLinkBus.OpenCloudflareSolver(novelId) → NavGraph → CloudflareChallengeDialog → same flow as user-driven
```

### HttpClient details

- Single `OkHttpClient` configured with:
  - `ConnectionSpec.MODERN_TLS` and an explicit cipher list ordered to match Chrome (GREASE-aware): `TLS_AES_128_GCM_SHA256`, `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`, `TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256`, `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`, `TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384`, `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`, `TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256`, `TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256`, `TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA`, `TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA`, `TLS_RSA_WITH_AES_128_GCM_SHA256`, `TLS_RSA_WITH_AES_256_GCM_SHA384`, `TLS_RSA_WITH_AES_128_CBC_SHA`, `TLS_RSA_WITH_AES_256_CBC_SHA`. (Sufficient to match Chrome's TLS profile; exact JA3 is not guaranteed.)
  - Default request headers per request: `User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36` (bumped from 125.x), `Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,application/signed-exchange;v=b3;q=0.7,*/*;q=0.6`, `Accept-Language: en-US,en;q=0.9`, `Accept-Encoding: gzip, deflate, br`, `Sec-Fetch-Site: none`, `Sec-Fetch-Mode: navigate`, `Sec-Fetch-Dest: document`, `Sec-Fetch-User: ?1`, `Upgrade-Insecure-Requests: 1`.
  - Timeouts: 30s connect / 30s read / 30s write (matches existing Jsoup `.timeout(30_000)`).
  - `followRedirects(true)`, `followSslRedirects(true)`.
  - Custom `CookieJar` backed by `CloudflareCookieStore` (read on `loadForRequest`, write on `saveFromResponse`).
- Public API: `suspend fun get(url: String, referrer: String? = null): HttpResponse`.
- `HttpResponse` is a value type: `statusCode: Int`, `body: String` (decompressed), `headers: Map<String, String>`.

### CloudflareCookieStore details

- DataStore: separate `cloudflare_cookies` instance (keyed by name in a Hilt module).
- Record shape: `domain: String`, `uaHash: String` (SHA-256 of the UA), `cookies: List<StoredCookie>`, `expiresAt: Long`.
- `StoredCookie`: `name`, `value`, `domain`, `path`, `expiresAt`. Drop entries with `expiresAt < now()`.
- `OkHttpClient` reads cookies on `loadForRequest(HttpUrl)`: filter by `domain matches` + `path matches` + not-expired. `saveFromResponse(HttpUrl, List<Cookie>)`: persist with `expiresAt = max(cookieExpiresAt, now + 1h)`.
- Public API: `suspend fun cookiesFor(url: String): List<Cookie>`, `suspend fun putCookies(url: String, cookies: List<Cookie>)`, `suspend fun clear(domain: String)`.

### CloudflareChallengeDialog details

- Compose `Dialog` (or fullscreen overlay) with an `AndroidView { WebView(it) }` pointed at the same URL.
- `WebViewClient`:
  - `onPageStarted`: log "challenge page loading" via `WebFetchProbe`.
  - `onPageFinished`: poll every 1s for up to 10s. On each tick, check if the current URL still contains `cf-mitigated` / `challenge-platform` OR the page title still equals "Just a moment…". If clear, call `CookieManager.getInstance().flush()` then `CookieManager.getInstance().getCookie(url)`, parse, persist via `CloudflareCookieStore.putCookies`, and emit `ChallengeSolved`.
- CookieManager setup: in the host Activity, `CookieManager.getInstance().setAcceptCookie(true)` and `setAcceptThirdPartyCookies(webView, true)` (required for some Cloudflare subdomains).
- Cancel button always available → emits `ChallengeCancelled`.
- Strings: `cloudflare_challenge_title`, `cloudflare_challenge_body`, `cloudflare_challenge_action`, `cloudflare_challenge_cancel`, `cloudflare_challenge_waiting`.

### Strings (PT + EN, both files)

| key | pt | en |
|---|---|---|
| `cloudflare_challenge_title` | Verificação do site | Site verification |
| `cloudflare_challenge_body` | O site exige uma verificação única de navegador. Toque abaixo para abrir a janela de verificação. | The site requires a one-time browser verification. Tap below to open the verification window. |
| `cloudflare_challenge_action` | Verificar conexão | Verify connection |
| `cloudflare_challenge_cancel` | Cancelar | Cancel |
| `cloudflare_challenge_waiting` | O site está verificando o seu navegador. Aguarde alguns segundos… | The site is verifying your browser. Please wait a few seconds… |
| `cloudflare_reverify_notif_title` | Verificação expirada — %s | Verification expired — %s |
| `cloudflare_reverify_notif_body` | Toque para verificar e continuar recebendo novos capítulos. | Tap to verify and continue receiving new chapters. |

The existing `i18n/I18nCoverageTest` enforces the EN mirror. Writing PT without writing EN will fail the test — drives the bilingual flow.

---

## Per-component (implementation + test)

### HttpClient (TDD)

**`HttpClientTest.kt`** using MockWebServer (already declared at `app/build.gradle.kts:137`, currently unused):

1. RED: `get(url) sends modern Chrome UA and Sec-Fetch headers` — fail because no `HttpClient` exists.
2. GREEN: implement `HttpClient` with the header set above.
3. RED: `get(url) replays cf_clearance from the cookie store` — seed a `cf_clearance` in `CloudflareCookieStore`, fire a request, assert MockWebServer received `Cookie: cf_clearance=…`.
4. GREEN: wire `CookieJar` → `CloudflareCookieStore`.
5. RED: `get(url) returns 4xx body without throwing` — enqueue 403 + body, assert `HttpResponse.statusCode == 403` and `body == recordedBody` (no exception).
6. GREEN: implement.

### CloudflareChallengeDetector (TDD)

**`CloudflareChallengeDetectorTest.kt`**:

1. `isCloudflareChallenge(403, body="Just a moment…", empty headers) == true`.
2. `isCloudflareChallenge(403, body="<html>real 403 page</html>", empty headers) == false`.
3. `isCloudflareChallenge(200, body="Just a moment…", empty headers) == false` (status filter).
4. `isCloudflareChallenge(403, body=null, headers=mapOf("cf-mitigated" to "challenge")) == true`.
5. `isCloudflareChallenge(404, body="", headers=emptyMap()) == false`.
6. `isCloudflareChallenge(429, body="Just a moment…", headers=emptyMap()) == true` (Cloudflare also gates on 429 sometimes).

GREEN: implement the detector as a pure function — no Android deps, easy to test.

### CloudflareCookieStore (TDD)

**`CloudflareCookieStoreTest.kt`** with Robolectric + DataStore:

1. `putCookies then cookiesFor returns the same cookies`.
2. `putCookies with expiresAt in the past then cookiesFor returns empty`.
3. `putCookies for domainA then cookiesFor for domainB returns empty` (domain isolation).
4. `clear(domainA) removes only domainA entries`.
5. `cookiesFor returns only unexpired cookies` (dropped expired ones even when present).

GREEN: implement the DataStore layer. Use `androidx.datastore.preferences.core` (already in deps).

### ChapterCrawler / ChapterFetcher (TDD)

**`ChapterCrawlerTest.kt`**:

1. 200 + recorded freewebnovel index HTML → `crawlChapterList(url)` returns chapter list (no exception).
2. 403 + recorded challenge HTML → throws `CloudflareChallengeRequiredException` (not generic `HttpStatusException`).
3. 403 + non-challenge HTML → throws `CloudflareChallengeRequiredException` only if detector says so; otherwise falls back to old behavior (caller sees `HttpStatusException`).

**`ChapterFetcherTest.kt`**:

1. 200 + recorded chapter HTML → `fetch(url, fileName, title)` returns parsed chapter.
2. 403 + challenge HTML → throws `CloudflareChallengeRequiredException`; `WebFetchProbe` log emitted.
3. 429 + 200 → retries once; final result is success (existing retry matrix preserved).
4. 500 → retries twice; final throws (existing behavior preserved).

### CloudflareChallengeDialog (Robolectric / Compose)

**`CloudflareChallengeDialogTest.kt`** (Robolectric + Compose Test):

1. Renders title + body + Verify + Cancel buttons.
2. Cancel tap emits `ChallengeCancelled` to `WebImportViewModel`.
3. (Optional) WebView interaction: load fixture HTML via `loadDataWithBaseURL`, simulate `onPageFinished`, assert `ChallengeSolved` emission.

### ChapterUpdateCheckWorker (Robolectric)

**`ChapterUpdateCheckWorkerTest.kt`** (existing pattern at `app/src/test/java/com/novelreader/data/worker/`):

1. With a freewebnovel novel and `cf_clearance` cached → success, no notification.
2. With a freewebnovel novel and no cookie + 403 challenge → `Result.success()` returned, notification posted.
3. With a freewebnovel novel and non-challenge exception → `Result.retry()` (unchanged behavior).

### i18n coverage

`i18n/I18nCoverageTest.kt` is already in place; the new PT keys will fail the test until EN mirrors are added. Drive the EN translations from the test failure (TDD).

### Test fixtures (committed, no live-site hits from CI)

- `app/src/test/resources/freewebnovel/index.html` — recorded freewebnovel index page (downloaded once with curl, expected to be a real novel index; recorded now while curl still gets 200).
- `app/src/test/resources/freewebnovel/chapter-1.html` — recorded chapter page.
- `app/src/test/resources/freewebnovel/challenge.html` — the 256-byte challenge snippet we already captured from the user's `WebFetchProbe` log.

---

## Out of scope (explicit)

- Switching the HTTP client to anything other than OkHttp (the OkHttp + Chrome-TLS profile is the only realistic path on Android).
- Using a third-party challenge-solving service (FlareSolverr, etc.) — adds infra and CGNAT.
- Pushing the 32 v2.4.2 unpushed commits.
- Bumping any unrelated dependencies.
- Adding OkHttp Interceptors for retry/backoff/circuit-breaker beyond what `ChapterFetcher.fetchWithRetry` already does (the existing retry matrix is preserved verbatim, just implemented via `HttpResponse.statusCode`).
- Adding Hilt `@Binds` for `HttpClient` interface — `HttpClient` is a concrete class; consumers depend on it directly.

---

## Risks

- **`cf_clearance` per-IP-and-UA**: mobile network changes or UA bumps invalidate the cookie. Keyed storage + `expiresAt` + re-prompt covers this.
- **Cloudflare adaptive challenges**: Cloudflare may escalate to an interactive checkbox. The dialog polls for 10s and shows a "still verifying" message; the user can wait or cancel.
- **`CookieManager.flush()` timing**: on `minSdk 26` (Android 8.0+), `flush()` must be called before reading cookies; the dialog calls it explicitly. Robolectric test simulates the timing.
- **WebView inside worker is not feasible**: this is the design rationale for the re-verify notification.
- **Bumping the OkHttp lib version** must not regress `minSdk 26` / `target 34` / Kotlin 2.2.10. Use the version catalog conventions.
- **The OkHttp swap alone will not fix anything** — without the WebView solver, the HTTP client just receives the challenge page and `FreeWebNovelParser` would barf. C.1 and C.2 are tightly coupled; both must land together.

---

## Commit plan

Single commit on `main`:
- Message (PT/EN, conventional): `fix(webimport): bypass Cloudflare managed-challenge via OkHttp + WebView solver (cf_clearance cache)`
- Touches (see Components table above).
- Does not touch the 32 v2.4.2 unpushed commits.
- Do not push.

---

## Verification

1. `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` — all green; tests ≥ 167 (163 baseline + 4 diagnostic + new C.1/C.2/C.3 tests).
2. `./gradlew :app:assembleDebug` — debug APK builds.
3. On-device:
   - Cold import, no cookie → dialog appears → solver resolves → import completes.
   - Warm import → no dialog.
   - Update check with valid cookie → succeeds.
   - Cookie expiry → re-verify notification → tap → solver → updates resume.
   - readnovelfull.com sanity → no regression.
   - Local MHT import sanity → no regression.
4. `WebFetchProbe` log on a warmed-up import shows `status=200` directly (no challenge).
