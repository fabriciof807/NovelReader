# Reader: Configurable Swipe Direction

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make swipe direction in the reader configurable (vertical/horizontal/both/off) via Settings, defaulting to vertical (Webnovel-style).

**Architecture:** Add `swipeDirection` field to `ReaderConfig` (DataStore), rewrite the JS swipe handler in `ReaderHtmlBuilder.kt` to read a global `_swipeDir` variable set by `applyConfig`, add UI chips in `ReaderSettingsSheet.kt`, wire through `ReaderViewModel`.

**Tech Stack:** Kotlin, Compose, DataStore, WebView + JavaScript interop

## Global Constraints

- Follow codebase conventions: no comments in production code, Kotlin official style, bilingual PT+EN strings
- `ReaderConfig` default values must be explicit and safe (fallback to `"vertical"` for invalid data)
- JS swipe handler must NOT fire on slow scrolls (dt > 500ms early return)
- Existing tap handler (|dx| < 15 && |dy| < 15 && dt < 300) must remain unaffected
- 297 tests currently pass; all must still pass after changes
- Minimum SDK 26, target SDK 34

---

### Task 1: ReaderPreferences — swipeDirection field

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/local/preferences/ReaderPreferences.kt`
- Modify: `app/src/test/java/com/novelreader/data/local/preferences/ReaderPreferencesTest.kt`

**Interfaces:**
- Consumes: existing `DataStore` pattern, `ReaderConfig` data class
- Produces: `config.swipeDirection: String` on `ReaderConfig`, `updateSwipeDirection(direction: String)` on `ReaderPreferences`, new DataStore key `"swipe_direction"`

- [ ] **Step 1: Write failing tests**

In `ReaderPreferencesTest.kt`:

```kotlin
@Test
fun `swipeDirection defaults to vertical`() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val prefs = ReaderPreferences(context)
    assertThat(prefs.config.first().swipeDirection).isEqualTo("vertical")
}

