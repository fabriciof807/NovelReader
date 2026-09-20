# Security notes — residual risks and dependency audit

Last updated: 2026-09-19 (post-remediation, shipped in v2.11.0; source at v2.12.0).

This file records the security posture after the piolium lite audit and the
follow-up hardening pass. It is a companion to the audit artifacts under
`piolium/` (gitignored, local only).

## 1. Audit findings and fixes (v2.9.2)

The piolium lite audit (Q0–Q4, commit `d9ff16c`) reported five findings. All were
verified against the source and fixed:

| ID | Severity | Issue | Fix |
|---|---|---|---|
| F1 (q2-001/M1) | medium | Backup-import `fontFamily` was interpolated raw into the reader `<style>`, allowing HTML/JS injection into the WebView JS bridge (PoC executed) | `PreferenceAllowlists.sanitizeFontFamily` on write and read; CSS escaping at the `ReaderHtmlBuilder` sink |
| F2 (q2-002) | low | Backup `sourceUrl` drove an outbound https fetch (SSRF/beacon) | `RemoteHostGuard` rejects loopback/private/link-local/CGNAT/internal host suffixes for backup-supplied URLs |
| F3 (q2-003) | low | Backup `photoPath` restored any readable file into the character gallery | `PendingRestoreApplier` only accepts canonical paths inside the app's private `filesDir` |
| F4 (q2-004) | low | Exported `MainActivity` obeyed deep-link extras from any local app | Persisted per-install `DeepLinkToken` required by `DeepLinkIntentParser`; notification intents attach it |
| F5 (q2-005) | low | Site parsers selected by `domain.contains(...)` (parser confusion, non-deterministic order) | `StringUtils.hostMatchesDomain` (host boundary, port/trailing-dot tolerant) + deterministic parser ordering |

### 1.1 Balanced audit (2026-09-17) — four Medium findings, all remediated

Source at commit `5715f540`. The audit report and its PoCs remain as evidence under
`piolium/final-audit-report.md` and `piolium/findings/`; the fixes below landed on
`agent/security-audit-remediation-impl`, final implementation commit `8d5f40b`. That
follow-up commit pins the page-discovered cover download to the source host and makes
its write atomic; it is the last commit in the branch that changes app source or tests,
so every commit after it (`6e98821` and later) is documentation only.

| ID | Severity | Issue | Fix commits |
|---|---|---|---|
| M1-cloudflare-challenge-host-allowlist-tautology | medium | The pre-load gate compared a challenge URL's host with itself, so it degenerated to a scheme check and any page-chosen HTTPS URL loaded into the challenge WebView | `d317f86` |
| M2-page-controlled-crawler-outbound-destination | medium | Crawl traversal trusted page bytes for the next destination, and `followRedirects(true)` let `Location` leave the origin unvalidated; a cover discovered in a fetched page became its own allowed host, so a cross-domain cover was fetched from the page-supplied origin | `8208ea4`, `057322b`, `8d5f40b` |
| M3-unguarded-raw-url-egress-and-cap-bypass | medium | `CoverStorage` and `MvlempyrCharacterImporter` used raw `java.net.URL` / `Jsoup.connect`, bypassing `PublicOnlyDns` and trusting `Content-Length` for a 10 MiB cap (chunked responses failed open) | `39e7415`, `fb5cdc5`, `a935a26` |
| M4-remote-controlled-unbounded-pagination-loop | medium | A page-supplied `totalPage` drove an uncapped, unpaced request loop that shared no budget with the crawl path | `31323ae` |

Two commits carry the shared infrastructure the four fixes build on: `c7d96dd`
(one egress boundary with explicit destination policies and pre-contact redirect
validation) and `7352ef5` (exact IPv6-literal match in `hostMatchesDomain`, so a
bracketed literal cannot be satisfied by a suffix match).

Validated at `a935a26` with the focused security regressions (75 tests) and the
full JVM suite (760 tests, 0 failures). The review follow-up on `CoverDownloader` at
`8d5f40b` (section 2) re-ran the same commands: 77 focused tests and 762
full-suite tests, 0 failures — see section 2 for the controls and section 5 for
the PoC status.

## 2. Hardening added after the audit

- **Reader WebView CSP nonce.** `buildReaderHtml` emits a fresh per-load nonce on
  the `<style>` and `<script>` elements and the CSP no longer allows
  `'unsafe-inline'` for `style-src`/`script-src`. Injected markup cannot execute
  even if a future sink escapes sanitization.
