# v2.4.3 Reader UX Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the five-section v2.4.3 reader-UX-polish design as five atomic, independently testable, independently revertable commits on `main`, ending with a v2.4.3 / versionCode 17 version bump.

**Architecture:** Per-surface commits (4 × `feat`/`fix` + 1 × `chore` version bump). TDD per project convention. No schema changes, no new top-level dependencies, no network-layer changes. Reader-side UX (haptics, keep-screen-on, chapter-list search, title cleaning) and favorites-side UX (search, created-date) are isolated from the in-progress FreeWebNovel 403 workstream by strict per-commit `git add` scoping.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1, JVM 17. Jetpack Compose (BOM 2024.12.01) + Material3. Room 2.8.4 (unchanged). Hilt 2.59.2. Jsoup 1.22.1. DataStore 1.1.3. Robolectric (existing test dep) for the rewritten `RelativeTime` test and the new `ReaderPreferencesTest`. MockK + Turbine + Truth (existing test deps). No new dependencies.

## Working-tree isolation (read first — applies to every commit)

The working tree currently contains **uncommitted in-progress FreeWebNovel 403 work** in these files (do NOT touch, do NOT stage):

```
M app/build.gradle.kts
M app/release/baselineProfiles/0/app-release.dm
M app/release/baselineProfiles/1/app-release.dm
M app/release/output-metadata.json
M app/src/main/java/com/novelreader/data/parser/ParserRegistry.kt
M app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterCrawler.kt
M app/src/main/java/com/novelreader/domain/usecase/webimport/ChapterFetcher.kt
M app/src/main/java/com/novelreader/domain/usecase/webimport/CoverDownloader.kt
M app/src/main/java/com/novelreader/util/StringUtils.kt
M app/src/test/java/com/novelreader/domain/usecase/WebImportUseCaseTest.kt
M gradle/libs.versions.toml
?? app/src/main/java/com/novelreader/data/local/preferences/DataStoreCloudflareCookieStore.kt
?? app/src/main/java/com/novelreader/domain/usecase/webimport/CloudflareCookieStore.kt
?? app/src/main/java/com/novelreader/domain/usecase/webimport/HttpClient.kt
?? app/src/main/java/com/novelreader/domain/usecase/webimport/WebFetchDiagnostic.kt
?? app/src/test/java/com/novelreader/data/local/preferences/DataStoreCloudflareCookieStoreTest.kt
?? app/src/test/java/com/novelreader/domain/usecase/webimport/
```

**Rule for every commit in this plan:** use the explicit exact-path `git add` command listed in the commit step. Never run `git add .` or `git add -A`. The `app/release/baselineProfiles/*` and `app/release/output-metadata.json` are build artifacts — never staged (per `AGENTS.md`).

**Build-file conflict at commit 5:** `app/build.gradle.kts` and `gradle/libs.versions.toml` are dirty with 403 work. The version-bump commit (Task 5) must isolate its versionCode/versionName edit from those 403 changes. See Task 5 pre-step for the `git stash` / coordinate procedure.

## Global Constraints

- **Kotlin / AGP / JVM:** 2.2.10 / 9.2.1 / 17 (unchanged).
- **Min SDK 26, Target 34, Compile 35** (unchanged).
- **Strings:** every new pt-BR key mirrored in `values-en/strings.xml`; the v2.4.2 i18n discipline (DocumentBuilder XML-parsing test approach) still applies to any string added by a task in this plan.
- **Haptics type:** `HapticFeedbackType.LongPress` to match the v2.4.2 pattern.
- **RelativeTime test runtime:** the rewritten `RelativeTimeTest` runs under Robolectric (`@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])`) — see AppPreferencesDynamicColorTest for the exact setup.
- **DateUtils in RelativeTime:** output depends on the system locale. The rewritten test asserts *properties* (non-null, non-empty, contains the number) rather than exact strings, so it is robust to Android-version wording changes. See Task 3 for the full rationale.
- **No `git add .`** (see Working-tree isolation).
- **Baseline:** 163/163 tests must stay green throughout. New tests add to the count. Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` before each commit.
- **No new top-level dependencies.**
- **Notification workstream:** still out of scope. Do not touch.

---

## Task 0: Pre-flight — verify baseline and working-tree state

**Files:** none modified. Read-only verification.

- [ ] **Step 1: Confirm HEAD and dirty file list**

```bash
cd /home/fabricio/Repos/Opencode/android-book
git log --oneline -3
git status --short
```

Expected: HEAD is `077db03 docs: reader UX polish design (v2.4.3 spec)` (the spec just committed). The dirty file list matches the "Working-tree isolation" section above. If the dirty list has changed (e.g., the 403 workstream committed or stashed), re-read the spec to confirm none of THIS plan's files are dirty in unexpected ways.

- [ ] **Step 2: Confirm baseline 163/163 green**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass (163/163 or the current baseline). If the 403 workstream has uncommitted test changes that are broken, STOP and coordinate — do not proceed with this plan while the test suite is red.

- [ ] **Step 3: Note the working-tree state in the commit message of Task 5**

When you reach Task 5 (version bump), `app/build.gradle.kts` will still be dirty. Task 5's pre-step handles this.

---

## Task 1: Reader toolbar haptics + keep-screen-on (Commit 1)

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/local/preferences/ReaderPreferences.kt`
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderViewModel.kt`
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt` (haptics + keepScreenOn wiring + plumb onKeepScreenOnChange)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/test/java/com/novelreader/data/local/preferences/ReaderPreferencesTest.kt`
- Modify: `app/src/test/java/com/novelreader/ui/reader/ReaderViewModelTest.kt`

**Interfaces (consumed by later tasks / callers):**
- `ReaderConfig.keepScreenOn: Boolean` (default `true`) — consumed by `SettingsSheet` (Switch) and `ReaderScreen` (`LocalView.current.keepScreenOn`).
- `ReaderPreferences.updateKeepScreenOn(value: Boolean): Unit` — consumed by `ReaderViewModel.updateKeepScreenOn`.
- `ReaderViewModel.updateKeepScreenOn(value: Boolean): Unit` — consumed by `ReaderScreen` (plumbed to SettingsSheet).
- `SettingsSheet(... , onKeepScreenOnChange: (Boolean) -> Unit)` — consumed by `ReaderScreen`.

**Sub-task 1a — `ReaderConfig.keepScreenOn` field + pref key + updater + round-trip test**

- [ ] **Step 1: Write the failing `ReaderPreferencesTest`**

Create `app/src/test/java/com/novelreader/data/local/preferences/ReaderPreferencesTest.kt`:

```kotlin
package com.novelreader.data.local.preferences

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReaderPreferencesTest {

    @Test
    fun `keepScreenOn defaults to true`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        assertThat(prefs.config.first().keepScreenOn).isTrue()
    }

    @Test
    fun `updateKeepScreenOn false persists`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        prefs.updateKeepScreenOn(false)
        assertThat(prefs.config.first().keepScreenOn).isFalse()
        prefs.updateKeepScreenOn(true)
        assertThat(prefs.config.first().keepScreenOn).isTrue()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.data.local.preferences.ReaderPreferencesTest
```

Expected: FAIL — `keepScreenOn` is not a property of `ReaderConfig`.

- [ ] **Step 3: Add `keepScreenOn` to `ReaderConfig`, the key, the mapping, and the updater**

Edit `app/src/main/java/com/novelreader/data/local/preferences/ReaderPreferences.kt`:

1. Add the import at the top with the other `androidx.datastore.preferences.core` imports:
   ```kotlin
   import androidx.datastore.preferences.core.booleanPreferencesKey
   ```
2. Add the field to `ReaderConfig` (keep existing fields, add at the end):
   ```kotlin
   data class ReaderConfig(
       val fontSize: Int = 20,
       val fontFamily: String = "serif",
       val lineHeight: Float = 1.8f,
       val theme: String = "light",
       val autoScrollSpeed: Float = 0f,
       val keepScreenOn: Boolean = true
   )
   ```
3. Add the key inside `Keys`:
   ```kotlin
   val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
   ```
4. Add the field to the `config` mapping:
   ```kotlin
   val config: Flow<ReaderConfig> = context.dataStore.data.map { prefs ->
       ReaderConfig(
           fontSize = prefs[Keys.FONT_SIZE] ?: 20,
           fontFamily = prefs[Keys.FONT_FAMILY] ?: "serif",
           lineHeight = prefs[Keys.LINE_HEIGHT]?.toFloatOrNull() ?: 1.8f,
           theme = prefs[Keys.THEME] ?: "light",
           autoScrollSpeed = prefs[Keys.AUTO_SCROLL_SPEED]?.toFloatOrNull() ?: 0f,
           keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true
       )
   }
   ```
5. Add the updater at the end of the class:
   ```kotlin
   suspend fun updateKeepScreenOn(value: Boolean) {
       context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }
   }
   ```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.data.local.preferences.ReaderPreferencesTest
```