@Test
fun `updateSwipeDirection persists all four valid values`() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val prefs = ReaderPreferences(context)
    for (dir in listOf("vertical", "horizontal", "both", "none")) {
        prefs.updateSwipeDirection(dir)
        assertThat(prefs.config.first().swipeDirection).isEqualTo(dir)
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.local.preferences.ReaderPreferencesTest" -i
```

Expected: COMPILATION ERROR — `swipeDirection` not defined on `ReaderConfig`.

- [ ] **Step 3: Add `swipeDirection` field to `ReaderConfig`**

```kotlin
data class ReaderConfig(
    val fontSize: Int = 20,
    val fontFamily: String = "serif",
    val lineHeight: Float = 1.8f,
    val theme: String = "light",
    val autoScrollSpeed: Float = 0f,
    val keepScreenOn: Boolean = true,
    val swipeDirection: String = "vertical"
)
```

- [ ] **Step 4: Add DataStore key + update method + config mapping**

Add to `ReaderPreferences`:

```kotlin
private object Keys {
    val FONT_SIZE = intPreferencesKey("font_size")
    val FONT_FAMILY = stringPreferencesKey("font_family")
    val LINE_HEIGHT = stringPreferencesKey("line_height")
    val THEME = stringPreferencesKey("theme")
    val AUTO_SCROLL_SPEED = stringPreferencesKey("auto_scroll_speed")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val SWIPE_DIRECTION = stringPreferencesKey("swipe_direction")
}
```

In the `config` flow, add to `ReaderConfig` constructor call:
```kotlin
swipeDirection = prefs[Keys.SWIPE_DIRECTION]?.takeIf { it in setOf("vertical", "horizontal", "both", "none") } ?: "vertical",
```

Add new update method:
```kotlin
suspend fun updateSwipeDirection(direction: String) {
    context.dataStore.edit { it[Keys.SWIPE_DIRECTION] = direction }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.local.preferences.ReaderPreferencesTest" -i
```

Expected: PASS (2 existing + 2 new).

- [ ] **Step 6: Full build + commit**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
git add app/src/main/java/com/novelreader/data/local/preferences/ReaderPreferences.kt
git add app/src/test/java/com/novelreader/data/local/preferences/ReaderPreferencesTest.kt
git commit -m "feat(reader): add swipeDirection preference (DataStore + ReaderConfig)"
```

---

### Task 2: Rewrite JS swipe handler in ReaderHtmlBuilder

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderHtmlBuilder.kt`
- Modify: `app/src/test/java/com/novelreader/ui/reader/ReaderHtmlBuilderTest.kt`

**Interfaces:**
- Consumes: `config.swipeDirection: String` from Task 1
- Produces: JS that reads `_swipeDir` global variable and dispatches `Android.onSwipe("prev"|"next")` accordingly; `applyConfig(cfg)` sets `_swipeDir`

- [ ] **Step 1: Write failing tests**

Add to `ReaderHtmlBuilderTest.kt`:

```kotlin
@Test
fun `buildReaderHtml with swipeDirection vertical contains vertical-only swipe JS`() {
    val html = buildReaderHtml("<p>x</p>", ReaderConfig(swipeDirection = "vertical"))
    assertThat(html).contains("dy < 0 ? 'next' : 'prev'")
    // Should NOT contain horizontal detection for both branches
    assertThat(html).doesNotContain("dx < 0 ? 'next' : 'prev'")
}

@Test
fun `buildReaderHtml with swipeDirection horizontal contains horizontal-only swipe JS`() {
    val html = buildReaderHtml("<p>x</p>", ReaderConfig(swipeDirection = "horizontal"))
    assertThat(html).contains("dx < 0 ? 'next' : 'prev'")
    assertThat(html).doesNotContain("dy < 0 ? 'next' : 'prev'")
}

@Test
fun `buildReaderHtml with swipeDirection both contains both vertical and horizontal JS`() {
    val html = buildReaderHtml("<p>x</p>", ReaderConfig(swipeDirection = "both"))
    assertThat(html).contains("dy < 0 ? 'next' : 'prev'")
    assertThat(html).contains("dx < 0 ? 'next' : 'prev'")
}

@Test
fun `buildReaderHtml with swipeDirection none never dispatches onSwipe`() {
    val html = buildReaderHtml("<p>x</p>", ReaderConfig(swipeDirection = "none"))
    assertThat(html).contains("_swipeDir")
    // When _swipeDir === 'none', none of the branch conditions match
    assertThat(html).doesNotContain("Android.onSwipe")
}

@Test
fun `applyConfigJs passes swipeDirection to applyConfig`() {
    val js = applyConfigJs(ReaderConfig(swipeDirection = "horizontal"))
    assertThat(js).contains("\"swipeDirection\":\"horizontal\"")
}
```

- [ ] **Step 2: Run to check test fails (or compilation fails)**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderHtmlBuilderTest" -i
```

Expected: PASS for existing tests, FAIL for new tests (the JS doesn't use `_swipeDir` yet).

- [ ] **Step 3: Rewrite the swipe JS block in `ReaderHtmlBuilder.kt`**

Replace the existing `(function() { ... })();` block (lines 178-193) with:

```javascript
var _swipeDir = '${config.swipeDirection}';
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
            return;
        }
        if (dt > 500) return;
        var dir = null;
        if ((_swipeDir === 'vertical' || _swipeDir === 'both') &&
            Math.abs(dy) > 60 && Math.abs(dy) > Math.abs(dx) * 1.5) {
            dir = dy < 0 ? 'next' : 'prev';
        }
        if (dir === null &&
            (_swipeDir === 'horizontal' || _swipeDir === 'both') &&
            Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) {
            dir = dx < 0 ? 'next' : 'prev';
        }
        if (dir !== null) {
            try { Android.onSwipe(dir); } catch(e) {}
        }
    });
})();
```

- [ ] **Step 4: Add `_swipeDir` setter to `applyConfig` function**

Inside the JS string, find `function applyConfig(cfg) {` and add one line at the top:

```javascript
function applyConfig(cfg) {
    _swipeDir = cfg.swipeDirection;
    var root = document.documentElement.style;
    ...
```

- [ ] **Step 5: Add `swipeDirection` to `applyConfigJs()` in Kotlin**

Update `applyConfigJs`:

```kotlin
fun applyConfigJs(config: ReaderConfig): String {
    val map = themeVars(config) + mapOf(
        "fontFamily" to config.fontFamily,
        "fontSize" to config.fontSize,
        "lineHeight" to config.lineHeight,
        "autoScrollSpeed" to config.autoScrollSpeed,
        "swipeDirection" to config.swipeDirection
    )
    return buildJs(code = "applyConfig(args.args);", params = mapOf("args" to payload))
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderHtmlBuilderTest" -i
```

Expected: PASS (all 5 new tests + 8 existing = 13 total).

- [ ] **Step 7: Full build + commit**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
git add app/src/main/java/com/novelreader/ui/reader/ReaderHtmlBuilder.kt
git add app/src/test/java/com/novelreader/ui/reader/ReaderHtmlBuilderTest.kt
git commit -m "feat(reader): rewrite swipe JS to support configurable direction (vertical/horizontal/both/none)"
```

---

### Task 3: ViewModel + Settings UI + strings

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderViewModel.kt`
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/test/java/com/novelreader/ui/reader/ReaderViewModelTest.kt`

**Interfaces:**
- Consumes: `ReaderPreferences.updateSwipeDirection(direction)` from Task 1, `config.swipeDirection: String` from Task 1
- Produces: Settings UI section, ViewModel method, screen wiring

- [ ] **Step 1: Write failing test for ViewModel**

Add to `ReaderViewModelTest.kt`:

```kotlin
@Test
fun `updateSwipeDirection forwards value to readerPreferences`() = runTest {
    coEvery { chapterDao.getChapterById(10) } returns ChapterEntity(
        id = 10, novelId = 1L, title = "Ch1", fileName = "ch1.html",
        orderIndex = 1, content = "<p>x</p>"
    )
    coEvery { chapterDao.getChaptersByNovelSync(1L) } returns emptyList()
    viewModel = createViewModel()
    viewModel.updateSwipeDirection("horizontal")
    coVerify { readerPrefs.updateSwipeDirection("horizontal") }
}
```

- [ ] **Step 2: Run test — must fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderViewModelTest" -i
```

Expected: COMPILATION ERROR — `updateSwipeDirection` not defined on ViewModel.

- [ ] **Step 3: Add `updateSwipeDirection` to `ReaderViewModel.kt`**

```kotlin
fun updateSwipeDirection(direction: String) {
    viewModelScope.launch {
        readerPreferences.updateSwipeDirection(direction)
    }
}
```

- [ ] **Step 4: Add i18n strings**

In `app/src/main/res/values/strings.xml` and `app/src/main/res/values-en/strings.xml`, add after the `reader_auto_scroll_hint` line:

```xml
    <string name="reader_swipe_direction">Direção do swipe</string>
    <string name="reader_swipe_vertical">Vertical</string>
    <string name="reader_swipe_horizontal">Horizontal</string>
    <string name="reader_swipe_both">Ambos</string>
    <string name="reader_swipe_none">Desligado</string>
```

For EN:
```xml
    <string name="reader_swipe_direction">Swipe direction</string>
    <string name="reader_swipe_vertical">Vertical</string>
    <string name="reader_swipe_horizontal">Horizontal</string>
    <string name="reader_swipe_both">Both</string>
    <string name="reader_swipe_none">Off</string>
```

Import `R` at top of `ReaderSettingsSheet.kt` (already imported).

- [ ] **Step 5: Add swipe direction section to `ReaderSettingsSheet.kt`**

Add new parameter to `SettingsSheet` signature:
```kotlin
fun SettingsSheet(
    config: ReaderConfig,
    onThemeChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onAutoScrollSpeedChange: (Float) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onSwipeDirectionChange: (String) -> Unit = {},
    onDismiss: () -> Unit
)
```

Add a new section before the auto-scroll divider (before the line `Spacer(modifier = Modifier.height(16.dp))` that precedes `HorizontalDivider()` at the auto-scroll section, around line 165):

```kotlin
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.reader_swipe_direction),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SwipeOption(
                    icon = Icons.AutoMirrored.Filled.ArrowUpward,
                    label = stringResource(R.string.reader_swipe_vertical),
                    selected = config.swipeDirection == "vertical",
                    onClick = { onSwipeDirectionChange("vertical") },
                    modifier = Modifier.weight(1f)
                )
                SwipeOption(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = stringResource(R.string.reader_swipe_horizontal),
                    selected = config.swipeDirection == "horizontal",
                    onClick = { onSwipeDirectionChange("horizontal") },
                    modifier = Modifier.weight(1f)
                )
                SwipeOption(
                    icon = Icons.Default.SwapHoriz,
                    label = stringResource(R.string.reader_swipe_both),
                    selected = config.swipeDirection == "both",
                    onClick = { onSwipeDirectionChange("both") },
                    modifier = Modifier.weight(1f)
                )
                SwipeOption(
                    icon = Icons.Default.Close,
                    label = stringResource(R.string.reader_swipe_none),
                    selected = config.swipeDirection == "none",
                    onClick = { onSwipeDirectionChange("none") },
                    modifier = Modifier.weight(1f)
                )
            }
```

Add the `SwipeOption` composable (reuses the same visual as `ThemeOption` but simpler — it's a chip):

```kotlin
@Composable
private fun SwipeOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

Add imports to `ReaderSettingsSheet.kt`:
```kotlin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
```

Note: `material-icons-extended` is included in the project, so `SwapHoriz` is available.

- [ ] **Step 6: Wire `onSwipeDirectionChange` in `ReaderScreen.kt`**

In `ReaderScreen.kt`, find the `SettingsSheet(...)` call (around line 195):

```kotlin
if (state.showSettings) {
    SettingsSheet(
        config = state.config,
        onThemeChange = { viewModel.updateTheme(it) },
        onFontSizeChange = { viewModel.updateFontSize(it) },
        onLineHeightChange = { viewModel.updateLineHeight(it) },
        onAutoScrollSpeedChange = { viewModel.updateAutoScrollSpeed(it) },
        onKeepScreenOnChange = { viewModel.updateKeepScreenOn(it) },
        onSwipeDirectionChange = { viewModel.updateSwipeDirection(it) },
        onDismiss = { viewModel.hideSettings() }
    )
}
```

- [ ] **Step 7: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.reader.ReaderViewModelTest" -i
```

Expected: PASS (existing tests + 1 new).

- [ ] **Step 8: Full build + commit**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
git add app/src/main/java/com/novelreader/ui/reader/ReaderViewModel.kt
git add app/src/main/java/com/novelreader/ui/reader/ReaderSettingsSheet.kt
git add app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt
git add app/src/main/res/values/strings.xml
git add app/src/main/res/values-en/strings.xml
git add app/src/test/java/com/novelreader/ui/reader/ReaderViewModelTest.kt
git commit -m "feat(reader): add swipe direction section to Settings sheet + wiring"
```

---

### Task 4: Final build + verification

- [ ] **Step 1: Full compile + test suite**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests passing (~307+ total).

- [ ] **Step 2: Manual verification**

```bash
./gradlew :app:installDebug
```

1. Open a novel → reader → swipe up fast → next chapter loads.
2. Swipe down fast → previous chapter loads.
3. Settings gear → "Direção do swipe" section visible with 4 chips.
4. Tap "Horizontal" → swipe left = next, swipe right = prev.
5. Tap "Off" → no swipe navigates (tap still toggles controls).
6. Tap "Ambos" → both directions work.
7. Slow drag (scroll) → no chapter change.

- [ ] **Step 3: Git log + done**

```bash
git log --oneline -6
```