- **A single egress boundary with two destination policies.** `HttpClient`
  (`domain/usecase/webimport/HttpClient.kt`) is the only production
  *programmatic* remote-read path, and it owns URL validation, redirect
  traversal, `PublicOnlyDns`, cookies, timeouts, actual-byte limits and decoding.
  (The Cloudflare challenge WebView is the one remote load outside it; see the
  challenge bullet below.) No production `java.net.URL`,
  `openConnection` or `Jsoup.connect` call remains; the surviving `readText()`
  calls (`SpecFileStore`, `PendingRestoreStore`, `ImportDataUseCase`) read local
  app files or a user-picked SAF document, never a network source. Every call
  selects a policy:
  - `RemoteRequestPolicy.SameNovelDomain(expectedHost)` — crawler pages, chapter
    fetches, covers discovered in fetched HTML, and failed-chapter retries. It
    accepts only HTTPS on the expected host, its `www` form, or a subdomain
    accepted by `StringUtils.hostMatchesDomain`.
  - `RemoteRequestPolicy.AnyPublicHttps` — user-entered cover URLs and the
    MVLEMPYR page, listing and image reads (user-directed features). It accepts
    any HTTPS hostname whose resolved addresses pass `PublicOnlyDns`.

  The expected novel host is derived from the URL the user typed
  (`WebImportViewModel.fetchChapters`) or from `novels.sourceUrl` on retry, before
  any request is issued — never from a challenge URL, a redirect target or a
  page-discovered link.
- **Pre-contact redirect validation.** `HttpClient` sets
  `followRedirects(false)`/`followSslRedirects(false)` on its OkHttp client and
  follows at most `MAX_REDIRECTS = 5` hops itself. Each hop resolves `Location`
  against the current URL, requires the destination to satisfy the request's
  policy, and only then issues the next request — so a cross-domain or
  `https` → `http` target is never contacted. `SameNovelDomain` rejects non-HTTPS
  outright; `AnyPublicHttps` requires HTTPS plus a public DNS answer.
- **Cloudflare challenge bound to the independently derived source host.**
  `WebImportViewModel` computes `expectedHost` from the URL the user typed
  *before* the crawl and carries both values in
  `CloudflareChallenge(url, expectedHost)`; `ImportScreen` passes them unchanged
  and never recomputes the expectation from `challenge.url`.
  `CloudflareChallengeDialog` refuses to construct or load the WebView when
  `isAllowed(url, expectedHost)` fails, disables content and file access, turns off
  third-party cookies, blocks any navigation outside the expected host in
  `shouldOverrideUrlLoading`, and uses the same host for its completion check and
  cookie persistence. This WebView is the only remote load that does not go
  through `HttpClient`: it is contained by `CloudflareChallengePolicy` instead of
  the request-policy, redirect-validation and byte-limit machinery, so the
  `HttpClient` egress rule above applies to programmatic reads only.
- **DNS rebinding guard.** `PublicOnlyDns` (wired into `HttpClient`'s OkHttp
  client) drops loopback, site-local, link-local, CGNAT, unique-local and
  multicast answers. A public hostname that resolves to an internal address is
  rejected with `UnknownHostException`.
- **Response size and decompression-bomb caps, measured on bytes read.**
  `readBounded` counts bytes as they come off the stream, so a chunked body with
  no — or a lying — `Content-Length` aborts at the limit instead of failing open.
  Limits are per request: 8 MiB raw / 16 MiB decompressed by default
  (`DEFAULT_MAX_BODY_BYTES` / `DEFAULT_MAX_DECOMPRESSED_BYTES`), 10 MiB for remote
  covers (`CoverStorage.MAX_REMOTE_COVER_BYTES`, `CoverDownloader`) and for
  MVLEMPYR character images (`MAX_IMAGE_BYTES`), and 1 MiB for one MVLEMPYR
  listing page (`MAX_API_PAGE_BYTES`).
- **Cover and character imports use the hardened client.** `CoverStorage.saveFromUrl`
  no longer opens `java.net.URL` connections and no longer checks only
  `Content-Length`: it calls `HttpClient` with `AnyPublicHttps` and a 10 MiB cap,
  writes `novel_<id>.jpg.tmp`, and renames only after a complete successful write
  (`finally` deletes the temp file), so a blocked, oversized or malformed response
  leaves no destination file. `CoverDownloader` follows the same temp-file-and-
  rename rule for a cover discovered in a fetched page, and pins that download to
  the source host — `SameNovelDomain(expectedHost)` derived from the user-typed
  novel URL, not from the host of the discovered cover URL — so a page-chosen
  cross-domain cover is rejected before it is contacted (`8d5f40b`).
  `MvlempyrCharacterImporter` reads its page, its listing pages and each character's
  `DesignImage` URL through `HttpClient` under the same temp-file-and-rename rule
  and the same limits, and rethrows `CancellationException` instead of swallowing
  it. Only `DesignImage` is downloaded: the `Avatar` field is parsed into
  `ImportedCharacter.avatarUrl` and never fetched, so no `Avatar` URL is requested.
