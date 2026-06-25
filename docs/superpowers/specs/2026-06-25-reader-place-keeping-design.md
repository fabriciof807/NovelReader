# NovelReader Reader "Perdi Meu Lugar" — Design

**Date:** 2026-06-25
**Scope:** Fix the "I lost my place" cluster in the reader — H1 (no scroll save on lifecycle), H4 (settings change snaps to stale scroll), H2 (rotation recreates Activity and loses the live WebView), and B3 (every bookmark write triggers a full HTML reload). Subproducts: unify the two scroll-ratio formulas (M6), make auto-scroll speed adjustable live (partial M8), regenerate the drifted `reader.css` (L1), and add `singleTop` to the Activity (B9).
**Approach:** Single feature, one logical change. Can land in one commit or be split if the diff is large. Direct to `main`.

---

## Decisions

- **H4 — settings without reload.** Apply ReaderConfig changes via a JS function `applyConfig(cfg)` that updates the `:root` CSS variables (`--bg-color`, `--text-color`, `--accent-color`, `--link-color`, `--font-family`, `--font-size`, `--line-height`) and calls a `setAutoScrollSpeed(speed)` helper to update the auto-scroll JS state without a reload. No `loadDataWithBaseURL` for settings changes.
- **B3 — bookmarks without reload.** Stop baking `.bookmarked` / `.bookmark-indicator` into the initial HTML. Apply bookmarks via a JS function `applyBookmarks(positionsJson)` that walks `#content p` by index and injects/removes the indicator span + `bookmarked` class — reproducing the existing dedup behavior.
- **H1 — live scroll capture.** `onScrollChanged` callback now also calls `viewModel.updateLiveScroll(ratio)`, which writes `lastKnownScrollPosition` in-memory (no DB). A `LifecycleEventObserver` on `ON_PAUSE` calls `viewModel.saveScrollPosition()` (no-arg overload) which uses the live value to persist via `chapterDao.markAsRead`. `ON_RESUME` calls `webView.onResume()`. The existing explicit `saveScroll()` call sites (back, prev/next, swipe, chapter-list tap, bookmark click) stay in place — they are now redundant but cheap and reduce risk.
- **H2 — rotation.** Add `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|density|fontScale"` to `MainActivity` in `AndroidManifest.xml`. The Activity is no longer recreated on rotation; the WebView (`remember { mutableStateOf<WebView?>(null) }`) and its in-memory scroll survive natively. Compose recomposes via `LocalConfiguration` / `LocalDensity`.
- **`launchMode="singleTop"`** — add alongside `configChanges` (B9). `onNewIntent` already exists; `singleTop` makes deep-link delivery reliable.
- **Remove `ReaderState.reloadVersion` entirely.** `LaunchedEffect(state.chapter, webView)` already covers the only remaining rebuild trigger (chapter content swap). The config and bookmark changes are applied reactively in their own `LaunchedEffect`s and no longer need a version counter.
- **`reader.css` is regenerated as reference documentation.** The file at `app/src/main/assets/css/reader.css` is never loaded (the runtime uses the inline `<style>` in `ReaderHtmlBuilder.kt`). Regenerate it from the inline source for parity, with a header comment explaining it is reference-only.
- **Scroll-ratio formula unification (M6).** `saveScroll()` and `getScrollRatio()` use the viewport-aware ratio that `onScrollChanged` already computes (`scrollY / (contentHeight*scale - height)`). The legacy JS `window.scrollY / document.body.scrollHeight` formula is gone. The progress bar at the bottom of the screen and the persisted position are now computed from the same source.

---

## Architecture overview

ReaderConfig and bookmark changes are no longer treated as "reload the HTML." They are treated as live updates applied through JS. The HTML is rebuilt only when the chapter content itself changes.

```
ReaderPreferences.config (Flow)           bookmarkDao.getByChapter (Flow)
        │                                            │
        ▼                                            ▼
ReaderViewModel.state.config              ReaderViewModel.state.bookmarks
        │                                            │
        ▼                                            ▼
LaunchedEffect(state.config, webView)     LaunchedEffect(state.bookmarks, webView)
        │                                            │
        ▼                                            ▼
webView.evaluateJavascript(               webView.evaluateJavascript(
  applyConfig(configJson))                  applyBookmarks(positionsJson))

loadChapter(chapterId) ─▶ state.chapter ─▶ LaunchedEffect(state.chapter, webView)
                                              │
                                              ▼
                                       loadDataWithBaseURL(html)
                                       (only rebuild path)
```

The lifecycle observer owns the persistent save:

```
ON_PAUSE  ─▶ viewModel.saveScrollPosition() + webView.onPause()
ON_RESUME ─▶ webView.onResume()
dispose   ─▶ webView.destroy()
```

