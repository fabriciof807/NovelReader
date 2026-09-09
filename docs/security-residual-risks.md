# Security notes — residual risks and dependency audit

Last updated: 2026-09-09 (v2.9.2).

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
| `org.jsoup:jsoup` | 1.22.1 | **CVE-2026-71497** (medium, CVSS 4.7): XSS via parser/browser desynchronization, but only when a custom `Safelist` permits raw-text/RCDATA elements (`style`, `title`, `iframe`, …). `READER_SAFELIST` allows only `p`, `h1`–`h6`, `br`, `strong`, `em`, `b`, `i`, `u`, `sub`, `sup`, so the precondition is not met. Upgrade to a 1.23.x release to clear the advisory. |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | No CVE found for the core client. The known brotli issue (CVE-2023-3782) affects `okhttp-brotli`, which this project does not use. The project's own brotli path is now bounded (section 2). |
| `io.coil-kt:coil-compose` | 2.7.0 | No known CVE. |
| `org.brotli:dec` | 0.1.2 | No known CVE; decompression is bounded (section 2). |
| Android WebView | system | Not pinned by the app; updated through the Play Store on the device. |

Recommended follow-up: bump jsoup to 1.23.x (verify the reader/parser fixtures
after the bump).

## 4. Known residual risks (accepted / deferred)

- **Legacy JS bridge.** `ReaderWebView` still uses
  `addJavascriptInterface` + `@JavascriptInterface`. On API 26+ only the four
  annotated methods are exposed, and the injection path is closed by content
  sanitization, settings allowlisting and the CSP nonce. Migrating to
  `WebViewCompat.addWebMessageListener` would require a new `androidx.webkit`
  dependency and a bridge-contract rewrite; it is deferred rather than done
  opportunistically.
- **DNS rebinding scope.** `PublicOnlyDns` filters answers but does not pin a
  single resolved address for the connection; a re-resolving attacker could
  still race. Full protection would need connection-time IP pinning.
- **Notification token rotation.** Notifications posted by older builds lack the
  token, so their tap no longer navigates until the notification is re-posted.
- **Coverage.** The piolium pass was lite (grep + source read): no CodeQL/Semgrep
  dataflow, no SpotBugs/FindSecBugs, and `landing-page/` was not audited.
- **WorkManager job inputs** were read only at a glance; no untrusted-input path
  into job specs was proven or disproven.

## 5. Re-running the audit

The piolium artifacts live in the gitignored `piolium/` directory
(`attack-surface/`, `findings/`, `audit-state.json`). Re-run the lite audit with
the piolium CLI from the repository root, then compare new findings against this
file and the `findings/` directory before opening fixes.