- **Shared crawl request budget.** `RequestBudget(50)` is created once per
  `ChapterCrawler.crawlChapterList` and shared by the main page loop and both list
  augmenters (`ReadNovelFullListAugmenter`, `FreewebnovelListAugmenter`); every
  request consumes a slot before it is issued. Exhaustion returns the links
  collected so far rather than continuing in the background.
  `FreewebnovelListAugmenter` additionally accepts a declared `pageSize` only in
  `1..200`, caps accumulated augmented links at 10,000, stops after three
  consecutive non-200/invalid-JSON responses, and applies the 1.5 s pacing delay
  after every attempted extra page — failures included.
- **Bounded MVLEMPYR pagination.** The WordPress listing uses `per_page=100` with
  at most 50 pages / 5,000 entries, stops on an empty page and after three
  consecutive HTTP or JSON failures, and caps each listing response at 1 MiB
  before `JSONArray` parsing, so a remote `totalPages`/`per_page` value cannot
  size an unbounded read or loop.

## 3. Dependency CVE audit (2026-09-09)

| Dependency | Version | Status |
|---|---|---|
| `org.jsoup:jsoup` | 1.23.2 | **CVE-2026-71497 cleared** (fixed in 1.23.1). The advisory was XSS via parser/browser desynchronization, only reachable when a custom `Safelist` permits raw-text/RCDATA elements (`style`, `title`, `iframe`, …); `READER_SAFELIST` allows only `p`, `h1`–`h6`, `br`, `strong`, `em`, `b`, `i`, `u`, `sub`, `sup`, so the precondition never applied here. The bump is verified by the parser fixtures under `app/src/test/resources/`. |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | No CVE found for the core client. The known brotli issue (CVE-2023-3782) affects `okhttp-brotli`, which this project does not use. The project's own brotli path is now bounded (section 2). |
| `io.coil-kt:coil-compose` | 2.7.0 | No known CVE. |
| `org.brotli:dec` | 0.1.2 | No known CVE; decompression is bounded (section 2). |
| Android WebView | system | Not pinned by the app; updated through the Play Store on the device. |

Recommended follow-up: none open for jsoup. The 1.22.1 → 1.23.2 bump (2026-09-14)
landed together with the parser fixtures passing unchanged. `Jsoup` is now used for
parsing only — the former `Jsoup.connect` call site (`MvlempyrCharacterImporter`)
reads through `HttpClient` (section 2), so no `Jsoup` code path performs network
I/O any more.

### 3.1 npm dependency audit (`landing-page/`, 2026-09-10)

`npm audit` on the Vite landing page project reported four advisories. All are
build-time transitive dependencies and all had a non-breaking fix, so
`npm audit fix` (no `--force`) resolved them by updating the lockfile only —
`package.json` ranges are unchanged and no direct dependency moved.

| Dependency | Before | After | Severity |
|---|---|---|---|
| `postcss` | 8.5.15 | 8.5.28 | high |
| `browserslist` | 4.28.2 | 4.28.9 | high |
| `nanoid` | 3.3.13 | 3.3.18 | high |
| `baseline-browser-mapping` | 2.10.38 | 2.11.21 | moderate |

Advisories cleared:

- `postcss` — GHSA-r28c-9q8g-f849 / GHSA-fxqj-rqcc-2cmp: path traversal in
  `sourceMappingURL` auto-loading discloses arbitrary `.map` files.
- `browserslist` — GHSA-c83g-rgw3-j3cx (unbounded cache growth → OOM) /
  GHSA-73wf-gq98-2v4g (prototype write via untrusted `browserslist-stats.json`).
- `nanoid` — GHSA-28wg-ghj8-5hjv / GHSA-2v37-7h3g-55p8: non-secure generators
  loop indefinitely on negative or zero size.
- `baseline-browser-mapping` — GHSA-w5vr-8v7q-w6rv: process termination on
  invalid input (DoS).

