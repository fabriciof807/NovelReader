# Security notes — residual risks and dependency audit

Last updated: 2026-09-10 (v2.9.2).

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

## 2. Hardening added after the audit

- **Reader WebView CSP nonce.** `buildReaderHtml` emits a fresh per-load nonce on
  the `<style>` and `<script>` elements and the CSP no longer allows
  `'unsafe-inline'` for `style-src`/`script-src`. Injected markup cannot execute
  even if a future sink escapes sanitization.
- **Cloudflare challenge pre-load allowlist.** `CloudflareChallengePolicy` only
  permits `https` URLs whose host matches the expected novel domain (with
  `www.`/subdomain handling) before the challenge WebView loads them. Previously
  the host check ran only after the page had loaded.
- **DNS rebinding guard.** `PublicOnlyDns` (wired into `HttpClient`'s OkHttp
  client) drops loopback, site-local, link-local, CGNAT, unique-local and
  multicast answers. A public hostname that resolves to an internal address is
  rejected with `UnknownHostException`.
- **Response size and decompression-bomb caps.** `HttpClient` reads at most
  8 MiB of the raw body and 16 MiB after gzip/brotli/deflate decoding
  (`DEFAULT_MAX_BODY_BYTES` / `DEFAULT_MAX_DECOMPRESSED_BYTES`). A malicious
  server can no longer exhaust memory with a huge or highly compressible body.

## 3. Dependency CVE audit (2026-09-09)

| Dependency | Version | Status |
|---|---|---|
| `org.jsoup:jsoup` | 1.23.2 | **CVE-2026-71497 cleared** (fixed in 1.23.1). The advisory was XSS via parser/browser desynchronization, only reachable when a custom `Safelist` permits raw-text/RCDATA elements (`style`, `title`, `iframe`, …); `READER_SAFELIST` allows only `p`, `h1`–`h6`, `br`, `strong`, `em`, `b`, `i`, `u`, `sub`, `sup`, so the precondition never applied here. The bump is verified by the parser fixtures under `app/src/test/resources/`. |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | No CVE found for the core client. The known brotli issue (CVE-2023-3782) affects `okhttp-brotli`, which this project does not use. The project's own brotli path is now bounded (section 2). |
| `io.coil-kt:coil-compose` | 2.7.0 | No known CVE. |
| `org.brotli:dec` | 0.1.2 | No known CVE; decompression is bounded (section 2). |
| Android WebView | system | Not pinned by the app; updated through the Play Store on the device. |

Recommended follow-up: none open for jsoup. The 1.22.1 → 1.23.2 bump (2026-09-14)
landed together with the parser fixtures passing unchanged; the only
`Jsoup.connect` call site (`MvlempyrCharacterImporter`) keeps `followRedirects(true)`
under the specification-correct redirect handling introduced in 1.23.1.

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
