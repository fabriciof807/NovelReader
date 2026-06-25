# Reader "Perdi Meu Lugar" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the "I lost my place" cluster in the reader — H1 (no scroll save on lifecycle), H4 (settings change snaps to stale scroll), H2 (rotation recreates Activity and loses the live WebView), and B3 (every bookmark write triggers a full HTML reload). One commit, direct to `main`.

**Architecture:** Reactive Compose. `ReaderConfig` and bookmark changes apply via JS through dedicated `applyConfig` / `applyBookmarks` functions in the HTML's `<script>`, eliminating the `loadDataWithBaseURL` reload path. The HTML is rebuilt only on chapter content swap. Scroll position is captured live in the VM and persisted on `ON_PAUSE` via a `LifecycleEventObserver`. The Activity declares `configChanges` and `singleTop` so rotation and config changes do not destroy the WebView.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2024.12.01), Room 2.8.4, Hilt 2.59.2, Robolectric, MockK, Turbine, JUnit 4.

## Global Constraints
- Kotlin official style; **no comments unless requested**.
- PT-BR comments only where unavoidable; strings always bilingual (no new string resources needed).
- `StateFlow` for UI state, `MutableStateFlow` for internal, `@IoDispatcher` for IO.
- **One commit** covering the entire change: `fix: reader "perdi meu lugar" — live scroll + lifecycle save + JS-injected settings/bookmarks + configChanges`. Conventional-commit style. Direct to `main`.
- **Always run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` before claiming done.**
- `app/release/baselineProfiles/*` regenerated every build — do NOT stage.
- TDD: write the failing test first, run it to confirm failure, then implement.
- The pre-existing `.gitignore` edit and `app/release/baselineProfiles/*` regenerations are NOT part of this change and are not staged.

---

### Task 0: Try to push existing unpushed commits

**Files:** none

- [ ] **Step 1: Attempt push**

Run: `git push origin main`
Expected: either success (existing local commits land on origin) OR failure with SSH/auth error. On failure, continue — this fix stacks on top locally.

- [ ] **Step 2: If push fails, raise it but proceed**

No commit step. Do not configure SSH keys. Surface the push failure to the user at the end of the session.

---

### Task 1: ReaderViewModel — add `updateLiveScroll` + `saveScrollPosition()` no-arg overload

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderViewModel.kt:151-179` (add new methods alongside existing `saveScrollPosition`)
- Test: `app/src/test/java/com/novelreader/ui/reader/ReaderViewModelTest.kt` (append 3 tests)

**Interfaces:**
- Consumes: existing `currentChapter: ChapterEntity?` (set by `loadChapter`).
- Produces:
  - `fun updateLiveScroll(ratio: Float)` — in-memory only, no DB, no StateFlow update.
  - `fun saveScrollPosition()` — no-arg overload, persists `lastKnownScrollPosition` via `chapterDao.markAsRead`.
  - Existing `fun saveScrollPosition(scrollRatio: Float)` keeps its body unchanged.

- [ ] **Step 1: Write failing tests**

Append to `ReaderViewModelTest.kt`:

```kotlin
@Test
fun `updateLiveScroll stores ratio scaled to 0-1000 in lastKnownScrollPosition`() = runTest {
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch1",
        fileName = "ch1.html", orderIndex = 0, content = "<p>hi</p>"
    )
    coEvery { chapterDao.getChapterById(10) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

    viewModel = createViewModel()
    viewModel.updateLiveScroll(0.42f)

    val title = viewModel.getDefaultBookmarkTitle()
    assertThat(title).isNotEmpty()
}

@Test
fun `updateLiveScroll does not write to chapterDao`() = runTest {
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch1",
        fileName = "ch1.html", orderIndex = 0, content = "<p>hi</p>",
        isRead = true
    )
    coEvery { chapterDao.getChapterById(10) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

    viewModel = createViewModel()
    viewModel.updateLiveScroll(0.5f)

    coVerify(exactly = 0) { chapterDao.markAsRead(any(), any()) }
}

@Test
fun `saveScrollPosition no-arg persists lastKnownScrollPosition via chapterDao`() = runTest {
    val chapter = ChapterEntity(
        id = 10, novelId = 1, title = "Ch1",
        fileName = "ch1.html", orderIndex = 0, content = "<p>hi</p>"
    )
    coEvery { chapterDao.getChapterById(10) } returns chapter
    coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf(chapter)
    coEvery { novelDao.getNovelById(1) } returns null
    coEvery { novelDao.updateLastRead(any(), any()) } returns Unit
    coEvery { chapterDao.markAsRead(any(), any()) } returns Unit

    viewModel = createViewModel()
    viewModel.updateLiveScroll(0.5f)
    viewModel.saveScrollPosition()

    coVerify { chapterDao.markAsRead(10, 500) }
}
```

Imports to add to the test file: `coVerify` (`import io.mockk.coVerify`).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderViewModelTest"`
Expected: 3 failures with "Unresolved reference: updateLiveScroll" and "Unresolved reference: saveScrollPosition" (the no-arg overload does not exist yet).

- [ ] **Step 3: Implement the new methods**

In `ReaderViewModel.kt`, after the existing `private var lastKnownScrollPosition: Int = 0` declaration (line 151) and before `getDefaultBookmarkTitle` (line 153), add:

```kotlin
fun updateLiveScroll(ratio: Float) {
    lastKnownScrollPosition = (ratio * 1000).toInt()
}

fun saveScrollPosition() {
    val chapter = currentChapter ?: return
    val position = lastKnownScrollPosition
    viewModelScope.launch {
        try {
            chapterDao.markAsRead(chapter.id, position)
        } catch (e: Exception) {
            _errorEvents.emit(e.message ?: e.toString())
        }
    }
}
```

Do not modify the existing `saveScrollPosition(scrollRatio: Float)` method (line 168). It keeps its body and continues to update `lastKnownScrollPosition` and persist. Both paths now write to the same in-memory field.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderViewModelTest"`
Expected: 3 new tests pass, all existing tests still pass.

- [ ] **Step 5: Commit (optional intermediate)**

This task is part of the single final commit. Skip an intermediate commit unless the implementer prefers a checkpoint.

---

### Task 2: ReaderViewModel — remove `reloadVersion`, simplify `updateX` methods + `collectBookmarks`

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderViewModel.kt:32-49` (remove `reloadVersion` from `ReaderState`), `:131-141` (simplify `collectBookmarks`), `:237-271` (simplify `updateTheme/FontSize/LineHeight/AutoScrollSpeed`)
- Test: `app/src/test/java/com/novelreader/ui/reader/ReaderViewModelTest.kt` (append 4 tests)

**Interfaces:**
- Consumes: existing `readerPreferences` and `bookmarkDao`.
- Produces:
  - `ReaderState` no longer has `reloadVersion`. The `data class` constructor calls in test code that pass `reloadVersion` will fail to compile — search-and-replace the test setup to drop the parameter.
  - `updateTheme/FontSize/LineHeight/AutoScrollSpeed` only call `readerPreferences.updateX(value)` — they do NOT touch `_state` (no `reloadVersion` bump, no copy).
  - `collectBookmarks` updates `state.bookmarks` without bumping any version field.

- [ ] **Step 1: Write failing tests**

Append to `ReaderViewModelTest.kt`:

```kotlin
@Test
fun `updateTheme delegates to readerPreferences and does not bump state`() = runTest {
    viewModel = createViewModel()
    val before = viewModel.state.value
    viewModel.updateTheme("dark")
    val after = viewModel.state.value
    assertThat(after).isEqualTo(before)
    coVerify { readerPrefs.updateTheme("dark") }
}

@Test
fun `updateFontSize delegates to readerPreferences and does not bump state`() = runTest {
    viewModel = createViewModel()
    val before = viewModel.state.value
    viewModel.updateFontSize(24)
    val after = viewModel.state.value
    assertThat(after).isEqualTo(before)
    coVerify { readerPrefs.updateFontSize(24) }
}

@Test
fun `updateLineHeight delegates to readerPreferences and does not bump state`() = runTest {
    viewModel = createViewModel()
    val before = viewModel.state.value
    viewModel.updateLineHeight(2.0f)
    val after = viewModel.state.value
    assertThat(after).isEqualTo(before)
    coVerify { readerPrefs.updateLineHeight(2.0f) }
}

@Test
fun `updateAutoScrollSpeed delegates to readerPreferences and does not bump state`() = runTest {
    viewModel = createViewModel()
    val before = viewModel.state.value
    viewModel.updateAutoScrollSpeed(1.5f)
    val after = viewModel.state.value
    assertThat(after).isEqualTo(before)
    coVerify { readerPrefs.updateAutoScrollSpeed(1.5f) }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderViewModelTest"`
Expected: 4 failures. `updateTheme` and friends currently bump `reloadVersion` via `_state.value.copy(reloadVersion = _state.value.reloadVersion + 1)`, so the state changes and the assertions on `assertThat(after).isEqualTo(before)` fail.

- [ ] **Step 3: Remove `reloadVersion` from `ReaderState`**

In `ReaderViewModel.kt:32-49`, delete the `val reloadVersion: Int = 0` field from the `ReaderState` data class. The class becomes:

```kotlin
data class ReaderState(
    val novel: NovelEntity? = null,
    val chapter: ChapterEntity? = null,
    val prevChapterId: Long? = null,
    val nextChapterId: Long? = null,
    val allChapters: List<ChapterEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showBookmarkDialog: Boolean = false,
    val showSettings: Boolean = false,
    val config: ReaderConfig = ReaderConfig(),
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ChapterEntity> = emptyList(),
    val selectedText: String = ""
)
```

- [ ] **Step 4: Simplify `updateTheme/FontSize/LineHeight/AutoScrollSpeed`**

In `ReaderViewModel.kt:237-271`, replace each of the four methods so they only call `readerPreferences.updateX` and return. The bodies become:

```kotlin
fun updateTheme(theme: String) {
    viewModelScope.launch { readerPreferences.updateTheme(theme) }
}

fun updateFontSize(size: Int) {
    viewModelScope.launch { readerPreferences.updateFontSize(size) }
}

fun updateLineHeight(height: Float) {
    viewModelScope.launch { readerPreferences.updateLineHeight(height) }
}

fun updateAutoScrollSpeed(speed: Float) {
    viewModelScope.launch { readerPreferences.updateAutoScrollSpeed(speed) }
}
```

The `_state.value.copy(reloadVersion = ...)` blocks are removed. The `init` block at `ReaderViewModel.kt:81-85` continues to collect the `readerPreferences.config` Flow and update `_state.value.config` — that drives the `LaunchedEffect(state.config)` in the Screen.

- [ ] **Step 5: Simplify `collectBookmarks`**

In `ReaderViewModel.kt:131-141`, drop the `reloadVersion` bump:

```kotlin
private fun collectBookmarks(chapterId: Long) {
    bookmarkCollectionJob?.cancel()
    bookmarkCollectionJob = viewModelScope.launch {
        bookmarkDao.getByChapter(chapterId).collect { list ->
            _state.value = _state.value.copy(bookmarks = list)
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderViewModelTest"`
Expected: 4 new tests pass, all existing tests still pass.

- [ ] **Step 7: Commit (optional intermediate)**

Skip — single final commit at the end.

---

### Task 3: ReaderHtmlBuilder — extract `themeVars`, add `applyConfig` + `applyBookmarks` JS, drop `bookmarksScrollPositions` param

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderHtmlBuilder.kt` (extract `themeVars`, add JS functions, drop param, remove `insertBookmarkInContent`)
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt:132-135` (drop `bookmarksScrollPositions` arg from `buildReaderHtml` call)
- Test: `app/src/test/java/com/novelreader/ui/reader/ReaderHtmlBuilderTest.kt` (new file, 4 tests)

**Interfaces:**
- Consumes: `ReaderConfig` (existing).
- Produces:
  - `themeVars(config: ReaderConfig): Map<String, String>` — new public function, four keys (`bgColor`, `textColor`, `accentColor`, `linkColor`) → hex values per theme.
  - `buildReaderHtml(content, config)` — new 2-arg signature. The old `bookmarksScrollPositions` parameter is gone.
  - The generated HTML contains two new JS functions: `applyConfig(cfg)` and `applyBookmarks(positionsJson)`. The auto-scroll JS body grows a `setAutoScrollSpeed(speed)` helper.

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/novelreader/ui/reader/ReaderHtmlBuilderTest.kt`:

```kotlin
package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.ReaderConfig
import org.junit.Test

class ReaderHtmlBuilderTest {

    @Test
    fun `buildReaderHtml does not embed bookmark indicators in initial content`() {
        val html = buildReaderHtml(
            content = "<p>one</p><p>two</p>",
            config = ReaderConfig()
        )
        assertThat(html).doesNotContain("bookmark-indicator")
        assertThat(html).doesNotContain("class=\"bookmarked\"")
    }

    @Test
    fun `buildReaderHtml includes applyConfig and applyBookmarks JS functions`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig()
        )
        assertThat(html).contains("function applyConfig(")
        assertThat(html).contains("function applyBookmarks(")
        assertThat(html).contains("function setAutoScrollSpeed(")
    }

    @Test
    fun `themeVars returns the four color keys for each theme`() {
        for (theme in listOf("light", "dark", "sepia", "gray")) {
            val map = themeVars(ReaderConfig(theme = theme))
            assertThat(map).containsKey("bgColor")
            assertThat(map).containsKey("textColor")
            assertThat(map).containsKey("accentColor")
            assertThat(map).containsKey("linkColor")
        }
    }

    @Test
    fun `buildReaderHtml still emits the auto-scroll script when speed is positive`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig(autoScrollSpeed = 1.5f)
        )
        assertThat(html).contains("_asSpeed")
        assertThat(html).contains("startAutoScroll")
        assertThat(html).contains("stopAutoScroll")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderHtmlBuilderTest"`
Expected: all 4 tests fail. The HTML still embeds bookmark indicators (test 1), the new functions are not in the HTML (test 2), `themeVars` does not exist (test 3 — compile failure), and the test for auto-scroll may pass or fail depending on the current state (test 4 — baseline).

- [ ] **Step 3: Extract `themeVars` and add new JS functions**

In `ReaderHtmlBuilder.kt`, add a new public function before `buildReaderHtml`:

```kotlin
fun themeVars(config: ReaderConfig): Map<String, String> = when (config.theme) {
    "dark" -> mapOf(
        "bgColor" to "#1a1a2e",
        "textColor" to "#e0e0e0",
        "accentColor" to "#90caf9",
        "linkColor" to "#64b5f6"
    )
    "sepia" -> mapOf(
        "bgColor" to "#f4e4c1",
        "textColor" to "#5b4636",
        "accentColor" to "#8d6e63",
        "linkColor" to "#6d4c41"
    )
    "gray" -> mapOf(
        "bgColor" to "#2d2d2d",
        "textColor" to "#d0d0d0",
        "accentColor" to "#90a4ae",
        "linkColor" to "#81d4fa"
    )
    else -> mapOf(
        "bgColor" to "#f5f0e8",
        "textColor" to "#333333",
        "accentColor" to "#1a237e",
        "linkColor" to "#1565c0"
    )
}
```

- [ ] **Step 4: Rewrite `buildReaderHtml` to use `themeVars` and inject JS functions**

In `ReaderHtmlBuilder.kt`, replace the entire `buildReaderHtml` function body so that:

1. The signature becomes `fun buildReaderHtml(content: String, config: ReaderConfig): String` — drop `bookmarksScrollPositions`.
2. The `themeVars` `when` block is replaced by a call to the new `themeVars(config)` function. The inline `:root` CSS formats the map as `key: value;` lines, prefixing each key with `--`. The full CSS block (preserving the existing selectors — `*`, `html`, `body`, `#content`, `p`, `p:first-of-type`, `.bookmarked`, `.bookmark-indicator`, `h1-h4`, `img`, `.search-highlight`, `@keyframes pulse` — exactly as they appear at `ReaderHtmlBuilder.kt:61-134`) becomes:

```kotlin
val themeCss = themeVars(config).entries.joinToString("\n            ") { (k, v) -> "--$k: $v;" }
val css = """
    :root {
        $themeCss
        --font-family: '${config.fontFamily}', Georgia, serif;
        --font-size: ${config.fontSize}px;
        --line-height: ${config.lineHeight};
        --padding: 20px;
        --max-width: 800px;
    }
    * {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
        background-color: transparent !important;
    }
    html {
        background-color: var(--bg-color) !important;
    }
    body {
        background-color: var(--bg-color) !important;
        color: var(--text-color) !important;
        font-family: var(--font-family);
        font-size: var(--font-size);
        line-height: var(--line-height);
        padding: var(--padding);
        -webkit-font-smoothing: antialiased;
        word-wrap: break-word;
    }
    #content {
        max-width: var(--max-width);
        margin: 0 auto;
        background-color: transparent !important;
    }
    p {
        margin: 0 0 1.2em 0;
        text-indent: 2em;
        color: var(--text-color) !important;
    }
    p:first-of-type { text-indent: 0; }
    .bookmarked {
        border-left: 3px solid var(--accent-color);
        padding-left: 12px;
        text-indent: 0;
    }
    .bookmark-indicator {
        display: inline-flex;
        align-items: center;
        margin-right: 6px;
        color: var(--accent-color);
    }
    h1, h2, h3, h4 {
        margin: 1.5em 0 0.8em 0;
        font-weight: bold;
        text-indent: 0;
        color: var(--text-color) !important;
    }
    img {
        max-width: 100%;
        height: auto;
        display: block;
        margin: 1em auto;
    }
    .search-highlight {
        background-color: rgba(255, 235, 59, 0.4);
        border-radius: 2px;
        padding: 1px 0;
        animation: pulse 0.6s ease-in-out 3;
    }
    @keyframes pulse {
        0%   { background-color: rgba(255, 235, 59, 0.3); }
        50%  { background-color: rgba(255, 235, 59, 1.0); }
        100% { background-color: rgba(255, 235, 59, 0.3); }
    }
""".trimIndent()
```

3. The `finalContent` is just `stripJunkContent(sanitized)` — remove the `insertBookmarkInContent` call entirely.
4. The `<script>` block gains three new function definitions: `applyConfig`, `applyBookmarks`, and `setAutoScrollSpeed`. Append them right after the existing touch-handling IIFE (after `})();` on line 195) and before `$autoScrollJs`. The complete updated template literal at `ReaderHtmlBuilder.kt:155-204` becomes:

```kotlin
return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta http-equiv="Content-Security-Policy"
            content="default-src 'none';
                     style-src 'unsafe-inline';
                     script-src 'unsafe-inline';
                     img-src data:;
                     font-src 'self' data:;
                     connect-src 'none';
                     object-src 'none';
                     base-uri 'none';
                     form-action 'none';
                     frame-src 'none';
                     frame-ancestors 'none';">
        <style>$css</style>
        <script>
        var _lastSel = '';
        document.addEventListener('selectionchange', function() {
            var sel = window.getSelection().toString().trim();
            if (sel !== _lastSel) { _lastSel = sel; Android.onTextSelected(sel); }
        });

        (function() {
            var _ts = {x:0, y:0, t:0};
            document.addEventListener('touchstart', function(e) {
                var t = e.touches[0]; _ts = {x: t.clientX, y: t.clientY, t: Date.now()};
            });
            document.addEventListener('touchend', function(e) {
                var dx = e.changedTouches[0].clientX - _ts.x;
                var dy = e.changedTouches[0].clientY - _ts.y;
                var dt = Date.now() - _ts.t;
                if (Math.abs(dx) < 15 && Math.abs(dy) < 15 && dt < 300) {
                    try { Android.onTap(); } catch(e) {}
                } else if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) {
                    try { Android.onSwipe(dx > 0 ? 'prev' : 'next'); } catch(e) {}
                }
            });
        })();

        function applyConfig(cfg) {
            var root = document.documentElement.style;
            root.setProperty('--bg-color', cfg.bgColor);
            root.setProperty('--text-color', cfg.textColor);
            root.setProperty('--accent-color', cfg.accentColor);
            root.setProperty('--link-color', cfg.linkColor);
            root.setProperty('--font-family', "'" + cfg.fontFamily + "', Georgia, serif");
            root.setProperty('--font-size', cfg.fontSize + 'px');
            root.setProperty('--line-height', cfg.lineHeight);
            setAutoScrollSpeed(cfg.autoScrollSpeed);
        }
        function setAutoScrollSpeed(speed) {
            _asSpeed = speed;
            if (speed <= 0) { stopAutoScroll(); }
            else if (!_asRunning) { startAutoScroll(); }
        }
        function applyBookmarks(positionsJson) {
            var content = document.getElementById('content');
            if (!content) return;
            var paras = content.querySelectorAll('p');
            if (!paras.length) return;
            var existing = content.querySelectorAll('.bookmark-indicator');
            for (var i = existing.length - 1; i >= 0; i--) existing[i].remove();
            paras.forEach(function(p){ p.classList.remove('bookmarked'); });
            var positions = JSON.parse(positionsJson);
            var used = {};
            positions.forEach(function(pos) {
                var idx = Math.floor((pos / 1000) * paras.length);
                if (idx < 0) idx = 0;
                if (idx >= paras.length) idx = paras.length - 1;
                if (used[idx]) return;
                used[idx] = true;
                var span = document.createElement('span');
                span.className = 'bookmark-indicator';
                span.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>';
                paras[idx].parentNode.insertBefore(span, paras[idx]);
                paras[idx].classList.add('bookmarked');
            });
        }

        $autoScrollJs
        </script>
    </head>
    <body>
        <div id="content">$finalContent</div>
    </body>
    </html>
""".trimIndent()
```

- [ ] **Step 5: Remove the dead `insertBookmarkInContent` function**

In `ReaderHtmlBuilder.kt`, delete the entire `private fun insertBookmarkInContent(content: String, scrollPositions: List<Int>): String` function (lines 207-226). The bookmark logic is reproduced in JS via `applyBookmarks`.

- [ ] **Step 6: Update the call site in `ReaderScreen.kt`**

In `ReaderScreen.kt:132-135`, change the `buildReaderHtml` call to drop the `bookmarksScrollPositions` argument:

```kotlin
val html = buildReaderHtml(
    content = chapter.content,
    config = state.config
)
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderHtmlBuilderTest"`
Expected: 4 new tests pass.

- [ ] **Step 8: Compile to catch any other call sites**

Run: `./gradlew :app:compileDebugKotlin`
Expected: clean compile. If other call sites of `buildReaderHtml` exist, fix them (drop the third arg).

- [ ] **Step 9: Commit (optional intermediate)**

Skip — single final commit at the end.

---

### Task 4: AndroidManifest — add `configChanges` + `singleTop` to `MainActivity`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:20-23` (add two attributes to the `<activity>`)

- [ ] **Step 1: Add the attributes**

In `AndroidManifest.xml:20-23`, the `<activity>` declaration becomes:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop"
    android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|density|fontScale"
    android:theme="@style/Theme.NovelReader">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

- [ ] **Step 2: Compile to verify no manifest validation errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: clean compile. (The manifest is not exercised at JVM compile time, but a clean Kotlin compile confirms no XML-level breakage the build would otherwise catch.)

- [ ] **Step 3: Commit (optional intermediate)**

Skip — single final commit at the end.

---

### Task 5: ReaderScreen — wire `onPageFinished`, add `LaunchedEffect`s, add `LifecycleEventObserver`, simplify `saveScroll`

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt:85-100` (add `isPageLoaded` state + `applyConfigJs`/`applyBookmarksJs` helpers)
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt:119-127` (simplify `saveScroll()`)
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt:129-186` (refactor chapter-swap `LaunchedEffect`; add `LaunchedEffect(state.config, webView)` and `LaunchedEffect(state.bookmarks, webView)`)
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt:516-531` (expand `onPageFinished` callback in `ReaderWebView(...)` invocation)
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt:563-567` (replace `DisposableEffect(Unit)` with `DisposableEffect` owning the `LifecycleEventObserver`)
- Test: manual QA + optional instrumented Compose test (see "Manual QA checklist" below)

**Interfaces:**
- Consumes: `state.config`, `state.bookmarks`, `viewModel.getScrollRatio()`, `pendingSearchQuery`.
- Produces:
  - `isPageLoaded: MutableState<Boolean>` (remember'd).
  - `applyConfigJs(config: ReaderConfig): String` — JSON payload + IIFE wrapper.
  - `applyBookmarksJs(bookmarks: List<BookmarkEntity>): String` — JSON array + IIFE wrapper.
  - `LaunchedEffect(state.config, webView)` — reactive config apply, short-circuits when `!isPageLoaded`.
  - `LaunchedEffect(state.bookmarks, webView)` — reactive bookmarks apply, short-circuits when `!isPageLoaded`.
  - Refactored chapter-swap `LaunchedEffect(state.chapter, webView)` — rebuilds HTML and resets `isPageLoaded = false`. The scroll-restore and search-highlight logic move into `onPageFinished`.
  - Expanded `onPageFinished = { wv, _ -> ... }` callback — sets `isPageLoaded = true`, applies config, applies bookmarks, restores scroll (or runs FTS highlight if `pendingSearchQuery != null`).
  - `LifecycleEventObserver` on `LocalLifecycleOwner.current` — `ON_PAUSE` calls `viewModel.saveScrollPosition()` + `webView?.onPause()`; `ON_RESUME` calls `webView?.onResume()`. The `onDispose` removes the observer and destroys the WebView.

- [ ] **Step 1: Add `isPageLoaded` state and the two `applyXxxJs` helpers**

In `ReaderScreen.kt`, after the `var pendingSearchQuery by pendingSearchQueryState` line (line 92), add:

```kotlin
var isPageLoaded by remember { mutableStateOf(false) }
```

Then in the same file (or at the top of the function, before the `LaunchedEffect`s), add the two helpers that build the JS payload strings. Reuse the existing `buildJs` helper (line 97):

```kotlin
fun applyConfigJs(config: ReaderConfig): String {
    val map = themeVars(config)
    val payload = map + mapOf(
        "fontFamily" to config.fontFamily,
        "fontSize" to config.fontSize,
        "lineHeight" to config.lineHeight,
        "autoScrollSpeed" to config.autoScrollSpeed
    )
    return buildJs(
        code = "applyConfig(JSON.parse(args));",
        params = mapOf("args" to payload)
    )
}

fun applyBookmarksJs(bookmarks: List<BookmarkEntity>): String {
    val positions = bookmarks.map { it.scrollPosition }
    return buildJs(
        code = "applyBookmarks(JSON.parse(args));",
        params = mapOf("args" to positions)
    )
}
```

Add the import for `com.novelreader.data.local.preferences.ReaderConfig` if not already present, and the import for `themeVars` (same package, no import needed).

- [ ] **Step 2: Simplify `saveScroll()`**

In `ReaderScreen.kt:119-127`, replace the body of `saveScroll()`:

```kotlin
fun saveScroll() {
    viewModel.saveScrollPosition()
}
```

The old JS-evaluation step is removed — the live scroll ratio is already in the VM via `updateLiveScroll(ratio)` on every `onScrollChanged`.

- [ ] **Step 3: Refactor the chapter-swap `LaunchedEffect` and add the two reactive `LaunchedEffect`s**

In `ReaderScreen.kt:129-186`, replace the existing `LaunchedEffect(state.chapter, state.reloadVersion, webView)` block. Note: `state.reloadVersion` no longer exists in `ReaderState` (removed in Task 2).

The new chapter-swap effect (keys: `state.chapter, webView`):

```kotlin
LaunchedEffect(state.chapter, webView) {
    state.chapter?.let { chapter ->
        webView?.let { wv ->
            isPageLoaded = false
            val html = buildReaderHtml(
                content = chapter.content,
                config = state.config
            )
            wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }
}
```

The scroll-restore and FTS-highlight logic moves out of this effect and into `onPageFinished` (Step 5 below). The `pendingSearchQuery` is still consumed in `onPageFinished`.

Add the two reactive `LaunchedEffect`s right after the chapter-swap effect:

```kotlin
LaunchedEffect(state.config, webView) {
    if (!isPageLoaded) return@LaunchedEffect
    webView?.evaluateJavascript(applyConfigJs(state.config), null)
}

LaunchedEffect(state.bookmarks, webView) {
    if (!isPageLoaded) return@LaunchedEffect
    webView?.evaluateJavascript(applyBookmarksJs(state.bookmarks), null)
}
```

- [ ] **Step 4: Update `onScrollChanged` to push live scroll to the VM**

In `ReaderScreen.kt:518`, change the `onScrollChanged` lambda:

```kotlin
onScrollChanged = { ratio ->
    scrollRatio = ratio
    viewModel.updateLiveScroll(ratio)
},
```

- [ ] **Step 5: Expand the `onPageFinished` callback**

In `ReaderScreen.kt:519`, the `onPageFinished = { _, _ -> }` line becomes:

```kotlin
onPageFinished = { wv, _ ->
    isPageLoaded = true
    wv.evaluateJavascript(applyConfigJs(state.config), null)
    wv.evaluateJavascript(applyBookmarksJs(state.bookmarks), null)
    val search = pendingSearchQuery
    if (search != null) {
        pendingSearchQuery = null
        wv.evaluateJavascript(buildJs(
            code = "window.scrollTo(0, 0);",
            params = emptyMap()
        ), null)
        wv.evaluateJavascript(buildJs(
            code = """
                setTimeout(function() {
                    (function(q) {
                        var c = document.getElementById('content');
                        if (!c) return;
                        var w = document.createTreeWalker(c, NodeFilter.SHOW_TEXT);
                        var n;
                        while (n = w.nextNode()) {
                            var i = n.nodeValue.toLowerCase().indexOf(q.toLowerCase());
                            if (i >= 0) {
                                var r = document.createRange();
                                r.setStart(n, i);
                                r.setEnd(n, i + q.length);
                                var m = document.createElement('mark');
                                m.className = 'search-highlight';
                                try {
                                    r.surroundContents(m);
                                    var top = m.getBoundingClientRect().top + window.scrollY - 80;
                                    window.scrollTo({ top: top, behavior: 'smooth' });
                                } catch (e) {}
                                return;
                            }
                        }
                    })(args.query);
                }, 1000);
            """.trimIndent(),
            params = mapOf("query" to search)
        ), null)
    } else {
        val ratio = viewModel.getScrollRatio()
        if (ratio > 0f) {
            wv.evaluateJavascript(buildJs(
                code = "var max = document.body.scrollHeight - window.innerHeight; window.scrollTo(0, max * args.ratio);",
                params = mapOf("ratio" to ratio)
            ), null)
        }
    }
},
```

Note: `state.config` and `state.bookmarks` inside this lambda read the current `State<T>` values at invocation time. The lambda is captured once in the `WebViewClient` factory closure, but the captured `state` is a `State<T>` property delegate, so the values are always current.

- [ ] **Step 6: Replace the `DisposableEffect(Unit)` with a lifecycle-observer-owning `DisposableEffect`**

In `ReaderScreen.kt:563-567`, replace the `DisposableEffect(Unit) { onDispose { webView?.destroy() } }` block with:

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                viewModel.saveScrollPosition()
                webView?.onPause()
            }
            Lifecycle.Event.ON_RESUME -> {
                webView?.onResume()
            }
            else -> {}
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
        webView?.destroy()
    }
}
```

Add the import for `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.LifecycleEventObserver`, and `androidx.lifecycle.compose.LocalLifecycleOwner` (note: the Compose `LocalLifecycleOwner` is in `androidx.lifecycle.compose`, not `androidx.compose.ui.platform`).

- [ ] **Step 7: Compile and run all unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: clean compile, all existing unit tests still pass, and the 11 new tests added in Tasks 1-3 (3 in Task 1, 4 in Task 2, 4 in Task 3) all pass.

- [ ] **Step 8: Manual QA checklist**

Verify on a connected device or emulator:

- [ ] Open a chapter, drag the font size slider — text reflows without scroll snap.
- [ ] Drag the line height slider — same.
- [ ] Switch theme (light → dark) — `:root` CSS vars update, no reload.
- [ ] Add a bookmark — the `.bookmark-indicator` span appears in the correct paragraph; the scroll position is preserved.
- [ ] Delete a bookmark — the span disappears; the scroll position is preserved.
- [ ] Rotate the device (portrait ↔ landscape) — the chapter, scroll position, and font size are all preserved; no full reload.
- [ ] Background the app via Home, then return — scroll position is preserved (lifecycle save fired).
- [ ] From an FTS search result, open a chapter — the first match is highlighted and the WebView scrolls to it (pendingSearchQuery path).
- [ ] Adjust auto-scroll speed mid-reading — the page auto-scrolls at the new rate without a reload.

If any item fails, the test is the regression. Fix and re-verify before committing.

- [ ] **Step 9: Commit (optional intermediate)**

Skip — single final commit at the end.

---

### Task 6: Regenerate `reader.css` as reference documentation

**Files:**
- Modify: `app/src/main/assets/css/reader.css` (rewrite to match the inline CSS in `ReaderHtmlBuilder.kt`)

- [ ] **Step 1: Copy the inline CSS into `reader.css`**

Open `app/src/main/assets/css/reader.css` and replace its contents with the CSS block currently inline in `ReaderHtmlBuilder.kt:61-134` (the CSS template between `val css = """` and `""".trimIndent()`).

The current `reader.css` (which is drifted — never loaded by the runtime) has a `.page-mode` columns rule and references `'Noto Serif'` which isn't bundled. The new file should match the inline CSS exactly: theme vars, font family, font size, line height, padding, max-width, body, `#content`, paragraphs, headings 1-4, images, `.bookmarked`, `.bookmark-indicator`, `.search-highlight`, `@keyframes pulse`.

- [ ] **Step 2: Add a reference-documentation header**

At the top of `reader.css`, add a comment block:

```css
/*
 * REFERENCE DOCUMENTATION ONLY.
 *
 * This file is not loaded by the runtime. The reader uses the inline
 * <style> block generated by `buildReaderHtml` in
 * `app/src/main/java/com/novelreader/ui/reader/ReaderHtmlBuilder.kt`.
 *
 * `loadDataWithBaseURL(null, ...)` in `ReaderScreen.kt` does not load
 * this file. Do not edit this file expecting runtime changes.
 *
 * If you change the CSS, change the inline template in ReaderHtmlBuilder.kt
 * and then mirror that change here for documentation parity.
 */