The WebView instance is the same `remember { mutableStateOf<WebView?>(null) }` that already exists in `ReaderScreen.kt:85`. With `configChanges` declared, the composable is not torn down on rotation, so the WebView and its in-memory scroll survive without re-creation.

---

## Per-feature

### H1 — Live scroll capture and lifecycle save

- **`ReaderViewModel` additions:**
  - `fun updateLiveScroll(ratio: Float)` — sets `lastKnownScrollPosition = (ratio * 1000).toInt()`. No DB write, no `StateFlow` update (this is internal-only state used by `saveScrollPosition` and the bookmark title heuristic).
  - `fun saveScrollPosition()` (new no-arg overload) — persists the current `lastKnownScrollPosition` via `chapterDao.markAsRead(currentChapter.id, lastKnownScrollPosition)`. Same error handling pattern (try / `errorEvents.emit`) as the existing `saveScrollPosition(scrollRatio: Float)`.
  - Existing `fun saveScrollPosition(scrollRatio: Float)` keeps its body (update `lastKnownScrollPosition`, persist). Both overloads update the same in-memory field, so either path stays consistent.

- **`ReaderScreen` changes:**
  - `ReaderWebView`'s `onScrollChanged` callback now does: `viewModel.updateLiveScroll(ratio)` in addition to updating local `scrollRatio` state used by the progress bar.
  - `saveScroll()` helper (`ReaderScreen.kt:119`) drops the `webView.evaluateJavascript(...)` step and becomes a single call: `viewModel.saveScrollPosition()`. The JS-based ratio read is no longer needed because the live ratio is already in the VM.
  - The `DisposableEffect(Unit)` block at `ReaderScreen.kt:563` becomes a `DisposableEffect` that owns a `LifecycleEventObserver` attached to `LocalLifecycleOwner.current`:
    - `onStateChanged`: dispatch on `ON_PAUSE` → `viewModel.saveScrollPosition()` + `webView?.onPause()`; `ON_RESUME` → `webView?.onResume()`.
    - `onDispose`: remove observer + `webView?.destroy()` (preserve current destroy behavior).
  - All existing call sites of `saveScroll()` stay (back, prev/next, swipe, chapter-list tap, bookmark click). They are now belt-and-suspenders with the lifecycle save.

- **Subproduct (M6):** with `saveScrollPosition()` using the viewport-aware ratio from `onScrollChanged`, the persisted `currentChapter.lastScrollPosition` and the in-memory `lastKnownScrollPosition` share one formula. `getScrollRatio()` returns `lastScrollPosition / 1000f` which is now the same value the progress bar displays.

### H4 — Settings without reload

- **`ReaderHtmlBuilder` changes (`ReaderHtmlBuilder.kt`):**
  - Extract a `themeVars(config: ReaderConfig): Map<String, String>` function that produces the four theme color keys (`bgColor`, `textColor`, `accentColor`, `linkColor`) and their hex values from the existing `when` block. The inline `:root` CSS formats the map as `key: value;` lines; the `applyConfig` JS payload serializes the same map as a JSON object. Single source of truth for the theme palette.
  - Add two JS function bodies to the `<script>` block of the generated HTML:
    - `applyConfig(cfg)` — reads `cfg.bgColor`, `cfg.textColor`, etc. and calls `document.documentElement.style.setProperty('--bg-color', cfg.bgColor)` for each CSS variable, then calls `setAutoScrollSpeed(cfg.autoScrollSpeed)`.
    - `setAutoScrollSpeed(speed)` — assigns to the existing `_asSpeed` variable. If `speed > 0` and `!_asRunning`, call `startAutoScroll()`. If `speed <= 0`, call `stopAutoScroll()`. The existing `touchstart`/`touchend` pause logic and the existing auto-scroll loop stay intact.
  - The auto-scroll JS body itself stays inside the generated `<script>` — only `setAutoScrollSpeed` is added next to it.

- **`ReaderScreen` changes:**
  - New `LaunchedEffect(state.config, webView) { webView?.evaluateJavascript(applyConfigJs(state.config), null) }` — fires on every config change after the WebView is ready. No `loadDataWithBaseURL`.
  - Helper function `applyConfigJs(config: ReaderConfig): String` builds the JSON payload (bgColor, textColor, accentColor, linkColor, fontFamily, fontSize, lineHeight, autoScrollSpeed) and returns a wrapped IIFE that calls `applyConfig(JSON.parse(args))`. Reuse the existing `buildJs(code, params)` helper that already lives in `ReaderScreen.kt:97`.

