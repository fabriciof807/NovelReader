# Security Audit Remediation Design

## Status

Approved in chat on 2026-09-17. This design remediates the four open Medium findings in `piolium/final-audit-report.md`, assembled against commit `5715f540cb2316cf089389adf4bbe9640015fc2c`:

- `M1-cloudflare-challenge-host-allowlist-tautology`
- `M2-page-controlled-crawler-outbound-destination`
- `M3-unguarded-raw-url-egress-and-cap-bypass`
- `M4-remote-controlled-unbounded-pagination-loop`

The report and its PoCs are audit evidence. Current source and regression tests remain authoritative for implementation behavior.

## Goals

- Make the existing hardened HTTP client the single boundary for production remote reads.
- Validate every initial URL and redirect destination before issuing a request.
- Pin page-discovered navigation and resources to the user-selected novel domain.
- Prevent non-public network access and oversized response writes on cover and character-import paths.
- Bound every page-controlled loop and share one request budget across chapter-list crawling and augmentation.
- Preserve normal imports, existing parser behavior, and public-host manual image downloads.

## Non-goals

- Replacing the reader JavaScript bridge.
- Introducing a repository layer or migrating the networking package hierarchy.
- Supporting arbitrary third-party CDNs for URLs discovered in novel HTML.
- Publishing, tagging, pushing, or merging the release as part of remediation.
- Addressing Low or deferred audit observations outside the four open findings.

## Security Model

### Destination policies

Every production remote read uses one of two explicit policies:

- `SameNovelDomain(expectedHost)`: only HTTPS URLs matching the independently derived novel host, including its `www` form and subdomains accepted by `StringUtils.hostMatchesDomain`.
- `AnyPublicHttps`: any HTTPS hostname whose resolved addresses pass `PublicOnlyDns`.

Crawler pages, chapter fetches, Cloudflare challenges, and covers discovered in fetched HTML use `SameNovelDomain`. Manual cover URLs and MVLEMPYR character images use `AnyPublicHttps`.

The expected novel host is derived from the user-entered source URL before fetching begins. It is never derived from a challenge URL, redirect target, or page-discovered link.

### Redirects

`HttpClient` disables OkHttp automatic redirects and follows redirects itself. Before each hop it:

1. resolves `Location` against the current URL;
2. requires HTTPS;
3. checks the request destination policy;
4. enforces a fixed redirect limit;
5. issues the next request only after validation succeeds.

This prevents cross-domain and HTTPS-to-HTTP redirect requests rather than detecting them after contact. `PublicOnlyDns` remains active for every resolved destination.

### Response limits

The client counts bytes actually read. It does not trust `Content-Length`. Limits are selected per request:

- existing HTML default: 8 MiB raw, 16 MiB decompressed;
- cover and character image: 10 MiB;
- MVLEMPYR listing page: a smaller explicit JSON limit suitable for 100 records.

Oversized or malformed responses abort before persistence. Download callers write validated response bytes to a temporary file and rename only after a complete successful write, or otherwise avoid creating the destination. No partial file remains after failure.

## Components and Data Flow

### Hardened `HttpClient`

Extend the existing client rather than creating parallel networking implementations. The request API accepts:

- URL;
- referrer and extra headers;
- destination policy;
- raw and decompressed body limits.

The client owns URL validation, manual redirects, `PublicOnlyDns`, cookies, timeouts, actual-byte limits, and response decoding. Existing test constructors continue to accept an injected OkHttp client so local MockWebServer tests can use controlled DNS and HTTP where explicitly configured for tests.

### Chapter crawler and fetcher

`ChapterCrawler` derives the expected host once from `homeUrl`. It uses that host for:

- every list-page request;
- next-page URL resolution and filtering;
- chapter-link filtering;
- cover URL filtering;
- Cloudflare challenge expectation.

Relative URLs are resolved with URI semantics. A resolved absolute URL is discarded if it does not match the expected host.

`ChapterFetcher` applies `SameNovelDomain` using the stored chapter URL host. Chapter URLs already pass the crawler's novel-domain filter; this second check prevents redirects from escaping that host during later imports or retries.

### Cloudflare solver

`CloudflareChallenge` carries both `url` and the independently derived `expectedHost`. `ImportScreen` passes those values unchanged to `CloudflareChallengeDialog`; it never computes the expectation from `challenge.url`.

The dialog:

- refuses to construct or load the WebView when the pre-load policy fails;
- disables content access;
- disables third-party cookies;
- blocks navigation outside the expected host in `shouldOverrideUrlLoading`;
- uses the same expected host for its completion check and cookie persistence flow.

### Cover storage