`update-browserslist-db` (1.2.3 → 1.3.2) moved with `browserslist`, and
`caniuse-lite`, `electron-to-chromium` and `node-releases` were refreshed as a
consequence — data-only packages. None of these reach the deployed bundle: Vite
and PostCSS consume them at build time, so the exposure is limited to the
build machine.

`postcss` is the only advisory with a plausible path to real impact, and even
then it needs an attacker-controlled `sourceMappingURL` in CSS the build
ingests; the landing page builds from first-party sources only.

Verification: `npm audit` now reports 0 vulnerabilities, and `npm run build`
emits byte-identical assets (`index-BuEzwT5_.css`, `index-0s6zsQ2d.js`) before
and after the bump, confirming the rendered output is unchanged.

## 4. Known residual risks (accepted / deferred)

- **Legacy JS bridge.** `ReaderWebView` still uses
  `addJavascriptInterface` + `@JavascriptInterface`. On API 26+ only the four
  annotated methods are exposed, and the injection path is closed by content
  sanitization, settings allowlisting and the CSP nonce. Migrating to
  `WebViewCompat.addWebMessageListener` would require a new `androidx.webkit`
  dependency and a bridge-contract rewrite; it is deferred rather than done
  opportunistically.
  - Re-assessed 2026-09-14, and the migration is **not** a risk reduction here.
    The frame/origin scoping it buys guards a configuration this WebView cannot
    reach: it loads only `loadDataWithBaseURL("https://reader.local/load/<token>/", ...)`,
    with `blockNetworkLoads = true`, `shouldOverrideUrlLoading` returning `true`,
    `setSupportMultipleWindows(false)`, file/content access off and no DOM
    storage, so no foreign origin or frame can ever be loaded into it. The only
    bridge in the app is this one: the Cloudflare challenge dialog, which does
    load remote content, has no `addJavascriptInterface`.
  - A script injected into the reader page sits in the main frame of the allowed
    origin, so `Android.postMessage(...)` would be exactly as callable as the
    annotated methods. Origin scoping and caller attribution therefore reduce
    nothing against the threat this stack actually faces.
  - What the migration *would* buy is hygiene, not safety: it is the API Google
    recommends, so it removes a permanent scanner finding, and it closes a
    latent trap — if this WebView ever loads a remote URL or an iframe, the
    legacy object is handed to every frame at once, while a listener would need
    an origin match. Revisit it if that changes.
  - Exposure ceiling if the sanitizer and the CSP nonce both failed: toggling the
    options bar, forcing chapter navigation, stopping auto-scroll, and setting
    `isPageLoaded`. No file, DB, network or secret access. The bridge arguments
    are not validated (`chapterTransitionFor` maps any unknown `direction` to
    `FROM_RIGHT` and `ReaderScreen` navigates on the `else` branch), so the
    cheap hardening is to allowlist `direction`/`axis` at the bridge and return
    `ChapterTransition.NONE` for unknown input — not to migrate.
- **DNS rebinding scope.** `PublicOnlyDns` filters answers but does not pin a
  single resolved address for the connection; a re-resolving attacker could
  still race. Full protection would need connection-time IP pinning.
- **Notification token rotation.** Notifications posted by older builds lack the
  token, so their tap no longer navigates until the notification is re-posted.
- **Coverage.** The piolium pass was lite (grep + source read): no CodeQL/Semgrep
  dataflow and no SpotBugs/FindSecBugs. The `landing-page/` npm dependency tree
  has since been audited with `npm audit` (section 3.1), but its source has still
  not been reviewed by an audit tool.
- **WorkManager job inputs** were read only at a glance; no untrusted-input path
  into job specs was proven or disproven.

## 5. Re-running the audit

The piolium artifacts live in the gitignored `piolium/` directory
(`attack-surface/`, `findings/`, `audit-state.json`). Re-run the lite audit with
the piolium CLI from the repository root, then compare new findings against this
file and the `findings/` directory before opening fixes.

The executed PoCs under `findings/` are **historical exploit harnesses**: they
embed the pre-fix constructor signatures and source lists (M2/M4 omit
`RemoteRequestPolicy.kt` and `RequestBudget.kt`; M3 calls the two- and
four-argument `CoverStorage`/`MvlempyrCharacterImporter` constructors) and they
assert the vulnerable behaviour. They no longer compile or pass against the fixed
source, and they must not be rewritten into passing tests; the JVM regressions
listed in section 2 are the post-fix evidence. Keep them byte-identical as audit
record.