- **`ReaderViewModel` changes:**
  - `updateTheme`, `updateFontSize`, `updateLineHeight`, `updateAutoScrollSpeed` each become a single line: `viewModelScope.launch { readerPreferences.updateX(value) }`. The `reloadVersion` bump is removed.
  - The existing `init` block collector at `ReaderViewModel.kt:81-85` keeps doing `_state.value.copy(config = config)` so the `LaunchedEffect(state.config)` re-fires when the Flow re-emits.

- **Page-load ordering:** the WebView's DOM is only ready for JS calls after `onPageFinished` fires. Use a `var isPageLoaded by remember { mutableStateOf(false) }` flag. The chapter-swap `LaunchedEffect` resets it to `false` before calling `loadDataWithBaseURL`. The `onPageFinished` callback (the existing `ReaderWebView.onPageFinished` argument, currently `{ _, _ -> }`) is expanded to: set `isPageLoaded = true`, then call `applyConfigJs(state.config)` and `applyBookmarksJs(state.bookmarks)` via `evaluateJavascript`, then restore the scroll position via `viewModel.getScrollRatio()`. The `LaunchedEffect(state.config, webView)` and `LaunchedEffect(state.bookmarks, webView)` short-circuit (early return) when `!isPageLoaded` so they never run against an empty DOM. Their keying on `state.config` / `state.bookmarks` is what makes them re-fire when the user changes a setting or adds a bookmark, which is the intended behavior.

### B3 — Bookmarks without reload

- **`ReaderHtmlBuilder` changes:**
  - Drop the `bookmarksScrollPositions` parameter from `buildReaderHtml` and the call to `insertBookmarkInContent` inside it. The `finalContent` is just `stripJunkContent(sanitized)`.
  - Remove the now-dead `insertBookmarkInContent` private function (it has no other consumer; the bookmark logic is reproduced in JS).
  - Update the call site in `ReaderScreen.kt:132` to match the new two-argument `buildReaderHtml(content, config)` signature.
  - Add an `applyBookmarks(positionsJson)` JS function body in the generated `<script>`:
    - Parse `positionsJson` (JSON array of `scrollPosition` ints).
    - Remove all existing `.bookmark-indicator` elements from `#content` and the `.bookmarked` class from every `p`.
    - For each position, compute `idx = Math.floor((pos / 1000) * paragraphs.length)` (clamped to `[0, paragraphs.length - 1]`). Use a `used` set to dedup (mirrors the current Kotlin logic at `ReaderHtmlBuilder.kt:216`).
    - For the chosen `<p>`, prepend the inline SVG `<span class="bookmark-indicator">` (same SVG string as today) and add the `bookmarked` class.

- **`ReaderScreen` changes:**
  - New `LaunchedEffect(state.bookmarks, webView) { webView?.evaluateJavascript(applyBookmarksJs(state.bookmarks), null) }`.
  - Helper `applyBookmarksJs(bookmarks: List<BookmarkEntity>): String` builds the JSON array of `scrollPosition` values and returns the IIFE that calls `applyBookmarks(JSON.parse(args))`.
  - The same `isPageLoaded` flag from the H4 section controls when this `LaunchedEffect` runs.

- **`ReaderViewModel` changes:**
  - `collectBookmarks` (lines 131-141) drops the `reloadVersion` bump. Body becomes: `_state.value = _state.value.copy(bookmarks = list)`.

### H2 — Rotation via `configChanges`

- **`AndroidManifest.xml`:** add `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|density|fontScale"` and `android:launchMode="singleTop"` to the `MainActivity` declaration. Other attributes unchanged.
- **No code change in `MainActivity`:** the `onCreate` / `onNewIntent` paths are already correct. `onConfigurationChanged` is not needed — Compose handles configuration changes via `LocalConfiguration` and the WebView is layout-passive.
- **Caveat documented (not a blocker):** opting into `density` and `fontScale` means the activity no longer recreates when the system font size changes. Compose text and the WebView (whose font size is controlled by `ReaderConfig`, not by system font scale) both handle this through recomposition. The reader's font size is the user's preference, not the system scale.

### `reader.css` regeneration (L1)

- Regenerate `app/src/main/assets/css/reader.css` to match the inline CSS currently in `ReaderHtmlBuilder.kt:61-134` (theme vars, font family, font size, line height, padding, max-width, body, `#content`, paragraphs, headings 1-4, images, `.bookmarked`, `.bookmark-indicator`, `.search-highlight`, `@keyframes pulse`).
- Add a header comment: this file is reference documentation. The runtime source of truth is the inline CSS in `ReaderHtmlBuilder.kt`. Do not edit the file expecting runtime changes.
- `loadDataWithBaseURL(null, ...)` in `ReaderScreen.kt` stays — the file is not loaded.