Expected: PASS (2/2).

**Sub-task 1b — `ReaderViewModel.updateKeepScreenOn` + VM test**

- [ ] **Step 1: Add a failing VM test case**

In `app/src/test/java/com/novelreader/ui/reader/ReaderViewModelTest.kt`, add this test method (keep the existing `setUp` and `createViewModel`; the file already mocks `readerPrefs`):

```kotlin
@Test
fun `updateKeepScreenOn calls readerPreferences updateKeepScreenOn`() = runTest {
    viewModel = createViewModel()
    viewModel.updateKeepScreenOn(false)
    coVerify { readerPrefs.updateKeepScreenOn(false) }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.ui.reader.ReaderViewModelTest.updateKeepScreenOn*
```

Expected: FAIL — `updateKeepScreenOn` not defined on `ReaderViewModel`.

- [ ] **Step 3: Add the method to `ReaderViewModel`**

In `app/src/main/java/com/novelreader/ui/reader/ReaderViewModel.kt`, add this method (modeled on the existing `updateAutoScrollSpeed` at lines 261–263):

```kotlin
fun updateKeepScreenOn(value: Boolean) {
    viewModelScope.launch { readerPreferences.updateKeepScreenOn(value) }
}
```

- [ ] **Step 4: Run the VM test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.ui.reader.ReaderViewModelTest
```

Expected: PASS for all cases in the file.

**Sub-task 1c — `SettingsSheet` keep-screen-on Switch + strings**

- [ ] **Step 1: Add the pt-BR and en strings (Task 1's strings only)**

In `app/src/main/res/values/strings.xml`, add (do not add the other tasks' strings yet):

```xml
<string name="reader_keep_screen_on">Manter tela ligada</string>
```

In `app/src/main/res/values-en/strings.xml`, add:

```xml
<string name="reader_keep_screen_on">Keep screen on</string>
```

- [ ] **Step 2: Extend the `SettingsSheet` signature and add the Switch row**

In `app/src/main/java/com/novelreader/ui/reader/ReaderSettingsSheet.kt`:

1. Add the `Switch` and `Row` imports if not present (the file currently uses no `Switch`):
   ```kotlin
   import androidx.compose.foundation.layout.Row
   import androidx.compose.material3.Switch
   ```
   (The file already imports `Row` and `MaterialTheme`; check and add `Switch` import only if absent.)
2. Change the function signature to add the callback:
   ```kotlin
   @OptIn(ExperimentalMaterial3Api::class)
   @Composable
   fun SettingsSheet(
       config: ReaderConfig,
       onThemeChange: (String) -> Unit,
       onFontSizeChange: (Int) -> Unit,
       onLineHeightChange: (Float) -> Unit,
       onAutoScrollSpeedChange: (Float) -> Unit,
       onKeepScreenOnChange: (Boolean) -> Unit,
       onDismiss: () -> Unit
   ) {
   ```
3. Add the keep-screen-on `Row` directly **above** the auto-scroll `Row` (the auto-scroll block currently starts at the `Row` containing `TouchApp` icon at line 149). Insert this block immediately before that `Row`:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        stringResource(R.string.reader_keep_screen_on),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f)
    )
    Switch(
        checked = config.keepScreenOn,
        onCheckedChange = onKeepScreenOnChange
    )
}

Spacer(modifier = Modifier.height(16.dp))
HorizontalDivider()
Spacer(modifier = Modifier.height(16.dp))
```

(Use the file's existing padding/spacer style; the `Spacer` + `HorizontalDivider` + `Spacer` mirrors the dividers between the other settings sections.)

- [ ] **Step 3: Plumb the callback in `ReaderScreen` and add haptics + keepScreenOn wiring**

In `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt`:

1. **Add the keep-screen-on `SideEffect`-style wiring** using `LaunchedEffect`. Place this immediately after the existing `val haptic = LocalHapticFeedback.current` at line 106 (the same spot where the `haptic` is captured):

```kotlin
LaunchedEffect(state.config.keepScreenOn) {
    LocalView.current.keepScreenOn = state.config.keepScreenOn
}
```

Add the `LocalView` import at the top of the file with the other `androidx.compose.ui.platform` imports:

```kotlin
import androidx.compose.ui.platform.LocalView
```

(Check whether the file already imports `LocalView` — `ReaderScreen.kt` does not import it today; add the import line.)

2. **Plumb the `onKeepScreenOnChange` callback** into the `SettingsSheet` call site (lines 187–196). Change the call to:

```kotlin
if (state.showSettings) {
    SettingsSheet(
        config = state.config,
        onThemeChange = { viewModel.updateTheme(it) },
        onFontSizeChange = { viewModel.updateFontSize(it) },
        onLineHeightChange = { viewModel.updateLineHeight(it) },
        onAutoScrollSpeedChange = { viewModel.updateAutoScrollSpeed(it) },
        onKeepScreenOnChange = { viewModel.updateKeepScreenOn(it) },
        onDismiss = { viewModel.hideSettings() }
    )
}
```

3. **Add `HapticFeedbackType.LongPress`** to the `onClick` of the 6 toolbar `IconButton`s. The `haptic` is already captured at line 106. For each of the 6 buttons listed below, add `haptic?.performHapticFeedback(HapticFeedbackType.LongPress)` as the FIRST line inside the `onClick` lambda. Concretely:

   - **Search** (line 310): change `IconButton(onClick = { viewModel.activateSearch() })` to `IconButton(onClick = { haptic?.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.activateSearch() })`.
   - **Previous** (line 358): inside the `onClick` lambda, prepend `haptic?.performHapticFeedback(HapticFeedbackType.LongPress)`.
   - **Bookmark** (line 373): same — prepend the haptic call inside the lambda. (The lambda body is multi-line; the haptic call goes on its own line at the top.)
   - **Settings** (line 412): same.
   - **Chapter list** (line 419): same.
   - **Next** (line 428): same.

   The `HapticFeedbackType` and `LocalHapticFeedback` imports are already present (lines 66–67). No new imports needed.

- [ ] **Step 4: Run the full unit test suite + compile**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All tests pass (baseline 163 + 2 new pref tests + 1 new VM test = 166+).

- [ ] **Step 5: Commit (exact-path `git add` only)**

```bash
git add \
  app/src/main/java/com/novelreader/data/local/preferences/ReaderPreferences.kt \
  app/src/main/java/com/novelreader/ui/reader/ReaderViewModel.kt \
  app/src/main/java/com/novelreader/ui/reader/ReaderSettingsSheet.kt \
  app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-en/strings.xml \
  app/src/test/java/com/novelreader/data/local/preferences/ReaderPreferencesTest.kt \
  app/src/test/java/com/novelreader/ui/reader/ReaderViewModelTest.kt
git diff --cached --stat   # VERIFY: only the 8 files above, no 403 files
git commit -m "feat: reader toolbar haptics + keep-screen-on setting

Haptics on the 6 reader toolbar IconButtons (search, prev, bookmark,
settings, chapter list, next) — HapticFeedbackType.LongPress to match the
v2.4.2 pattern. ReaderConfig.keepScreenOn (default true) with a Switch
in the reader settings sheet, persists via ReaderPreferences, wired to
LocalView.current.keepScreenOn in ReaderScreen (auto-clears when the
reader leaves the foreground)."
```

The `git diff --cached --stat` MUST show only the 8 listed files. If any 403 file appears, abort with `git restore --staged .` and re-add with the explicit paths.

---

## Task 2: Chapter-list search-in-picker (Commit 2)

**Files:**
- Create: `app/src/main/java/com/novelreader/ui/reader/ChapterListFilter.kt`
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt` (add search field + filtered list + header + empty state in the chapter-list `ModalBottomSheet`)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/test/java/com/novelreader/ui/reader/ChapterListFilterTest.kt`

**Interfaces:**
- `fun filterChaptersByQuery(chapters: List<ChapterEntity>, query: String): List<ChapterEntity>` — pure function. Blank query returns `chapters`; non-blank returns `chapters.filter { it.title.contains(query.trim(), ignoreCase = true) }`. Consumed by `ReaderScreen` chapter-list sheet.

- [ ] **Step 1: Write the failing `ChapterListFilterTest`**

Create `app/src/test/java/com/novelreader/ui/reader/ChapterListFilterTest.kt`:

```kotlin
package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.ChapterEntity
import org.junit.Test

class ChapterListFilterTest {

    private fun ch(id: Long, title: String) = ChapterEntity(
        id = id, novelId = 1L, title = title, fileName = "ch$id.html", orderIndex = id.toInt(), content = ""
    )

    @Test
    fun `blank query returns all chapters unchanged`() {
        val list = listOf(ch(1, "Chapter 1 - Beginning"), ch(2, "Chapter 2 - Middle"), ch(3, "Chapter 3 - End"))
        assertThat(filterChaptersByQuery(list, "")).isEqualTo(list)
        assertThat(filterChaptersByQuery(list, "   ")).isEqualTo(list)
    }

    @Test
    fun `matching query returns only matches`() {
        val list = listOf(ch(1, "Chapter 1 - Beginning"), ch(2, "Chapter 2 - Middle"), ch(3, "Chapter 3 - End"))
        val result = filterChaptersByQuery(list, "Middle")
        assertThat(result.map { it.id }).containsExactly(2L)
    }

    @Test
    fun `non-matching query returns empty`() {
        val list = listOf(ch(1, "Chapter 1"), ch(2, "Chapter 2"))
        assertThat(filterChaptersByQuery(list, "Nonexistent")).isEmpty()
    }

    @Test
    fun `query is case-insensitive`() {
        val list = listOf(ch(1, "The Beginning"), ch(2, "The Middle"))
        val result = filterChaptersByQuery(list, "the")
        assertThat(result.map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `query is trimmed`() {
        val list = listOf(ch(1, "The Beginning"), ch(2, "The Middle"))
        val result = filterChaptersByQuery(list, "  Beginning  ")
        assertThat(result.map { it.id }).containsExactly(1L)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.ui.reader.ChapterListFilterTest
```

Expected: FAIL — `filterChaptersByQuery` not defined.

- [ ] **Step 3: Create `ChapterListFilter.kt`**

Create `app/src/main/java/com/novelreader/ui/reader/ChapterListFilter.kt`:

```kotlin
package com.novelreader.ui.reader

import com.novelreader.data.local.db.entity.ChapterEntity

fun filterChaptersByQuery(chapters: List<ChapterEntity>, query: String): List<ChapterEntity> {
    if (query.isBlank()) return chapters
    val needle = query.trim()
    return chapters.filter { it.title.contains(needle, ignoreCase = true) }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.ui.reader.ChapterListFilterTest
```

Expected: PASS (5/5).

- [ ] **Step 5: Add the 3 pt-BR and 3 en strings (Task 2's strings only)**

In `app/src/main/res/values/strings.xml`, add (do not add other tasks' strings):

```xml
<string name="chapter_list_search_hint">Buscar capítulo...</string>
<string name="chapters_count_filtered">%1$d de %2$d capítulos</string>
<string name="chapter_list_no_matches">Nenhum capítulo encontrado</string>
```

In `app/src/main/res/values-en/strings.xml`, add:

```xml
<string name="chapter_list_search_hint">Search chapter...</string>
<string name="chapters_count_filtered">%1$d of %2$d chapters</string>
<string name="chapter_list_no_matches">No chapters found</string>
```

- [ ] **Step 6: Add the search field + filter + empty state to the chapter-list sheet in `ReaderScreen.kt`**

In `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt`:

1. Add a `var chapterSearchQuery by remember { mutableStateOf("") }` local state. Place it next to the other `var` declarations at the top of `ReaderScreen` (around line 104, near `var showChapterList by remember { mutableStateOf(false) }`):

```kotlin
var showChapterList by remember { mutableStateOf(false) }
var chapterSearchQuery by remember { mutableStateOf("") }
```

2. Modify the `if (showChapterList && state.allChapters.isNotEmpty()) { ... }` block (starts at line 215). Replace the `Column { Text(...chapters_count...) LazyColumn { ... } }` with the version below. The key changes: (a) the header `Text` shows `chapters_count_filtered` when filtering; (b) the `LazyColumn` is hidden and a no-match `Text` is shown when filtering yields zero; (c) the `OutlinedTextField` search field sits above the header; (d) `filtered` is computed via `remember`.

```kotlin
if (showChapterList && state.allChapters.isNotEmpty()) {
    val chapterListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filtered = remember(chapterSearchQuery, state.allChapters) {
        filterChaptersByQuery(state.allChapters, chapterSearchQuery)
    }
    ModalBottomSheet(
        onDismissRequest = {
            chapterSearchQuery = ""
            showChapterList = false
        },
        sheetState = chapterListSheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = chapterSearchQuery,
                onValueChange = { chapterSearchQuery = it },
                placeholder = { Text(stringResource(R.string.chapter_list_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = if (chapterSearchQuery.isNotBlank())
                    stringResource(R.string.chapters_count_filtered, filtered.size, state.allChapters.size)
                else
                    stringResource(R.string.chapters_count, state.allChapters.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (chapterSearchQuery.isNotBlank() && filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.chapter_list_no_matches),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filtered, key = { it.id }) { chapter ->
                        val isCurrent = chapter.id == state.chapter?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    saveScroll()
                                    viewModel.loadChapter(chapter.id)
                                    chapterSearchQuery = ""
                                    showChapterList = false
                                }
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent || !chapter.isRead) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (chapter.isRead && !isCurrent)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
```

(The `OutlinedTextField` is already imported at line 49; the `Box` is already imported. No new imports needed.)

- [ ] **Step 7: Run the full unit test suite + compile**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All tests pass (baseline + 5 new filter tests).

- [ ] **Step 8: Commit (exact-path `git add`)**

```bash
git add \
  app/src/main/java/com/novelreader/ui/reader/ChapterListFilter.kt \
  app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-en/strings.xml \
  app/src/test/java/com/novelreader/ui/reader/ChapterListFilterTest.kt
git diff --cached --stat   # VERIFY: only the 5 files
git commit -m "feat: chapter-list sheet search

OutlinedTextField in the reader's chapter-list ModalBottomSheet filters
the list by chapter title (case-insensitive, trimmed). When filtering,
the header shows 'X of Y chapters' and a 'no chapters found' line
replaces the empty list. filterChaptersByQuery is a pure function in
its own file, unit-tested. Search query is cleared on dismiss and on
chapter tap. LazyColumn key=it.id preserves item identity across
filter changes."
```

The `git diff --cached --stat` MUST show only the 5 files.

---

## Task 3: Favorites search + created-date + RelativeTime i18n fix (Commit 3)

**Files:**
- Modify: `app/src/main/java/com/novelreader/util/RelativeTime.kt`
- Modify: `app/src/main/java/com/novelreader/ui/favorites/FavoritesViewModel.kt`
- Modify: `app/src/main/java/com/novelreader/ui/favorites/FavoritesScreen.kt` (search field + created-date line)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/test/java/com/novelreader/util/RelativeTimeTest.kt` (rewrite under Robolectric)
- Modify: `app/src/test/java/com/novelreader/ui/favorites/FavoritesViewModelTest.kt` (add filtering cases)

**Interfaces:**
- `formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String?` — rewritten to be locale-aware (uses `android.text.format.DateUtils.getRelativeTimeSpanString`). Guards: `timestampMs <= 0` → null; future (`nowMs < timestampMs`) → null; else non-null locale-aware string.
- `FavoritesViewModel.setSearchQuery(query: String): Unit` — new setter.
- `FavoritesViewModel.displayItems: StateFlow<List<BookmarkDisplayItem>>` — now a `combine(bookmarksFlow, _searchQuery)` flow; filtered by query matching bookmark title / chapter title / note (case-insensitive), null-safe.

**Sub-task 3a — Rewrite `RelativeTime` + tests**

- [ ] **Step 1: Rewrite the failing test for the new behavior**

Replace `app/src/test/java/com/novelreader/util/RelativeTimeTest.kt` with the version below. The exact-string assertions are replaced by property assertions because `DateUtils.getRelativeTimeSpanString` output depends on the Android version and the system locale. Setting `Locale.setDefault` in specific tests verifies locale-awareness without coupling to exact wording.

```kotlin
package com.novelreader.util

import android.text.format.DateUtils
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RelativeTimeTest {

    private val now = 1_700_000_000_000L
    private var originalLocale: Locale? = null

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        // Trigger DateUtils initialization under Robolectric.
        ApplicationProvider.getApplicationContext<android.content.Context>()
    }

    @After
    fun tearDown() {
        originalLocale?.let { Locale.setDefault(it) }
    }

    @Test
    fun `now returns non-empty string`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now, now)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `30 seconds ago returns non-empty string`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now - 30_000L, now)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `5 minutes ago in en contains the number and minutes word`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now - 5 * 60_000L, now)
        assertThat(result).isNotNull()
        assertThat(result!!).contains("5")
        assertThat(result.lowercase()).contains("min")
    }

    @Test
    fun `2 hours ago in en contains the number and hours word`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now - 2L * 3_600_000L, now)
        assertThat(result).isNotNull()
        assertThat(result!!).contains("2")
        assertThat(result.lowercase()).contains("hour")
    }

    @Test
    fun `3 days ago in en contains the number and days word`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now - 3L * 86_400_000L, now)
        assertThat(result).isNotNull()
        assertThat(result!!).contains("3")
        assertThat(result.lowercase()).contains("day")
    }

    @Test
    fun `5 minutes ago in pt-BR contains the number and ha prefix`() {
        Locale.setDefault(Locale("pt", "BR"))
        val result = formatRelativeTime(now - 5 * 60_000L, now)
        assertThat(result).isNotNull()
        assertThat(result!!).contains("5")
        assertThat(result).contains("há")
    }

    @Test
    fun `60 days ago returns a non-null string from DateUtils`() {
        Locale.setDefault(Locale.US)
        // DateUtils handles > 1 month itself (returns an absolute-ish relative string).
        val result = formatRelativeTime(now - 60L * 86_400_000L, now)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `non-positive timestamp returns null`() {
        assertThat(formatRelativeTime(0L, now)).isNull()
        assertThat(formatRelativeTime(-1L, now)).isNull()
    }

    @Test
    fun `future timestamp returns null`() {
        assertThat(formatRelativeTime(now + 60_000L, now)).isNull()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.util.RelativeTimeTest
```

Expected: FAIL — the old `formatRelativeTime` returns hardcoded PT-BR strings, so `contains("hour")` and `contains("day")` (en) will fail. (And the `@RunWith(RobolectricTestRunner::class)` change means even the JVM-only tests now require the Robolectric runner.)

- [ ] **Step 3: Rewrite `RelativeTime.kt`**

Replace `app/src/main/java/com/novelreader/util/RelativeTime.kt` with:

```kotlin
package com.novelreader.util

import android.text.format.DateUtils

fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
    if (timestampMs <= 0L) return null
    if (nowMs < timestampMs) return null
    return DateUtils.getRelativeTimeSpanString(
        timestampMs,
        nowMs,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.util.RelativeTimeTest
```

Expected: PASS (9/9). If Robolectric's `DateUtils` output does not match the property assertions (e.g., the "hour" word is not present in the en abbreviated form), adjust the assertion to a less-specific property (e.g., `contains("2")`) and document the reason. The drive-by goal is locale-awareness, not exact wording.

**Sub-task 3b — `FavoritesViewModel` search + filtering**

- [ ] **Step 1: Add failing VM test cases for filtering**

In `app/src/test/java/com/novelreader/ui/favorites/FavoritesViewModelTest.kt`, the file already has `setUp`, `novel`, `chapter`, `bookmark` fixtures and an `init_loadsDisplayItems` test. Add the following test methods (place them after the existing tests in the file). The fixture bookmark has `title = "Great quote"`, `note = "Amazing description"`, `chapter.title = "The Beginning"`. Add an additional fixture bookmark for the no-chapter case.

```kotlin
private val bookmarkNoChapter = BookmarkEntity(
    id = 101, chapterId = 999L, title = "Orphan note", page = 0, note = "standalone"
)

@Test
fun setSearchQuery_blankQuery_returnsAll() = runTest {
    every { bookmarkDao.getAll() } returns flowOf(listOf(bookmark))
    coEvery { chapterDao.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
    coEvery { novelDao.getNovelsByIds(listOf(1L)) } returns listOf(novel)

    viewModel = FavoritesViewModel(novelDao, chapterDao, bookmarkDao)
    viewModel.setSearchQuery("nonexistent")
    viewModel.setSearchQuery("")

    viewModel.displayItems.test {
        val items = awaitItem()
        assertThat(items).hasSize(1)
        assertThat(items[0].bookmark.id).isEqualTo(100L)
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun setSearchQuery_matchesBookmarkTitle() = runTest {
    every { bookmarkDao.getAll() } returns flowOf(listOf(bookmark))
    coEvery { chapterDao.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
    coEvery { novelDao.getNovelsByIds(listOf(1L)) } returns listOf(novel)

    viewModel = FavoritesViewModel(novelDao, chapterDao, bookmarkDao)
    viewModel.setSearchQuery("Great")

    viewModel.displayItems.test {
        val items = awaitItem()
        assertThat(items).hasSize(1)
        assertThat(items[0].bookmark.id).isEqualTo(100L)
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun setSearchQuery_matchesChapterTitle() = runTest {
    every { bookmarkDao.getAll() } returns flowOf(listOf(bookmark))
    coEvery { chapterDao.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
    coEvery { novelDao.getNovelsByIds(listOf(1L)) } returns listOf(novel)

    viewModel = FavoritesViewModel(novelDao, chapterDao, bookmarkDao)
    viewModel.setSearchQuery("Beginning")

    viewModel.displayItems.test {
        val items = awaitItem()
        assertThat(items).hasSize(1)
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun setSearchQuery_matchesNote() = runTest {
    every { bookmarkDao.getAll() } returns flowOf(listOf(bookmark))
    coEvery { chapterDao.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
    coEvery { novelDao.getNovelsByIds(listOf(1L)) } returns listOf(novel)

    viewModel = FavoritesViewModel(novelDao, chapterDao, bookmarkDao)
    viewModel.setSearchQuery("Amazing")

    viewModel.displayItems.test {
        val items = awaitItem()
        assertThat(items).hasSize(1)
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun setSearchQuery_isCaseInsensitive() = runTest {
    every { bookmarkDao.getAll() } returns flowOf(listOf(bookmark))
    coEvery { chapterDao.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
    coEvery { novelDao.getNovelsByIds(listOf(1L)) } returns listOf(novel)

    viewModel = FavoritesViewModel(novelDao, chapterDao, bookmarkDao)
    viewModel.setSearchQuery("GREAT")

    viewModel.displayItems.test {
        val items = awaitItem()
        assertThat(items).hasSize(1)
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun setSearchQuery_nonMatching_returnsEmpty() = runTest {
    every { bookmarkDao.getAll() } returns flowOf(listOf(bookmark))
    coEvery { chapterDao.getChaptersByIds(listOf(10L)) } returns listOf(chapter)
    coEvery { novelDao.getNovelsByIds(listOf(1L)) } returns listOf(novel)

    viewModel = FavoritesViewModel(noveldao, chapterdao, bookmarkdao)
    viewModel.setSearchQuery("zzz-no-match")

    viewModel.displayItems.test {
        val items = awaitItem()
        assertThat(items).isEmpty()
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun setSearchQuery_nullChapter_stillMatchesOnBookmarkTitle() = runTest {
    every { bookmarkDao.getAll() } returns flowOf(listOf(bookmarkNoChapter))
    coEvery { chapterDao.getChaptersByIds(listOf(999L)) } returns emptyList()

    viewModel = FavoritesViewModel(novelDao, chapterDao, bookmarkDao)
    viewModel.setSearchQuery("Orphan")

    viewModel.displayItems.test {
        val items = awaitItem()
        assertThat(items).hasSize(1)
        assertThat(items[0].chapter).isNull()
        cancelAndConsumeRemainingEvents()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.ui.favorites.FavoritesViewModelTest
```

Expected: FAIL — `setSearchQuery` not defined on `FavoritesViewModel`.

- [ ] **Step 3: Refactor `FavoritesViewModel` to use `combine` and add `setSearchQuery`**

Replace the body of `app/src/main/java/com/novelreader/ui/favorites/FavoritesViewModel.kt` (keep the `BookmarkDisplayItem` data class, the class declaration, and the existing constructor signature):

```kotlin
package com.novelreader.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkDisplayItem(
    val bookmark: BookmarkEntity,
    val chapter: ChapterEntity?,
    val novel: NovelEntity?
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val displayItems: StateFlow<List<BookmarkDisplayItem>> = combine(
        bookmarkDao.getAll(),
        _searchQuery
    ) { bookmarks, query ->
        buildDisplayItems(bookmarks, query)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            try {
                bookmarkDao.deleteById(id)
            } catch (_: Exception) {
                // best-effort delete
            }
        }
    }

    private suspend fun buildDisplayItems(
        bookmarks: List<BookmarkEntity>,
        query: String
    ): List<BookmarkDisplayItem> {
        if (bookmarks.isEmpty()) return emptyList()
        val chapterIds = bookmarks.map { it.chapterId }.distinct()
        val chapters = chapterDao.getChaptersByIds(chapterIds).associateBy { it.id }
        val novelIds = chapters.values.map { it.novelId }.distinct()
        val novels = novelDao.getNovelsByIds(novelIds).associateBy { it.id }
        val needle = query.trim()
        return bookmarks
            .map { bookmark ->
                val chapter = chapters[bookmark.chapterId]
                val novel = chapter?.let { novels[it.novelId] }
                BookmarkDisplayItem(bookmark, chapter, novel)
            }
            .filter { item ->
                if (needle.isEmpty()) return@filter true
                val bTitle = item.bookmark.title
                val cTitle = item.chapter?.title ?: ""
                val bNote = item.bookmark.note ?: ""
                bTitle.contains(needle, ignoreCase = true) ||
                    cTitle.contains(needle, ignoreCase = true) ||
                    bNote.contains(needle, ignoreCase = true)
            }
    }
}
```

- [ ] **Step 4: Run the VM tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.ui.favorites.FavoritesViewModelTest
```

Expected: PASS for all cases (the original `init_loadsDisplayItems` and the 7 new ones). If the original test's `awaitItem()` timing changes because `displayItems` is now a `stateIn` flow with `Eagerly`, add a `cancelAndConsumeRemainingEvents()` at the end of the original test if it doesn't already have one.

**Sub-task 3c — `FavoritesScreen` search field + created-date + strings**

- [ ] **Step 1: Add the pt-BR and en strings (Task 3's strings only)**

In `app/src/main/res/values/strings.xml`, add:

```xml
<string name="favorites_search_hint">Buscar favoritos...</string>
```

In `app/src/main/res/values-en/strings.xml`, add:

```xml
<string name="favorites_search_hint">Search favorites...</string>
```

- [ ] **Step 2: Modify `FavoritesScreen.kt`**

In `app/src/main/java/com/novelreader/ui/favorites/FavoritesScreen.kt`:

1. Add imports at the top (the file currently has `OutlinedTextField`? No — check. If absent, add):
   ```kotlin
   import androidx.compose.material3.OutlinedTextField
   import com.novelreader.util.formatRelativeTime
   import java.text.DateFormat
   import java.util.Date
   import java.util.Locale
   ```
2. In the `FavoritesScreen` composable, after the `Scaffold { padding -> ... }` block's `if (items.isEmpty()) { ... }` branch, add the search field. The cleanest placement: just inside the `Scaffold` content lambda, ABOVE the `if (items.isEmpty())` check (so the field is always visible, even when the filtered list is empty). Insert this right after `} else {`:

Actually, the spec says "always-visible, since the screen is small; cleaner than an expanding icon." Add it at the top of the `Scaffold` content, BEFORE the `if (items.isEmpty())` check, so it's always present. Replace the current structure (lines 86–152) with the version below:

```kotlin
) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        OutlinedTextField(
            value = viewModel.searchQuery.collectAsState().value,
            onValueChange = viewModel::setSearchQuery,
            placeholder = { Text(stringResource(R.string.favorites_search_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(80.dp))
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.no_favorites),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.add_favorites_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                itemsIndexed(items, key = { _, item -> item.bookmark.id }) { index, item ->
                    val isFirstForNovel = index == 0 || items[index - 1].novel?.id != item.novel?.id
                    if (isFirstForNovel) {
                        item.novel?.let { novel ->
                            Text(
                                text = novel.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 12.dp,
                                    bottom = 4.dp
                                )
                            )
                        }
                    }
                    BookmarkItem(
                        title = item.bookmark.title,
                        chapterTitle = item.chapter?.title ?: "",
                        note = item.bookmark.note,
                        createdAt = item.bookmark.createdAt,
                        onClick = {
                            item.chapter?.let { chapter ->
                                onChapterClick(chapter.novelId, chapter.id)
                            }
                        },
                        onDelete = { bookmarkToDelete = item.bookmark.id }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
```

3. Modify the `BookmarkItem` private composable (lines 156–207) to accept a new `createdAt: Long` parameter and render a third text line. Replace the `BookmarkItem` signature and add the new line BELOW the note block (after `note?.let { ... }` at line 195). The full replacement:

```kotlin
@Composable
private fun BookmarkItem(
    title: String,
    chapterTitle: String,
    note: String?,
    createdAt: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            note?.let { n ->
                if (n.isNotBlank()) {
                    Text(
                        text = n,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = formatRelativeTime(createdAt) ?: DateFormat
                    .getDateInstance(DateFormat.SHORT, Locale.getDefault())
                    .format(Date(createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.remove),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

4. Expose `searchQuery` as a `StateFlow` on the ViewModel so the screen can `collectAsState` it. Add to `FavoritesViewModel`:

```kotlin
val searchQuery: StateFlow<String> = _searchQuery
```

Add this line right after `private val _searchQuery = MutableStateFlow("")`. The screen then reads `viewModel.searchQuery.collectAsState().value`. (`MutableStateFlow` is already a `StateFlow`.)

- [ ] **Step 3: Run the full unit test suite + compile**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All tests pass (baseline + 5 chapter filter + 2 pref + 1 VM updateKeepScreenOn + 7 Favorites VM + 9 RelativeTime = new tests added; the rewritten RelativeTimeTest is 9 cases replacing the old 8).

- [ ] **Step 4: Commit (exact-path `git add`)**

```bash
git add \
  app/src/main/java/com/novelreader/util/RelativeTime.kt \
  app/src/main/java/com/novelreader/ui/favorites/FavoritesViewModel.kt \
  app/src/main/java/com/novelreader/ui/favorites/FavoritesScreen.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-en/strings.xml \
  app/src/test/java/com/novelreader/util/RelativeTimeTest.kt \
  app/src/test/java/com/novelreader/ui/favorites/FavoritesViewModelTest.kt
git diff --cached --stat   # VERIFY: only the 7 files
git commit -m "feat: favorites search + created-date + RelativeTime i18n fix

FavoritesViewModel: combine(bookmarksFlow, _searchQuery) producing
filtered displayItems (matches bookmark title / chapter title / note,
case-insensitive, null-safe for orphaned bookmarks). Exposed
searchQuery as StateFlow.

FavoritesScreen: OutlinedTextField below the TopAppBar bound to
viewModel.searchQuery; BookmarkItem shows the bookmark createdAt as a
locale-aware relative time (with absolute-date fallback via
DateFormat.getDateInstance).

Drive-by: formatRelativeTime rewritten to use
android.text.format.DateUtils.getRelativeTimeSpanString for
locale-aware output. Fixes the PT-BR-only reading badge in
NovelCard.kt:158 for English users at the same call site (no
NovelCard change). RelativeTimeTest moved to Robolectric and asserts
properties (non-null, contains the number) rather than exact strings
to be robust to Android-version wording."
```

The `git diff --cached --stat` MUST show only the 7 files.

---

## Task 4: Chapter title cleaning — parser source fix + display-side (Commit 4)

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/parser/TitleExtractor.kt` (extend `SEPARATORS` with `": "`; add `cleanChapterTitleForDisplay`)
- Modify: `app/src/main/java/com/novelreader/data/parser/FreeWebNovelParser.kt` (delegate `parseChapterTitle` to `TitleExtractor.extractChapterTitle`; strip novel prefix in h2/h1 fallbacks)
- Modify: `app/src/main/java/com/novelreader/data/parser/ReadNovelFullParser.kt` (same delegation + fallback stripping)
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt` (2 display sites: toolbar + chapter list)
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt` (+ `novelTitle: String?` param; use at display site)
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt` (pass `novelTitle = selectedNovel?.title` at the call site)
- Modify: `app/src/main/java/com/novelreader/ui/favorites/FavoritesScreen.kt` (1 display site: chapter-title line in `BookmarkItem` — but `BookmarkItem` currently takes `chapterTitle: String`. Clean the title at the call site in `FavoritesScreen`, not inside `BookmarkItem`, so the composable stays a dumb renderer.)
- Create: `app/src/test/java/com/novelreader/data/parser/TitleExtractorTest.kt`
- Modify: `app/src/test/java/com/novelreader/data/parser/FreeWebNovelParserTest.kt` (add en-dash/em-dash/pipe fixtures)
- Modify: `app/src/test/java/com/novelreader/data/parser/ReadNovelFullParserTest.kt` (add en-dash/em-dash/pipe fixtures)

**Interfaces:**
- `TitleExtractor.cleanChapterTitleForDisplay(title: String, novelTitle: String?): String` — strips a leading `novelTitle` (case-insensitive) if immediately followed by a separator in `SEPARATORS`; else returns `title` unchanged. Pure.
- `TitleExtractor.SEPARATORS` — extended with `": "` (strict improvement for `extractChapterTitle` too).

- [ ] **Step 1: Write the failing `TitleExtractorTest` for `cleanChapterTitleForDisplay`**

Create `app/src/test/java/com/novelreader/data/parser/TitleExtractorTest.kt`:

```kotlin
package com.novelreader.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TitleExtractorTest {

    @Test
    fun `null novel title returns title unchanged`() {
        assertThat(TitleExtractor.cleanChapterTitleForDisplay("Chapter 5 - Title", null)).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `blank novel title returns title unchanged`() {
        assertThat(TitleExtractor.cleanChapterTitleForDisplay("Chapter 5 - Title", "")).isEqualTo("Chapter 5 - Title")
        assertThat(TitleExtractor.cleanChapterTitleForDisplay("Chapter 5 - Title", "   ")).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `strips novel name prefix with ASCII hyphen separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name - Chapter 5 - Title", "Novel Name")
        ).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `strips novel name prefix with en-dash separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name – Chapter 5", "Novel Name")
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `strips novel name prefix with em-dash separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name — Chapter 5 - Title", "Novel Name")
        ).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `strips novel name prefix with pipe separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name | Chapter 5", "Novel Name")
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `strips novel name prefix with colon separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name: Chapter 5", "Novel Name")
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `match is case-insensitive`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("novel name - Chapter 5", "Novel Name")
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `no prefix match returns title unchanged`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Chapter 5 - Title", "Some Other Novel")
        ).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `false positive guard - prefix match without separator is not stripped`() {
        // "The Beginning" is a prefix of "The Beginning of the End - Chapter 5" but
        // is not followed by a separator; the function must NOT strip it.
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("The Beginning of the End - Chapter 5", "The Beginning")
        ).isEqualTo("The Beginning of the End - Chapter 5")
    }

    @Test
    fun `prefix with separator is stripped even when novel title is short`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("A - Chapter 5", "A")
        ).isEqualTo("Chapter 5")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.data.parser.TitleExtractorTest
```

Expected: FAIL — `cleanChapterTitleForDisplay` not defined.

- [ ] **Step 3: Extend `TitleExtractor.kt` (SEPARATORS + new function)**

Edit `app/src/main/java/com/novelreader/data/parser/TitleExtractor.kt`:

1. Extend `SEPARATORS` (line 5) to include `": "`:
   ```kotlin
   private val SEPARATORS = listOf(" | ", " – ", " - ", " — ", " :: ", " « ", ": ")
   ```
2. Add a small private helper for the prefix-strip logic, then the new public function. Add these inside the `object TitleExtractor` (after the existing `cleanHtmlTitle` function at the end):

```kotlin
fun cleanChapterTitleForDisplay(title: String, novelTitle: String?): String {
    if (novelTitle.isNullOrBlank()) return title
    if (!title.startsWith(novelTitle, ignoreCase = true)) return title
    val after = title.substring(novelTitle.length).trimStart()
    for (sep in SEPARATORS) {
        if (after.startsWith(sep)) {
            return after.substring(sep.length).trimStart()
        }
    }
    return title
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.data.parser.TitleExtractorTest
```

Expected: PASS (11/11).

- [ ] **Step 5: Add failing parser fixture tests for the source fix**

In `app/src/test/java/com/novelreader/data/parser/FreeWebNovelParserTest.kt`, add these tests (the file already constructs HTML strings and calls `parser.parse(doc, fileName)`):

```kotlin
@Test fun `parses chapter title with en-dash separator stripping novel name`() {
    val html = """
        <html><head><title>Cultivation Novel – Chapter 5 – The Trial</title></head><body>
        <div class="chapter-content"><p>content</p></div>
        </body></html>
    """.trimIndent()
    val doc = Jsoup.parse(html)
    val parsed = parser.parse(doc, "chapter_5.html")
    assertThat(parsed.novelTitle).isEqualTo("Cultivation Novel")
    assertThat(parsed.chapterTitle).isEqualTo("Chapter 5 – The Trial")
}

@Test fun `parses chapter title with pipe separator stripping novel name`() {
    val html = """
        <html><head><title>Cultivation Novel | Chapter 5 | The Trial</title></head><body>
        <div class="chapter-content"><p>content</p></div>
        </body></html>
    """.trimIndent()
    val doc = Jsoup.parse(html)
    val parsed = parser.parse(doc, "chapter_5.html")
    assertThat(parsed.novelTitle).isEqualTo("Cultivation Novel")
    assertThat(parsed.chapterTitle).isEqualTo("Chapter 5 | The Trial")
}
```

In `app/src/test/java/com/novelreader/data/parser/ReadNovelFullParserTest.kt`, add:

```kotlin
@Test fun `parses chapter title with em-dash separator stripping novel name`() {
    val html = """
        <html><head><title>Martial Peak — Chapter 5 — The Trial</title></head><body>
        <h3 class="title" itemprop="name">Martial Peak</h3>
        <div id="chr-content"><p>content</p></div>
        </body></html>
    """.trimIndent()
    val doc = Jsoup.parse(html)
    val parsed = parser.parse(doc, "chapter_5.html")
    assertThat(parsed.novelTitle).isEqualTo("Martial Peak")
    assertThat(parsed.chapterTitle).isEqualTo("Chapter 5 — The Trial")
}
```

- [ ] **Step 6: Run the new parser tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.data.parser.FreeWebNovelParserTest --tests com.novelreader.data.parser.ReadNovelFullParserTest
```

Expected: FAIL — the current parsers do not handle en-dash/em-dash/pipe in the title tag.

- [ ] **Step 7: Refactor `FreeWebNovelParser.parseChapterTitle` to delegate to `TitleExtractor`**

In `app/src/main/java/com/novelreader/data/parser/FreeWebNovelParser.kt`, replace the body of `parseChapterTitle` (lines 46–67) with:

```kotlin
private fun parseChapterTitle(doc: Document, fileName: String): String {
    val novelTitleGuess = parseNovelTitle(doc, fileName)
    val titleTag = doc.title().trim()
    if (titleTag.isNotEmpty()) {
        val extracted = TitleExtractor.extractChapterTitle(titleTag, novelTitleGuess)
        if (extracted != null) return extracted
    }

    val h2 = doc.selectFirst("h2")
    if (h2 != null) {
        val cleaned = TitleExtractor.cleanChapterTitleForDisplay(h2.text().trim(), novelTitleGuess)
        if (cleaned != h2.text().trim()) return cleaned
        if (cleaned.isNotEmpty()) return cleaned
    }

    val h1 = doc.selectFirst("h1")
    if (h1 != null) {
        val text = h1.text().trim()
        if (!text.contains(fileName.substringBeforeLast(".").take(20), ignoreCase = true)) {
            val cleaned = TitleExtractor.cleanChapterTitleForDisplay(text, novelTitleGuess)
            return cleaned
        }
    }

    return fromFileName(fileName)
}
```

Add the import for `TitleExtractor` if not present (it lives in the same package `com.novelreader.data.parser`, so no import is strictly needed since it's in the same package — but check the file's imports to be sure; if Kotlin requires an explicit import for an `object` in the same package, add `import com.novelreader.data.parser.TitleExtractor` — it should not be needed for same-package references).

- [ ] **Step 8: Refactor `ReadNovelFullParser.parseChapterTitle` to delegate to `TitleExtractor`**

In `app/src/main/java/com/novelreader/data/parser/ReadNovelFullParser.kt`, replace the body of `parseChapterTitle` (lines 52–74) with:

```kotlin
private fun parseChapterTitle(doc: Document, fileName: String): String {
    val novelTitleGuess = parseNovelTitle(doc, fileName)
    val chrText = doc.selectFirst("span.chr-text")
    if (chrText != null) {
        val cleaned = TitleExtractor.cleanChapterTitleForDisplay(chrText.text().trim(), novelTitleGuess)
        return cleaned
    }

    val h2 = doc.selectFirst("h2")
    if (h2 != null) {
        val text = h2.text().trim()
        if (text.contains("Chapter", ignoreCase = true)) {
            return TitleExtractor.cleanChapterTitleForDisplay(text, novelTitleGuess)
        }
    }

    val titleTag = doc.title().trim()
    if (titleTag.isNotEmpty()) {
        val extracted = TitleExtractor.extractChapterTitle(titleTag, novelTitleGuess)
        if (extracted != null) return extracted
    }

    return fromFileName(fileName)
}
```

- [ ] **Step 9: Run the parser tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests com.novelreader.data.parser.FreeWebNovelParserTest --tests com.novelreader.data.parser.ReadNovelFullParserTest --tests com.novelreader.data.parser.GenericFallbackParserTest
```

Expected: PASS for the new tests AND all pre-existing parser tests (regression guard). If a pre-existing test breaks because the new delegation strips something it didn't before, the test is asserting the old (buggy) behavior — update the test's expectation to the new correct behavior and add a one-line comment explaining the fix.

- [ ] **Step 10: Wire `cleanChapterTitleForDisplay` into the 4 display sites**

**Site 1 — `ReaderScreen.kt` toolbar title (line 288).** Change:

```kotlin
Text(
    state.chapter?.title ?: "",
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
)
```

to:

```kotlin
Text(
    com.novelreader.data.parser.TitleExtractor.cleanChapterTitleForDisplay(
        state.chapter?.title ?: "",
        state.novel?.title
    ),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
)
```

**Site 2 — `ReaderScreen.kt` chapter list sheet item (line 247).** Change the `Text(text = chapter.title, ...)` inside the `items(filtered, key = { it.id }) { chapter -> ... }` block to:

```kotlin
Text(
    text = com.novelreader.data.parser.TitleExtractor.cleanChapterTitleForDisplay(chapter.title, state.novel?.title),
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = if (isCurrent || !chapter.isRead) FontWeight.SemiBold else FontWeight.Normal,
    color = if (chapter.isRead && !isCurrent)
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    else MaterialTheme.colorScheme.onSurface,
    maxLines = 4,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.weight(1f)
)
```

**Site 3 — `ChaptersTab.kt` library chapter list (line 172).** First, add the `novelTitle` parameter to the `ChaptersTab` function signature. In `app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt`, change the signature (lines 59–70) by adding `novelTitle: String? = null,` (after `novelId: Long = 0L,`):

```kotlin
@Composable
fun ChaptersTab(
    novelId: Long = 0L,
    novelTitle: String? = null,
    chapters: List<ChapterEntity>,
    ...
)
```

Then, change the `Text(text = chapter.title, ...)` at line 172 to:

```kotlin
Text(
    text = com.novelreader.data.parser.TitleExtractor.cleanChapterTitleForDisplay(chapter.title, novelTitle),
    style = MaterialTheme.typography.bodyLarge,
    fontWeight = if (chapter.isRead) FontWeight.Normal else FontWeight.SemiBold,
    maxLines = 4,
    overflow = TextOverflow.Ellipsis,
    color = if (chapter.isRead)
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    else
        MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.weight(1f)
)
```

**Site 4 — `FavoritesScreen.kt` chapter-title line in `BookmarkItem` (line 139).** The cleanest place to clean is at the call site, so `BookmarkItem` stays a dumb renderer. In `FavoritesScreen.kt`, the `BookmarkItem` call passes `chapterTitle = item.chapter?.title ?: ""`. Change it to pass the cleaned value. Import `TitleExtractor` at the top of the file (or use the fully qualified name):

```kotlin
import com.novelreader.data.parser.TitleExtractor
```

Then in the `BookmarkItem(...)` call (around line 139), change:

```kotlin
chapterTitle = item.chapter?.title ?: "",
```

to:

```kotlin
chapterTitle = TitleExtractor.cleanChapterTitleForDisplay(
    item.chapter?.title ?: "",
    item.novel?.title
),
```

**`LibraryScreen.kt` call site.** In `app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt`, at the `ChaptersTab(...)` call (line 301), add the new arg:

```kotlin
1 -> ChaptersTab(
    novelId = selectedNovel?.id ?: 0L,
    novelTitle = selectedNovel?.title,
    chapters = chapters,
    ...
)
```

- [ ] **Step 11: Run the full unit test suite + compile**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All tests pass (baseline + all new tests from Tasks 1–4).

- [ ] **Step 12: Commit (exact-path `git add`)**

```bash
git add \
  app/src/main/java/com/novelreader/data/parser/TitleExtractor.kt \
  app/src/main/java/com/novelreader/data/parser/FreeWebNovelParser.kt \
  app/src/main/java/com/novelreader/data/parser/ReadNovelFullParser.kt \
  app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt \
  app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt \
  app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt \
  app/src/main/java/com/novelreader/ui/favorites/FavoritesScreen.kt \
  app/src/test/java/com/novelreader/data/parser/TitleExtractorTest.kt \
  app/src/test/java/com/novelreader/data/parser/FreeWebNovelParserTest.kt \
  app/src/test/java/com/novelreader/data/parser/ReadNovelFullParserTest.kt
git diff --cached --stat   # VERIFY: only the 10 files, no ParserRegistry.kt
git commit -m "fix: strip novel-name prefix from chapter titles

Hybrid: parser source fix for future imports + display-side
cleanChapterTitleForDisplay for already-imported chapters (no
migration, no data mutation).

TitleExtractor: SEPARATORS extended with ': ' (strict improvement for
extractChapterTitle too). New cleanChapterTitleForDisplay(title,
novelTitle) — strips a leading novelTitle (case-insensitive) if
immediately followed by a separator; separator-after-prefix guard
prevents false positives (e.g. 'The Beginning' doesn't strip from
'The Beginning of the End - Chapter 5').

FreeWebNovel/ReadNovelFull parsers: parseChapterTitle now delegates
to TitleExtractor.extractChapterTitle(tag, novelTitle) and the h2/h1
fallbacks strip a leading novelTitle prefix via
cleanChapterTitleForDisplay. Future imports are clean at the source.

Display sites: ReaderScreen toolbar (line 288) and chapter list
sheet (line 247); ChaptersTab library list (line 172) — new
novelTitle: String? param, passed by LibraryScreen.kt:301;
FavoritesScreen chapter-title line — cleaned at the BookmarkItem
call site."
```

The `git diff --cached --stat` MUST show the 10 listed files and MUST NOT show `ParserRegistry.kt` or any of the 403 files.

---

## Task 5: Version bump v2.4.3 (Commit 5)

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode 16` → `17`; `versionName "2.4.2"` → `"2.4.3"`)
- Modify: `README.md` (update v2.4.2 references to v2.4.3 and versionCode 16 to 17)
- Modify: `README_PT.md` (same as above)

**Pre-step (CRITICAL — build-file isolation):**

`app/build.gradle.kts` and `gradle/libs.versions.toml` are dirty with uncommitted 403 work. The version-bump commit must contain ONLY the versionCode/versionName change. The 403 changes to those files must be committed (or stashed) BEFORE the version bump is staged, so the version-bump commit touches a clean build file.

- [ ] **Step 1: Determine the 403 workstream's state for these files**

```bash
cd /home/fabricio/Repos/Opencode/android-book
git status --short -- app/build.gradle.kts gradle/libs.versions.toml
```

Expected: both files appear as `M ` (modified, uncommitted). The 403 workstream is in progress and has not committed these changes.

- [ ] **Step 2: Coordinate with the 403 workstream**

Three options, in order of preference:

**Option A (preferred):** The 403 workstream commits its changes to `main` first (its full set of commits, including `app/build.gradle.kts` and `gradle/libs.versions.toml`). Then this version-bump commit cleanly follows on `main` with only the versionCode/versionName change. Coordinate via the 403 workstream's handoff (e.g., `/tmp/opencode/handoff-20260626-freewebnovel-403.md`).

**Option B:** Stash ONLY the 403 changes to those two files (not the other 403 files), commit the version bump, unstash:

```bash
git stash push -- app/build.gradle.kts gradle/libs.versions.toml
# Now those two files match HEAD. Make the version-bump edits (see Step 4).
git add app/build.gradle.kts README.md README_PT.md
git commit -m "chore: bump version to v2.4.3 (versionCode 17)"
git stash pop   # Restores the 403 changes on top.
```

After `git stash pop`, the working tree returns to its prior dirty state (the 403 build changes are back as uncommitted modifications on top of the now-bumped versionCode). This is safe because `git stash pop` applies the stashed 403 changes AFTER the version-bump commit; the 403 diffs in `build.gradle.kts` and `libs.versions.toml` remain uncommitted, owned by the 403 workstream.

**Option C:** If neither A nor B is possible (e.g., the 403 workstream is mid-edit and stashing would lose work), DEFER this entire task. Leave v2.4.3 unversioned for now; the 4 feature/fix commits from Tasks 1–4 are usable on their own, and the version bump can be done in a final commit once the 403 workstream has committed.

**Choose A if possible. Otherwise B. Defer to C only as a last resort.** Do NOT mix the version-bump change with 403 build changes in a single commit.

- [ ] **Step 3: (Skip if Option C) Verify the build file is clean**

```bash
git diff -- app/build.gradle.kts
```

Expected: empty diff (the file matches HEAD or matches the popped-stash state after a successful `git stash pop` in Option B — but for the commit, the diff against HEAD should show ONLY the version bump, not the 403 changes).

For Option A: the file is clean (matches `origin/main` or the 403 commit tip). Edit it.
For Option B: after the stash + edit + commit + pop sequence, the file is again dirty with 403 changes. That's the expected end state.

- [ ] **Step 4: Edit `app/build.gradle.kts`**

Find the lines:

```kotlin
    versionCode = 16
    versionName = "2.4.2"
```

Change to:

```kotlin
    versionCode = 17
    versionName = "2.4.3"
```

(Exact indentation/whitespace matches the file.)

- [ ] **Step 5: Update the READMEs**

In `README.md` and `README_PT.md`, update all references to `v2.4.2` → `v2.4.3` and `versionCode 16` → `versionCode 17` (or `versionCode 15` → `versionCode 17` if some references are still pre-v2.4.2). Use `grep` to find the references:

```bash
grep -n "v2.4.2\|versionCode" README.md README_PT.md
```

Update each occurrence. The READMEs are end-user docs; the change is the version string.

- [ ] **Step 6: Run the full unit test suite + compile**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All tests pass.

- [ ] **Step 7: Commit the version bump (exact-path `git add`)**

```bash
git add app/build.gradle.kts README.md README_PT.md
git diff --cached --stat   # VERIFY: only the 3 files
git commit -m "chore: bump version to v2.4.3 (versionCode 17)"
```

The `git diff --cached --stat` MUST show only the 3 files and only the versionCode/versionName/README version-string changes.

---

## Final verification

- [ ] **Step 1: Confirm the 5-commit series on `main`**

```bash
git log --oneline -7
```

Expected (newest first, with the 403 work's commits possibly interleaved before this series if Option A was used):

```
<this commit>  chore: bump version to v2.4.3 (versionCode 17)
<prev commit> fix: strip novel-name prefix from chapter titles
<prev commit> feat: favorites search + created-date + RelativeTime i18n fix
<prev commit> feat: chapter-list sheet search
<prev commit> feat: reader toolbar haptics + keep-screen-on setting
<prev>       docs: reader UX polish design (v2.4.3 spec)          <- 077db03
<prev>       chore: remove dead helper code + exercise ComposeUiTestBase in smoke test  <- e97d735
```

(If the 403 workstream commits in between, the 5 v2.4.3 commits form a clean block on `main`; the 403 commits may sit before or after this block, not interleaved within it.)

- [ ] **Step 2: Final full test + build**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests green (163 baseline + new tests from all 4 feature tasks).

- [ ] **Step 3: Manual QA on emulator (the only way to verify the Compose-UI / locale / haptics / keep-screen-on behavior)**

Build and install on a connected device:

```bash
./gradlew :app:installDebug
```

Checklist (each item must be exercised on the emulator):

1. **Toolbar haptics:** open any chapter, tap each of the 6 toolbar buttons (search, prev, bookmark, settings, chapter list, next) and confirm a haptic vibration on each.
2. **Keep-screen-on:** open a chapter, confirm the screen does not dim/lock. Toggle the new Switch in the settings sheet to OFF, confirm the system timeout now applies. Toggle back to ON, confirm screen stays on. Back to library, confirm normal timeout.
3. **Chapter-list search:** open the chapter list sheet, type a query, confirm the list narrows. Clear the query, confirm the full list returns. Try a non-matching query, confirm the "no chapters found" line. Tap a filtered chapter, confirm the reader navigates to it.
4. **Favorites search:** add a bookmark to a chapter, open Favorites, type a query, confirm the list narrows by title/chapter-title/note. Clear the query, confirm all return.
5. **Favorites created-date:** confirm the bookmark in Favorites shows a relative time below the note (or chapter title if no note) — e.g. "now" / "5 min. ago" / "há 5 min" per device locale. For an old bookmark, confirm the absolute-date fallback.
6. **Reading badge locale (en device):** set the device locale to English (US), open the library, confirm the "Reading" badge on a novel card shows "5 min. ago" (en) instead of "há 5 min" (pt-BR). Set locale back to pt-BR, confirm "há 5 min".
7. **Chapter title cleaning:** open a novel whose chapter titles historically included the novel name (or import a chapter whose source title tag uses en-dash / em-dash / pipe + novel name). Confirm the reader toolbar title, the chapter list sheet, the library Chapters tab, and the Favorites chapter-title line all show the chapter part WITHOUT the novel-name prefix.
8. **Perdi meu lugar regression check:** open a chapter, scroll halfway, kill the app from recents, reopen. Confirm scroll position is restored. Rotate the device. Confirm the WebView state survives (per the v2.4.1 spec). This validates that no toolbar / settings change from Tasks 1–4 regressed the v2.4.1 cluster.

- [ ] **Step 4: Report**

Report the result:
- 5-commit series on `main` (or 4 if Task 5 was deferred via Option C).
- All tests green.
- Manual QA checklist passed.
- The 403 workstream's working tree is intact (no accidental staging of its files).

---

## Self-review notes (this plan, checked against the spec)

**Spec coverage:**
- Section 1 (toolbar polish) → Task 1. ✓
- Section 2 (chapter-list search) → Task 2. ✓
- Section 3 (favorites + RelativeTime i18n) → Task 3. ✓
- Section 4 (cross-cutting: strings, commit plan, testing, out-of-scope, verification, risks) → reflected in the Global Constraints, per-task `git add` scopes, manual QA, and Final verification. ✓
- Section 5 (title cleaning) → Task 4. ✓
- Version bump → Task 5. ✓

**Placeholders:** none. All code shown is complete; all commands and file paths are exact.

**Type/signature consistency:** `ReaderConfig.keepScreenOn: Boolean`, `ReaderPreferences.updateKeepScreenOn(Boolean)`, `ReaderViewModel.updateKeepScreenOn(Boolean)`, `SettingsSheet(... onKeepScreenOnChange: (Boolean) -> Unit)`, `filterChaptersByQuery(List<ChapterEntity>, String): List<ChapterEntity>`, `FavoritesViewModel.setSearchQuery(String)`, `FavoritesViewModel.searchQuery: StateFlow<String>`, `FavoritesViewModel.displayItems: StateFlow<List<BookmarkDisplayItem>>`, `TitleExtractor.cleanChapterTitleForDisplay(String, String?): String`, `ChaptersTab(... novelTitle: String? = null, ...)`. All names consistent across tasks.

**Risks/mitigations explicitly handled:**
- Haptics brittleness → manual QA + action smoke (not Compose spy test).
- Parser regression → existing parser tests run as regression guard in Step 9.
- 403 worktree contamination → exact-path `git add` per commit + `git diff --cached --stat` verification.
- Build-file conflict at Task 5 → Option A/B/C procedure with stash isolation.
- RelativeTime test fragility (DateUtils exact-string coupling) → property assertions + `Locale.setDefault` per test, with fallback to weaker assertions documented in Step 4 of Task 3.
- Keep-screen-on `View` flag → manual QA on emulator (auto-rotate, recents-kill, library-return).
- `View.keepScreenOn` semantics (auto-clears on detach) → confirmed in spec; no manual cleanup needed in the plan.

**Scope:** single plan, 5 commits (or 4 + deferred version bump). Matches the spec's single-plan scope.