`CoverStorage.saveFromUrl` uses `HttpClient` with `AnyPublicHttps` and a 10 MiB limit for user-entered cover URLs. It no longer opens `java.net.URL` connections or checks only `Content-Length`.

Covers discovered by `ChapterCrawler` are filtered to the novel domain before reaching the download stage. `CoverDownloader` also applies `SameNovelDomain` to the discovered cover URL so redirects cannot escape.

### MVLEMPYR character import

`MvlempyrCharacterImporter` uses `HttpClient` for:

- the selected novel page;
- the WordPress character listing;
- character design images.

The selected page uses `AnyPublicHttps`, preserving the user-directed feature while retaining DNS and body protections. The fixed API endpoint and image URLs also require public HTTPS destinations.

The API changes from `per_page=15000` to bounded pagination:

- 100 entries per page;
- at most 50 pages and 5,000 entries;
- stop on an empty page;
- stop after three consecutive HTTP or JSON failures;
- enforce a per-response JSON byte limit before constructing `JSONArray`.

### Shared crawl request budget

Introduce `RequestBudget(limit = 50)`. Each chapter-list network request consumes one slot before execution. The same instance is shared by:

- the main `ChapterCrawler` page loop;
- `ReadNovelFullListAugmenter`;
- `FreewebnovelListAugmenter`.

`NovelListAugmenter.augment` receives the budget explicitly. No augmenter may create its own unbounded loop outside this budget.

`FreewebnovelListAugmenter` also:

- accepts `pageSize` only in `1..200`;
- limits declared pages to the remaining shared budget;
- caps accumulated augmented links at 10,000;
- stops after three consecutive non-200, invalid JSON, or invalid application responses;
- applies the 1.5-second pacing delay after every attempted extra page, including failures.

Budget exhaustion returns the links safely collected so far, matching the crawler's existing capped behavior.

## Error Handling

- URL-policy failures are represented internally as specific security exceptions.
- Required HTML fetches surface through existing import error flows without leaking internal network details.
- Optional cover and image downloads return `null` and leave no partial file when blocked, oversized, malformed, or unavailable.
- Missing or invalid expected challenge hosts fail closed and show the existing blocked state.
- Invalid page-controlled pagination state is ignored rather than trusted.
- Request-budget exhaustion returns bounded partial results; it does not continue in the background.

## Test Strategy

Implementation follows test-driven development. Each security regression test must fail before its corresponding production change.

### Challenge tests

- ViewModel challenge state carries the host from the original typed URL.
- A foreign challenge URL is rejected despite being HTTPS.
- Off-domain WebView navigation is blocked.
- Missing expected host prevents WebView loading.

### Redirect and origin tests

- A cross-domain `rel=next` link is not requested.
- A same-domain relative next link still works.
- A cross-domain discovered cover is omitted.
- A same-domain redirect succeeds within the hop limit.
- A cross-domain redirect under `SameNovelDomain` is rejected before the foreign server receives a request.
- HTTPS downgrade and excess redirects are rejected.

### Egress and size tests

- `HttpClient` rejects oversized chunked bodies by actual bytes read.
- Production DNS policy blocks loopback/private destinations.
- `CoverStorage.saveFromUrl` uses the hardened client, enforces 10 MiB, and leaves no partial file.
- MVLEMPYR page, API, and image reads use the hardened client and enforce limits.
- Bounded API pagination stops at empty pages, repeated failures, and its hard cap.

### Crawl budget tests

- Main crawling and augmenters share one 50-request budget.
- Page-controlled `totalPage` cannot exceed the budget.
- Error responses are paced and stop after three consecutive failures.
- Accumulated augmented links remain bounded.

### Regression validation

Run:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Also run the focused test classes after each finding is fixed and re-run compatible PoC/regression harnesses from `piolium/findings/`. Existing parser fixtures must remain unchanged.

## Documentation and Audit Closure

After implementation and validation:

- update `docs/security-residual-risks.md` so the Cloudflare, DNS, response-limit, redirect, and bounded-pagination claims match actual behavior;
- record the four findings as remediated without deleting the original audit evidence;
- inspect the final diff for unrelated changes;
- leave release versioning and publication for a separate explicitly authorized step.

## Acceptance Criteria

- No production `java.net.URL` or `Jsoup.connect` remote egress remains.
- Every production remote read passes through the hardened client.
- Page-discovered destinations cannot leave the novel domain.
- Redirects are validated before contact and cannot downgrade HTTPS.
- Non-public DNS answers and oversized chunked bodies are blocked.
- Chapter-list crawling and augmentation share a 50-request ceiling.
- MVLEMPYR pagination and image writes are bounded.
- All focused regression tests and the full compile/unit suite pass.
- Security documentation accurately describes the implemented controls.