---

## Cross-cutting

- **Error handling:** No new error paths. `saveScrollPosition()` (no-arg) reuses the same `try { chapterDao.markAsRead(...) } catch (e) { _errorEvents.emit(...) }` pattern that the existing overload uses.
- **Strings:** No new string resources. All changes are code / manifest / asset regeneration.
- **Testing strategy (TDD):**
  - **`ReaderViewModelTest`** — new and updated cases:
    - `updateTheme/FontSize/LineHeight/AutoScrollSpeed` only call the corresponding `readerPreferences.updateX` and do not mutate `state` (no `reloadVersion` field exists to assert on; assert that no unexpected state copy happens beyond the config collection path).
    - `updateLiveScroll(ratio)` updates `lastKnownScrollPosition` and does not touch `chapterDao`.
    - `saveScrollPosition()` (no-arg) calls `chapterDao.markAsRead(chapterId, lastKnownScrollPosition)` with the in-memory value.
    - `saveScrollPosition(ratio: Float)` still works (overload preserved for explicit call sites).
    - `collectBookmarks` emission updates `state.bookmarks` and does not bump any version field.
  - **`ReaderHtmlBuilderTest`** — new and updated cases:
    - Generated HTML does not contain `class="bookmark-indicator"` in the initial build (bookmark markers are now JS-applied).
    - Generated HTML does contain `function applyConfig(` and `function applyBookmarks(` definitions.
    - `themeVars(config)` returns the expected color string for each of the four themes.
    - The auto-scroll script body is still emitted when `autoScrollSpeed > 0f`.
  - **`ReaderScreen` Compose UI test (instrumented, optional):** add a test that advances scroll, dispatches `ON_PAUSE` via `LifecycleRegistry`, and verifies that the chapter's `lastScrollPosition` was written. Run on emulator.
  - **Manual QA on device:** slider drag for font size / line height / theme should not cause scroll snap; adding/removing a bookmark should not cause scroll snap; rotating the device should preserve the chapter and scroll position.
- **Commit plan:** one commit covering the entire change, with a message that names the cluster: `fix: reader "perdi meu lugar" — live scroll + lifecycle save + JS-injected settings/bookmarks + configChanges`. Conventional-commit style. Direct to `main`.
- **Sequence for verification:** before the commit, run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` (per `AGENTS.md`). The instrumented lifecycle test and the manifest `configChanges` / `singleTop` attributes are not testable via unit tests; manual / device verification covers them.

---

## Out of scope (explicit follow-ups)

- **H3 — keep-screen-on.** High value, independent. Next session or a follow-up in this one after this lands.
- **H5 — sync reader theme with app/system theme.** Reader currently always defaults to `"light"`. The `applyConfig` JS function makes a "follow system" option cheap to add, but it is not in scope for this cluster.
- **M1 — search-result navigation stacks reader entries** on the back stack (`NavGraph.kt:113`). Pure nav fix.
- **M2 / M3 — FTS highlight finds only the first match and the search panel covers the page.** Needs a `next/previous match` UI and a "next match" JS walker.
- **M4 / M5 — chapter picker has no search / no jump-to-N / current-chapter auto-scroll / titles truncated to 1 line.** The `maxLines=1` in the chapter picker (reader side) is inconsistent with the library fix #6; a small follow-up.
- **M7 — font-family UI.** The `fontFamily` field exists in `ReaderConfig`; the inline CSS reads it; the settings sheet has no control. The JS injection makes a picker cheap but it is not in this cluster.
- **M8 full — auto-scroll play/pause UI and auto-advance at end of chapter.** This change makes the speed adjustable live but does not add the play/pause button or wire up `onAutoScrollReachedEnd` to load the next chapter.
- **M9 — tap zones** (left/center/right thirds for prev/next/toggle). The swipe detection at `ReaderHtmlBuilder.kt:180-195` is unchanged.
- **M10 — swipe vs text selection conflict.** Same file as M9; same follow-up.
- **L3 — CSP `font-src 'self' data:` blocks custom fonts.** Would need a CSP relaxation or bundled TTF files. Out of scope.
- **L5 — bookmark position is stored as a ratio and re-mapped to paragraphs on every render; the absolute pixel position drifts when font size changes.** This change applies bookmarks via JS on every config change (re-mapping the indices to the new paragraph count after reflow), which closes the visual drift; the underlying "ratio" representation is unchanged.
- **`app/release/baselineProfiles/*`** — regenerated by every build; not staged (per `AGENTS.md`).
- **`.gitignore`** — the unrelated edit from a previous session is not part of this change and is not staged.
- **Push of the 7 unpushed local commits** — requires SSH key configuration; the user is aware per the handoff.