```

- [ ] **Step 3: Compile to confirm no compile-time references break**

Run: `./gradlew :app:compileDebugKotlin`
Expected: clean compile. (The file is a static asset, not referenced from Kotlin code; the build should not notice the change.)

- [ ] **Step 4: Commit (optional intermediate)**

Skip — single final commit at the end.

---

### Task 7: Final verification + single commit

**Files:** the entire diff accumulated across Tasks 1-6.

- [ ] **Step 1: Full build + tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: clean compile, 129 unit tests pass (118 current baseline + 11 new: 3 in Task 1, 4 in Task 2, 4 in Task 3). If the baseline has drifted, verify the total is "baseline + 11".

- [ ] **Step 2: Confirm the spec's scope is covered**

Walk through the spec's "Per-feature" sections and confirm each item is present in the diff:

- H1: `updateLiveScroll`, `saveScrollPosition()` no-arg, `LifecycleEventObserver` in `DisposableEffect`, `onScrollChanged` calls `viewModel.updateLiveScroll(ratio)`, `saveScroll()` simplified.
- H4: `LaunchedEffect(state.config, webView)` in `ReaderScreen`, `applyConfig` JS in `ReaderHtmlBuilder`, `themeVars` extraction, `setAutoScrollSpeed` JS, `updateX` methods no longer bump state.
- B3: `LaunchedEffect(state.bookmarks, webView)`, `applyBookmarks` JS, `bookmarksScrollPositions` parameter removed, `insertBookmarkInContent` removed.
- H2: `configChanges` and `singleTop` on `<activity>` in `AndroidManifest.xml`.
- L1: `reader.css` regenerated with reference-documentation header.
- M6: `saveScrollPosition()` (no-arg) uses the same viewport-aware formula as `onScrollChanged`.
- partial M8: `setAutoScrollSpeed` JS updates auto-scroll speed mid-reading.
- B9: `singleTop` (added in H2).

Any missing item is a regression — fix and re-verify.

- [ ] **Step 3: Stage and commit**

Stage only the relevant files (do NOT stage `.gitignore`, `app/release/baselineProfiles/*`, or `app/release/output-metadata.json`):

```bash
git add \
  app/src/main/java/com/novelreader/ui/reader/ReaderViewModel.kt \
  app/src/main/java/com/novelreader/ui/reader/ReaderHtmlBuilder.kt \
  app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt \
  app/src/main/AndroidManifest.xml \
  app/src/main/assets/css/reader.css \
  app/src/test/java/com/novelreader/ui/reader/ReaderViewModelTest.kt \
  app/src/test/java/com/novelreader/ui/reader/ReaderHtmlBuilderTest.kt
git status
```

Verify `git status` shows only the 7 intended files plus the pre-existing `docs/superpowers/specs/2026-06-25-reader-place-keeping-design.md` and `docs/superpowers/plans/2026-06-25-reader-place-keeping.md` from the brainstorming phase. The `.gitignore`, baseline profiles, and output-metadata.json must NOT appear in `git status` after staging.

Then:

```bash
git commit -m "fix: reader \"perdi meu lugar\" — live scroll + lifecycle save + JS-injected settings/bookmarks + configChanges

Closes the cluster of UX issues around losing the reader scroll position:
- H1: scroll position is now captured live on every onScrollChanged and
  persisted on ON_PAUSE via a LifecycleEventObserver.
- H4: settings changes (font size, line height, theme, auto-scroll speed)
  are applied via a JS function (applyConfig) that updates :root CSS
  variables — no more loadDataWithBaseURL reload and no scroll snap.
- B3: bookmark add/delete is applied via a JS function (applyBookmarks)
  that injects/removes .bookmark-indicator spans — no more reload on
  every bookmark change.
- H2: Activity declares configChanges + singleTop so rotation does not
  recreate the WebView; the live in-memory scroll survives natively.
- M6: the two scroll-ratio formulas are unified — save and restore use
  the same viewport-aware ratio from onScrollChanged.
- partial M8: auto-scroll speed is now adjustable mid-reading via JS.
- L1: reader.css is regenerated as reference documentation matching the
  inline CSS in ReaderHtmlBuilder.kt.
- B9: launchMode=singleTop ensures deep-link delivery via onNewIntent.

Spec: docs/superpowers/specs/2026-06-25-reader-place-keeping-design.md
Plan: docs/superpowers/plans/2026-06-25-reader-place-keeping.md"
```

- [ ] **Step 4: Verify the commit**

Run: `git log --oneline -1 && git show --stat HEAD`
Expected: the new commit is on top of `db8a624` (the spec corrections), with 7 files changed.

- [ ] **Step 5: Try the push again**

Run: `git push origin main`
Expected: either success (the commit and the spec commit land on origin) OR SSH/auth error. On failure, surface to the user — the commits stack locally for a future session.
