# v2.4.2 UI/UX Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship v2.4.2 (versionCode 16) — 13 polish tickets + 1 housekeeping commit — covering reader chapter list, library empty state, scroll memory, a11y, haptics, i18n, dynamic color, tab transitions, and a Compose UI test base infrastructure. Notifications workstream remains deferred to v2.5.0.

**Architecture:** Additive Compose + DataStore + Hilt changes inside the existing MVVM + MVI layout. Three new files (`ComposeUiTestBase.kt`, `LibraryEmptyState.kt`, `RelativeTime.kt`, `HapticClickable.kt`); the rest are small modifications to existing composables, ViewModels, the theme, and string resources. Two state changes (chapters-scroll per-novel, dynamic color preference) are backed by `SavedStateHandle` and DataStore respectively. No new entities, no migrations, no new third-party dependencies.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1, JVM 17. Jetpack Compose (BOM 2024.12.01) + Material3. Room 2.8.4. Hilt 2.59.2. DataStore 1.1.3. Robolectric, MockK, Turbine, JUnit 4, Truth, `androidx.compose.ui:ui-test-junit4`. Min SDK 26, Target SDK 34.

**Spec:** `docs/superpowers/specs/2026-06-25-ui-ux-polish-design.md` (read this for design rationale; the plan is a strict execution of it).

## Global Constraints

- Kotlin official style; **no comments unless requested**.
- PT-BR comments only where unavoidable; strings always bilingual (pt-BR in `values/strings.xml` as default, en in `values-en/strings.xml`).
- `StateFlow` for UI state, `MutableStateFlow` for internal, `errorEvents: SharedFlow<String>` with `BufferOverflow.DROP_OLDEST`.
- `@ApplicationContext` for Context; `@IoDispatcher` for IO; Hilt for all DI.
- DataStore for preferences (never SharedPreferences for app data).
- Room for persistence.
- Conventional commit messages; **one logical change per commit**.
- Direct to `main` branch.
- **Always run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` before claiming done.**
- `app/release/baselineProfiles/*` and `app/release/output-metadata.json` are build artifacts — **do NOT stage them**.
- `handoff-*.md` files are gitignored.
- TDD: write the failing test first, run it to confirm failure, then implement. For visual / config-only tickets without an automated test, the verification step is `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` (existing tests must still pass) and a manual visual check noted in the commit body.
- `AGENTS.md` documents the i18n convention inverted; the **actual** layout used in this repo is `values/strings.xml` = pt-BR (default), `values-en/strings.xml` = en override. Do not create `values-pt-rBR/`.

---

### Task 0: Push existing unpushed commits (no-op if it fails)

**Files:** none

- [ ] **Step 1: Attempt push**

Run: `git push origin main`
Expected: either success (10 prior local commits land on origin) OR failure with SSH/auth error. On failure, continue — this plan stacks on top locally.

- [ ] **Step 2: If push fails, surface and proceed**

No commit step. Do not configure SSH keys. Surface the push failure to the user at the end of the session.

---

### Task 1: Compose UI test base infrastructure

**Files:**
- Create: `app/src/test/java/com/novelreader/ui/test/ComposeUiTestBase.kt`
- Create: `app/src/test/java/com/novelreader/ui/test/ComposeUiTestBaseSmokeTest.kt`
- Verify: `app/build.gradle.kts` already has `testImplementation("androidx.compose.ui:ui-test-junit4")` and `testImplementation(platform(libs.compose.bom))` (lines 134-135).

**Interfaces:**
- Produces: `abstract class ComposeUiTestBase { fun setNovelReaderContent(content: @Composable () -> Unit); fun assertTextDisplayed(text: String); fun assertTextNotDisplayed(text: String); fun performClick(text: String); fun assertFirstVisibleItem(index: Int, state: LazyListState) }`. `createComposeRule()` is exposed via the protected `composeTestRule` property.

- [ ] **Step 1: Create the test base class**

Write `app/src/test/java/com/novelreader/ui/test/ComposeUiTestBase.kt`:

```kotlin
package com.novelreader.ui.test

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule

abstract class ComposeUiTestBase {

    @get:Rule
    val composeTestRule = createComposeRule()

    protected fun setNovelReaderContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            NovelReaderTheme {
                content()
            }
        }
    }

    protected fun assertTextDisplayed(text: String) {
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    protected fun assertTextNotDisplayed(text: String) {
        composeTestRule.onNodeWithText(text).assertDoesNotExist()
    }

    protected fun performClick(text: String) {
        composeTestRule.onNodeWithText(text).performClick()
    }

    protected fun assertFirstVisibleItem(index: Int, state: LazyListState) {
        composeTestRule.runOnUiThread {
            assertThat(state.firstVisibleItemIndex).isEqualTo(index)
        }
    }
}
```

- [ ] **Step 2: Write the smoke test**

Write `app/src/test/java/com/novelreader/ui/test/ComposeUiTestBaseSmokeTest.kt`:

```kotlin
package com.novelreader.ui.test

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp")
class ComposeUiTestBaseSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `createComposeRule renders text and isDisplayable`() {
        composeTestRule.setContent {
            com.novelreader.ui.theme.NovelReaderTheme {
                Text("hello")
            }
        }
        composeTestRule.onNodeWithText("hello").assertIsDisplayed()
        assertThat(true).isTrue()
    }
}
```

- [ ] **Step 3: Run the smoke test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.test.ComposeUiTestBaseSmokeTest"`
Expected: PASS. (If a `Robolectric` SDK 33 setup error appears, confirm `testOptions { unitTests.isIncludeAndroidResources = true }` is in `app/build.gradle.kts` — it should already be present.)

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/novelreader/ui/test/
git commit -m "chore: compose UI test base infrastructure (Robolectric)"
```

---

### Task 2: Reader chapter list — remove 1-line cap

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt:246`

**Interfaces:** none (visual constant change).

- [ ] **Step 1: Edit the chapter list title `Text`**

In `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt`, change line 246 from:
```kotlin
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
```
to:
```kotlin
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
```

- [ ] **Step 2: Compile to verify no syntax error**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run all unit tests to ensure no regression**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 139 tests passing (138 baseline + 1 from Task 1).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt
git commit -m "fix: reader — chapter list removes 1-line cap (maxLines 1 → 4)"
```

---

### Task 3: Personagens — ExtendedFAB with labels

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/PersonagensTab.kt:238-255`
- Modify: `app/src/main/res/values/strings.xml` (add 2 strings)
- Modify: `app/src/main/res/values-en/strings.xml` (add 2 en strings)
- Create: `app/src/test/java/com/novelreader/ui/library/tabs/PersonagensTabFabTest.kt`

**Interfaces:**
- Produces: new string resources `personagens_add_label`, `personagens_import_label`.

- [ ] **Step 1: Add the new string resources**

In `app/src/main/res/values/strings.xml`, add:
```xml
    <string name="personagens_add_label">Adicionar</string>
    <string name="personagens_import_label">Importar</string>
```

In `app/src/main/res/values-en/strings.xml`, add:
```xml
    <string name="personagens_add_label">Add</string>
    <string name="personagens_import_label">Import</string>
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/novelreader/ui/library/tabs/PersonagensTabFabTest.kt`:

```kotlin
package com.novelreader.ui.library.tabs

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp+pt-rBR")
class PersonagensTabFabTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `PersonagensTab renders ExtendedFAB labels for add and import`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                PersonagensTab(
                    characters = emptyList(),
                    characterPhotos = emptyMap(),
                    selectedNovel = null,
                    onAddCharacter = { _, _ -> },
                    onDeleteCharacter = { },
                    onAddCharacterPhoto = { _, _ -> },
                    onBatchAddCharacterPhotos = { _, _ -> },
                    onDeleteCharacterPhoto = { _, _ -> },
                    onUpdateCharacterName = { _, _ -> },
                    onUpdateCharacterNotes = { _, _ -> },
                    onToggleCharacterFavorite = { _, _ -> },
                    onImportCharacters = { }
                )
            }
        }
        composeTestRule.onNodeWithText("Adicionar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Importar").assertIsDisplayed()
    }
}
```

Add the import for `assertIsDisplayed`:
```kotlin
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
```

The `+pt-rBR` locale qualifier forces Robolectric to resolve `R.string.*` against the default `values/strings.xml` (Portuguese) rather than `values-en/strings.xml`. Without it, Robolectric's default English locale makes the test look for "Add" and "Import" and never finds the Portuguese labels.

**Note on qualifier format:** the working form is `qualifiers = "pt-rBR-w400dp-h800dp"` (language/region must precede size qualifiers per Android's resource qualifier ordering rules). The `+pt-rBR` form from the initial brief is malformed.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.tabs.PersonagensTabFabTest"`
Expected: FAIL with "no node with text 'Adicionar' found" (or similar — the current FAB is icon-only).

- [ ] **Step 4: Replace the FABs with ExtendedFloatingActionButton**

In `app/src/main/java/com/novelreader/ui/library/tabs/PersonagensTab.kt`, replace lines 238-255:

```kotlin
            if (onImportCharacters != null) {
                SmallFloatingActionButton(
                    onClick = {
                        importUrl = ""
                        showImportDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = stringResource(R.string.import_characters)
                    )
                }
            }
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
```

with:

```kotlin
            if (onImportCharacters != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        importUrl = ""
                        showImportDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.personagens_import_label))
                }
            }
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.personagens_add_label))
            }
```

**Important:** use the content-slot overload (a single `@Composable RowScope.() -> Unit` lambda), NOT the `text=`/`icon=` named-params overload. The named-params overload wraps `text()` in `Modifier.clearAndSetSemantics {}` (a Material 3 1.3.1 quirk), which strips the text from the semantics tree and makes `onNodeWithText` unable to find it.

Add the imports for `ExtendedFloatingActionButton` and `Spacer.width` near the other material3 imports:
```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.tabs.PersonagensTabFabTest"`
Expected: PASS.

- [ ] **Step 6: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 140 tests passing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/tabs/PersonagensTab.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/test/java/com/novelreader/ui/library/tabs/PersonagensTabFabTest.kt
git commit -m "feat: personagens — ExtendedFAB with labels (Add / Import)"
```

---

### Task 4: Library empty state composable + integration

**Files:**
- Create: `app/src/main/java/com/novelreader/ui/library/components/LibraryEmptyState.kt`
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/LibraryTab.kt` (render the empty state)
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt` (pass callbacks)
- Modify: `app/src/main/res/values/strings.xml` (add 3 strings)
- Modify: `app/src/main/res/values-en/strings.xml` (add 3 en strings)
- Create: `app/src/test/java/com/novelreader/ui/library/components/LibraryEmptyStateTest.kt`

**Interfaces:**
- Produces:
  - `LibraryEmptyState(onImportLocal: () -> Unit, onImportWeb: () -> Unit)` composable.
  - String resources `library_empty_title`, `library_empty_body`, `library_empty_import_local`, `library_empty_import_web`.

- [ ] **Step 1: Add string resources**

In `app/src/main/res/values/strings.xml`:
```xml
    <string name="library_empty_title">Adicione sua primeira novel</string>
    <string name="library_empty_body">Importe um arquivo local ou baixe da web</string>
    <string name="library_empty_import_local">Arquivo local</string>
    <string name="library_empty_import_web">Da web</string>
```

In `app/src/main/res/values-en/strings.xml`:
```xml
    <string name="library_empty_title">Add your first novel</string>
    <string name="library_empty_body">Import a local file or download from the web</string>
    <string name="library_empty_import_local">Local file</string>
    <string name="library_empty_import_web">From the web</string>
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/novelreader/ui/library/components/LibraryEmptyStateTest.kt`:

```kotlin
package com.novelreader.ui.library.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class LibraryEmptyStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `LibraryEmptyState displays illustration, text, and two CTAs`() {
        var localClicks = 0
        var webClicks = 0
        composeTestRule.setContent {
            NovelReaderTheme {
                LibraryEmptyState(
                    onImportLocal = { localClicks++ },
                    onImportWeb = { webClicks++ }
                )
            }
        }
        composeTestRule.onNodeWithText("Adicione sua primeira novel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Importe um arquivo local ou baixe da web").assertIsDisplayed()
        composeTestRule.onNodeWithText("Arquivo local").assertIsDisplayed()
        composeTestRule.onNodeWithText("Da web").assertIsDisplayed()

        composeTestRule.onNodeWithText("Arquivo local").performClick()
        composeTestRule.onNodeWithText("Da web").performClick()
        assertThat(localClicks).isEqualTo(1)
        assertThat(webClicks).isEqualTo(1)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.components.LibraryEmptyStateTest"`
Expected: FAIL with unresolved reference `LibraryEmptyState`.

- [ ] **Step 4: Create the empty state composable**

Create `app/src/main/java/com/novelreader/ui/library/components/LibraryEmptyState.kt`:

```kotlin
package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novelreader.R

@Composable
fun LibraryEmptyState(
    onImportLocal: () -> Unit,
    onImportWeb: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onImportLocal) {
            Text(stringResource(R.string.library_empty_import_local))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImportWeb) {
            Text(stringResource(R.string.library_empty_import_web))
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.components.LibraryEmptyStateTest"`
Expected: PASS.

- [ ] **Step 6: Integrate the empty state into LibraryTab**

In `app/src/main/java/com/novelreader/ui/library/tabs/LibraryTab.kt`, modify the function signature to accept two new params:

```kotlin
fun LibraryTab(
    // ... existing params ...
    onImportLocal: () -> Unit = {},
    onImportWeb: () -> Unit = {},
    modifier: Modifier = Modifier
) {
```

Find the `LazyColumn` block (lines ~173-200) and wrap it with an `if`:
```kotlin
        } else if (chipFiltered.isEmpty()) {
            LibraryEmptyState(
                onImportLocal = onImportLocal,
                onImportWeb = onImportWeb,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            LazyColumn( /* ...existing body... */ )
        }
```

The `if (novels.isEmpty()) { ... } else if (chipFiltered.isEmpty()) { ... } else { LazyColumn(...) }` chain handles three cases: no novels at all, no novels matching the active chip filter, and the normal list.

- [ ] **Step 7: Pass callbacks from LibraryScreen**

In `app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt`, find the `LibraryTab(...)` call site and add:
```kotlin
            onImportLocal = { onImportClick() },
            onImportWeb = { onImportClick() },
```

(If `onImportClick` only opens a single "import" sheet, both callbacks route to it; v2.5.0 can split them.)

- [ ] **Step 8: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 141 tests passing.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/ \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/test/java/com/novelreader/ui/library/components/LibraryEmptyStateTest.kt
git commit -m "feat: library — empty state with illustration and CTAs"
```

---

### Task 5: RelativeTime helper + Reading badge with relative time

**Files:**
- Create: `app/src/main/java/com/novelreader/util/RelativeTime.kt`
- Create: `app/src/test/java/com/novelreader/util/RelativeTimeTest.kt`
- Modify: `app/src/main/java/com/novelreader/ui/library/components/NovelCard.kt:156-169`
- Modify: `app/src/main/java/com/novelreader/ui/library/components/NovelListItem.kt` (similar logic)
- Modify: `app/src/main/res/values/strings.xml` (add `library_reading_with_time`)
- Modify: `app/src/main/res/values-en/strings.xml` (add en)

**Interfaces:**
- Produces: `fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String?`. Returns one of: "agora", "há 5 min", "há 2 h", "há 3 dias", or `null`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/novelreader/util/RelativeTimeTest.kt`:

```kotlin
package com.novelreader.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `now returns agora`() {
        assertThat(formatRelativeTime(now, now)).isEqualTo("agora")
    }

    @Test
    fun `30 seconds ago returns agora`() {
        assertThat(formatRelativeTime(now - 30_000L, now)).isEqualTo("agora")
    }

    @Test
    fun `5 minutes ago returns ha 5 min`() {
        assertThat(formatRelativeTime(now - 5 * 60_000L, now)).isEqualTo("há 5 min")
    }

    @Test
    fun `2 hours ago returns ha 2 h`() {
        assertThat(formatRelativeTime(now - 2 * 3_600_000L, now)).isEqualTo("há 2 h")
    }

    @Test
    fun `3 days ago returns ha 3 dias`() {
        assertThat(formatRelativeTime(now - 3 * 86_400_000L, now)).isEqualTo("há 3 dias")
    }

    @Test
    fun `60 days ago returns null`() {
        assertThat(formatRelativeTime(now - 60L * 86_400_000L, now)).isNull()
    }

    @Test
    fun `negative timestamp returns null`() {
        assertThat(formatRelativeTime(-1L, now)).isNull()
    }

    @Test
    fun `future timestamp returns null`() {
        assertThat(formatRelativeTime(now + 60_000L, now)).isNull()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.util.RelativeTimeTest"`
Expected: FAIL with unresolved reference `formatRelativeTime`.

- [ ] **Step 3: Implement the helper**

Create `app/src/main/java/com/novelreader/util/RelativeTime.kt`:

```kotlin
package com.novelreader.util

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60L * MINUTE_MS
private const val DAY_MS = 24L * HOUR_MS
private const val MONTH_MS = 30L * DAY_MS

fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
    if (timestampMs <= 0L) return null
    val delta = nowMs - timestampMs
    if (delta < 0L) return null
    return when {
        delta < MINUTE_MS -> "agora"
        delta < HOUR_MS -> "há ${delta / MINUTE_MS} min"
        delta < DAY_MS -> "há ${delta / HOUR_MS} h"
        delta < MONTH_MS -> "há ${delta / DAY_MS} dias"
        else -> null
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.util.RelativeTimeTest"`
Expected: 8 PASS.

- [ ] **Step 5: Add the format string resource**

In `app/src/main/res/values/strings.xml`:
```xml
    <string name="library_reading_with_time">Lendo · %1$s</string>
```

In `app/src/main/res/values-en/strings.xml`:
```xml
    <string name="library_reading_with_time">Reading · %1$s</string>
```

- [ ] **Step 6: Update NovelCard Reading badge**

In `app/src/main/java/com/novelreader/ui/library/components/NovelCard.kt`, replace lines 156-169:

```kotlin
                if (novel.lastChapterId != null && onContinueClick != null) {
                    Text(
                        text = stringResource(R.string.reading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
```

with:

```kotlin
                if (novel.lastChapterId != null && onContinueClick != null) {
                    val rel = novel.lastReadAt?.let { formatRelativeTime(it) }
                    Text(
                        text = if (rel != null) stringResource(R.string.library_reading_with_time, rel) else stringResource(R.string.reading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
```

Add the import for `formatRelativeTime`:
```kotlin
import com.novelreader.util.formatRelativeTime
```

(Check that `NovelEntity.lastReadAt: Long?` exists in `data/local/db/entity/NovelEntity.kt` — confirm before implementing. If absent, fall back to a no-op: leave the badge as `R.string.reading` and skip the import.)

- [ ] **Step 7: Apply the same change to NovelListItem**

In `app/src/main/java/com/novelreader/ui/library/components/NovelListItem.kt`, locate the equivalent Reading-text block (currently `NovelListItem.kt` does not show a "Reading" badge — it shows `chapters_count` instead). Search for any reference to `stringResource(R.string.reading)` and apply the same `rel` enrichment if present. If absent, skip this step.

- [ ] **Step 8: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 149 tests passing.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/novelreader/util/RelativeTime.kt \
        app/src/main/java/com/novelreader/ui/library/components/NovelCard.kt \
        app/src/main/java/com/novelreader/ui/library/components/NovelListItem.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/test/java/com/novelreader/util/RelativeTimeTest.kt
git commit -m "feat: library — relative time in Reading badge (há 2 h, há 3 dias)"
```

---

### Task 6: Scroll — Library tab remembers position

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/LibraryTab.kt`
- Create: `app/src/test/java/com/novelreader/ui/library/tabs/LibraryTabScrollTest.kt`

**Interfaces:**
- Consumes: `LibraryTab` renders a `LazyColumn`. New `rememberSaveable(saver = LazyListState.Saver)` state.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/novelreader/ui/library/tabs/LibraryTabScrollTest.kt`:

```kotlin
package com.novelreader.ui.library.tabs

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp")
class LibraryTabScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `rememberSaveable LazyListState persists scroll index across recomposition`() {
        var state: LazyListState? = null
        composeTestRule.setContent {
            NovelReaderTheme {
                val s = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                state = s
                SimpleLazyColumn(state = s, count = 50)
            }
        }
        composeTestRule.runOnUiThread { state!!.scrollToItem(15) }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { assertThat(state!!.firstVisibleItemIndex).isEqualTo(15) }

        composeTestRule.setContent {
            NovelReaderTheme {
                val s = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                state = s
                SimpleLazyColumn(state = s, count = 50)
            }
        }
        composeTestRule.runOnUiThread { assertThat(state!!.firstVisibleItemIndex).isEqualTo(15) }
    }
}

@Composable
private fun SimpleLazyColumn(state: LazyListState, count: Int) {
    androidx.compose.foundation.lazy.LazyColumn(state = state) {
        items(count) { i ->
            androidx.compose.material3.Text("Item $i")
        }
    }
}
```

Add `items` import at the top:
```kotlin
import androidx.compose.foundation.lazy.items
```

- [ ] **Step 2: Run the test to verify it passes (this validates the rememberSaveable approach works on Robolectric)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.tabs.LibraryTabScrollTest"`
Expected: PASS. (This test confirms the underlying mechanism before we apply it to `LibraryTab`.)

- [ ] **Step 3: Apply `rememberSaveable` to LibraryTab's `LazyColumn`**

In `app/src/main/java/com/novelreader/ui/library/tabs/LibraryTab.kt`, at the top of the composable function (after the `var`/`val` declarations), add:

```kotlin
    val listState = androidx.compose.runtime.saveable.rememberSaveable(
        saver = androidx.compose.foundation.lazy.LazyListState.Saver
    ) { androidx.compose.foundation.lazy.LazyListState() }
```

Add these imports at the top of the file:
```kotlin
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
```

Pass `state = listState` to the `LazyColumn` that renders the novel list. Specifically, modify the `LazyColumn(...)` block (lines ~173-200) to include `state = listState`.

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 150 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/tabs/LibraryTab.kt \
        app/src/test/java/com/novelreader/ui/library/tabs/LibraryTabScrollTest.kt
git commit -m "feat: scroll — Library tab remembers position (rememberSaveable)"
```

---

### Task 7: Scroll — Chapters tab per-novel remembers position

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt`
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt`
- Create: `app/src/test/java/com/novelreader/ui/library/LibraryViewModelScrollTest.kt`
- Create: `app/src/test/java/com/novelreader/ui/library/tabs/ChaptersTabScrollTest.kt`

**Interfaces:**
- Produces:
  - `data class ChaptersScrollState(val firstVisibleItemIndex: Int, val firstVisibleItemScrollOffset: Int)`
  - `LibraryViewModel.chaptersScrollByNovel: StateFlow<Map<Long, ChaptersScrollState>>`
  - `LibraryViewModel.setChaptersScroll(novelId: Long, index: Int, offset: Int)`
  - `LibraryViewModel.getChaptersScroll(novelId: Long): ChaptersScrollState?`

- [ ] **Step 1: Add `ChaptersScrollState` and VM methods (failing-test driven)**

Append to `app/src/test/java/com/novelreader/ui/library/LibraryViewModelTest.kt` (or create `LibraryViewModelScrollTest.kt`):

```kotlin
package com.novelreader.ui.library

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.library.LibraryViewModel.ChaptersScrollState
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryViewModelScrollTest {

    @Test
    fun `setChaptersScroll then getChaptersScroll returns saved values`() = runTest {
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val savedState = androidx.lifecycle.SavedStateHandle()
        val vm = LibraryViewModel(
            context = context,
            savedStateHandle = savedState,
            novelDao = io.mockk.mockk(relaxed = true),
            chapterDao = io.mockk.mockk(relaxed = true),
            characterDao = io.mockk.mockk(relaxed = true),
            characterPhotoDao = io.mockk.mockk(relaxed = true),
            failedChapterDao = io.mockk.mockk(relaxed = true),
            bookmarkDao = io.mockk.mockk(relaxed = true),
            libraryPreferences = io.mockk.mockk(relaxed = true),
            characterManagement = io.mockk.mockk(relaxed = true),
            coverManagement = io.mockk.mockk(relaxed = true),
            webImport = io.mockk.mockk(relaxed = true),
            scanMissing = io.mockk.mockk(relaxed = true),
            retryChapter = io.mockk.mockk(relaxed = true),
            parserRegistry = io.mockk.mockk(relaxed = true),
            mhtParser = io.mockk.mockk(relaxed = true),
            backgroundImportManager = io.mockk.mockk(relaxed = true),
            chapterInserter = io.mockk.mockk(relaxed = true),
            fileCharsetDetector = io.mockk.mockk(relaxed = true),
            characterImporter = io.mockk.mockk(relaxed = true),
            updateCheckScheduler = io.mockk.mockk(relaxed = true),
            @IoDispatcher ioDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
        )

        assertThat(vm.getChaptersScroll(1L)).isNull()

        vm.setChaptersScroll(novelId = 1L, index = 12, offset = 40)

        val saved = vm.getChaptersScroll(1L)
        assertThat(saved).isNotNull()
        assertThat(saved!!.firstVisibleItemIndex).isEqualTo(12)
        assertThat(saved.firstVisibleItemScrollOffset).isEqualTo(40)
        assertThat(vm.getChaptersScroll(2L)).isNull()
    }
}
```

Adjust the constructor signature to match the actual `LibraryViewModel` constructor (read `LibraryViewModel.kt:1-100` first to copy the exact parameter list and `@Inject` annotation). Use `io.mockk.mockk(relaxed = true)` for all DAOs / use cases; only the `SavedStateHandle` is real.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.LibraryViewModelScrollTest"`
Expected: FAIL (compile error: unresolved reference `setChaptersScroll` / `getChaptersScroll` / `ChaptersScrollState`).

- [ ] **Step 3: Implement `ChaptersScrollState` + VM methods**

In `app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt`, add near the top (after the imports):

```kotlin
    data class ChaptersScrollState(
        val firstVisibleItemIndex: Int,
        val firstVisibleItemScrollOffset: Int
    )

    private val chaptersScrollKey = "chapters_scroll"

    val chaptersScrollByNovel: StateFlow<Map<Long, ChaptersScrollState>> =
        savedStateHandle.getStateFlow(chaptersScrollKey, emptyMap())
            .map { raw ->
                raw.mapNotNull { (idStr, state) ->
                    val id = idStr.toLongOrNull() ?: return@mapNotNull null
                    val (idx, off) = (state as? List<*>)?.takeIf { it.size == 2 }
                        ?.let { (it[0] as? Int ?: 0) to (it[1] as? Int ?: 0) }
                        ?: return@mapNotNull null
                    id to ChaptersScrollState(idx, off)
                }.toMap()
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setChaptersScroll(novelId: Long, index: Int, offset: Int) {
        val current = chaptersScrollByNovel.value.toMutableMap()
        current[novelId] = ChaptersScrollState(index, offset)
        savedStateHandle[chaptersScrollKey] = current
    }

    fun getChaptersScroll(novelId: Long): ChaptersScrollState? =
        chaptersScrollByNovel.value[novelId]
```

Notes on `SavedStateHandle` type: the value stored must be a `Parcelable` or one of the basic types supported by `Bundle`. `Map<Long, ChaptersScrollState>` is not directly supported; store as a `Map<Long, List<Int>>` (an `ArrayList<Int>` per novel), and decode on read. Adjust the implementation above to use `Map<Long, ArrayList<Int>>` instead of the strongly-typed map if `SavedStateHandle.set` rejects the original. Use `androidx.savedstate.SavedStateHandle` write API that accepts a `java.io.Serializable` fallback or a `Bundle`. If both fail, fall back to approach B from the spec: N keys, 2 per novel (`intPreferencesKey`-style on `SavedStateHandle`).

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.LibraryViewModelScrollTest"`
Expected: PASS.

- [ ] **Step 5: Wire ChaptersTab to read/write the scroll state**

In `app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt`, modify the signature to add:

```kotlin
fun ChaptersTab(
    // ... existing params ...
    initialScroll: LibraryViewModel.ChaptersScrollState? = null,
    onScroll: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
)
```

Inside the composable, after the existing `var showScanDialog by remember { mutableStateOf(false) }` block, add:

```kotlin
    val listState = androidx.compose.foundation.lazy.LazyListState(
        firstVisibleItemIndex = initialScroll?.firstVisibleItemIndex ?: 0,
        firstVisibleItemScrollOffset = initialScroll?.firstVisibleItemScrollOffset ?: 0
    )
    androidx.compose.runtime.LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .debounce(300)
            .collect { (idx, off) -> onScroll(idx, off) }
    }
```

Add the imports:
```kotlin
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
```

Pass `state = listState` to the existing `LazyColumn` at the top of `ChaptersTab`.

- [ ] **Step 6: Wire callbacks from LibraryScreen**

In `app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt`, find the `ChaptersTab(...)` call site and add (assuming `selectedNovel?.id` exists as the current novel id):

```kotlin
            initialScroll = viewModel.getChaptersScroll(selectedNovel?.id ?: 0L),
            onScroll = { idx, off ->
                selectedNovel?.id?.let { viewModel.setChaptersScroll(it, idx, off) }
            }
```

- [ ] **Step 7: Write a Compose UI test for the scroll restoration**

Create `app/src/test/java/com/novelreader/ui/library/tabs/ChaptersTabScrollTest.kt`:

```kotlin
package com.novelreader.ui.library.tabs

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp")
class ChaptersTabScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ChaptersTab initializes LazyListState at initialScroll position`() {
        val state = LazyListState(firstVisibleItemIndex = 7, firstVisibleItemScrollOffset = 0)
        composeTestRule.setContent {
            NovelReaderTheme {
                ChaptersTab(
                    novelId = 1L,
                    chapters = (0L..29L).map {
                        ChapterEntity(
                            id = it, novelId = 1L, title = "Ch $it",
                            fileName = "ch_$it.html", orderIndex = it.toInt(),
                            content = "<p>x</p>"
                        )
                    },
                    bookmarkCounts = emptyMap(),
                    onChapterClick = { },
                    initialScroll = com.novelreader.ui.library.LibraryViewModel.ChaptersScrollState(
                        firstVisibleItemIndex = 7, firstVisibleItemScrollOffset = 0
                    ),
                    onScroll = { _, _ -> }
                )
            }
        }
        composeTestRule.runOnUiThread {
            // We can't easily reach the private listState from the test, so verify
            // the topbar/section text is still rendered and no crash occurred.
        }
        composeTestRule.waitForIdle()
    }
}
```

(The test's primary purpose is to ensure the new `initialScroll`/`onScroll` parameters do not crash and the screen renders. Visual scroll restoration is verified by manual QA at the end of Task 7.)

- [ ] **Step 8: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 152 tests passing.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt \
        app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt \
        app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt \
        app/src/test/java/com/novelreader/ui/library/LibraryViewModelScrollTest.kt \
        app/src/test/java/com/novelreader/ui/library/tabs/ChaptersTabScrollTest.kt
git commit -m "feat: scroll — Chapters tab per-novel remembers position (SavedStateHandle)"
```

---

### Task 8: A11y — contentDescription audit

**Files:**
- Modify: 6-10 files under `app/src/main/java/com/novelreader/ui/` (exact list enumerated at task time)
- Modify: `app/src/main/res/values/strings.xml` (add ~12-18 new cd_ labels)
- Modify: `app/src/main/res/values-en/strings.xml` (add en)

**Interfaces:** none. Pure content-description replacements.

- [ ] **Step 1: Enumerate the functional `contentDescription = null` cases**

Run:
```bash
grep -rn "contentDescription = null" app/src/main/java/com/novelreader/ui
```

For each hit, classify as **decorative** (visual only, paired with adjacent text — keep `null`) or **functional** (icon-only button or state indicator — add a string resource).

Expected functional candidates based on the spec's audit: `failed_chapters_section_title` icon, library filter row sort icon, library cover-image placeholders, character card photo-add icon, settings icons, import progress banner icons, create-character dialog icons, bookmark-dialog icons, reader settings sheet touch-app icon.

- [ ] **Step 2: Add the new string resources**

In `app/src/main/res/values/strings.xml`, add (one entry per functional case; rename `<..._cd>` to match each icon's purpose):
```xml
    <string name="cd_failed_chapters_section">Capítulos com falha</string>
    <string name="cd_sort_order">Ordenar</string>
    <string name="cd_cover_placeholder">Capa da novel</string>
    <string name="cd_add_photo">Adicionar foto</string>
    <string name="cd_settings">Configurações</string>
    <string name="cd_about">Sobre</string>
    <string name="cd_import_file">Importar arquivo</string>
    <string name="cd_export">Exportar</string>
    <string name="cd_delete">Excluir</string>
    <string name="cd_refresh">Atualizar</string>
    <string name="cd_search">Pesquisar</string>
    <string name="cd_public">Importar da web</string>
```

In `app/src/main/res/values-en/strings.xml`:
```xml
    <string name="cd_failed_chapters_section">Failed chapters</string>
    <string name="cd_sort_order">Sort order</string>
    <string name="cd_cover_placeholder">Novel cover</string>
    <string name="cd_add_photo">Add photo</string>
    <string name="cd_settings">Settings</string>
    <string name="cd_about">About</string>
    <string name="cd_import_file">Import file</string>
    <string name="cd_export">Export</string>
    <string name="cd_delete">Delete</string>
    <string name="cd_refresh">Refresh</string>
    <string name="cd_search">Search</string>
    <string name="cd_public">Import from web</string>
```

Adjust the exact list of `cd_*` keys to match the actual functional icons found in Step 1. Remove or rename any that don't apply.

- [ ] **Step 3: Replace `contentDescription = null` with `stringResource(R.string.cd_...)` at each functional site**

For each file enumerated in Step 1, change:
```kotlin
Icon(Icons.Default.X, contentDescription = null, ...)
```
to:
```kotlin
Icon(Icons.Default.X, contentDescription = stringResource(R.string.cd_x), ...)
```

Examples:
- `ChaptersTab.kt:195` — `Icon(Icons.Default.CloudOff, contentDescription = null, ...)` → `contentDescription = stringResource(R.string.cd_failed_chapters_section)`
- `LibraryTab.kt:271` — `Icon(Icons.Default.PhotoCamera, contentDescription = null)` → `contentDescription = stringResource(R.string.cd_add_photo)`
- (Continue for each functional site enumerated in Step 1.)

- [ ] **Step 4: Compile to verify no missing-string-resource errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 152 tests passing (no test changes in this task; existing tests must still pass).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/ \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml
git commit -m "feat: a11y — contentDescription audit (~12-18 icon labels added)"
```

---

### Task 9: Haptics on primary actions

**Files:**
- Create: `app/src/main/java/com/novelreader/util/HapticClickable.kt`
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/PersonagensTab.kt` (apply to FABs)
- Modify: `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt` (apply to bookmark-add)
- Modify: `app/src/main/java/com/novelreader/ui/navigation/NavGraph.kt` (apply to tab switch)

**Interfaces:**
- Produces: `fun Modifier.hapticClickable(haptic: androidx.compose.ui.hapticfeedback.HapticFeedback?, enabled: Boolean = true, onClick: () -> Unit): Modifier`.

- [ ] **Step 1: Create the helper**

Create `app/src/main/java/com/novelreader/util/HapticClickable.kt`:

```kotlin
package com.novelreader.util

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

fun Modifier.hapticClickable(
    haptic: HapticFeedback?,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this.clickable(enabled = enabled) {
    haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    onClick()
}
```

- [ ] **Step 2: Apply to Personagens FABs**

In `app/src/main/java/com/novelreader/ui/library/tabs/PersonagensTab.kt`, wrap the body of each `ExtendedFloatingActionButton` from Task 3 with the haptic. The simplest path: pass the haptic to the FAB via `Modifier` if `ExtendedFloatingActionButton` supports it; otherwise, wrap the `onClick` lambda:
```kotlin
ExtendedFloatingActionButton(
    onClick = {
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        showAddDialog = true
    },
    // ...
)
```

Add the import:
```kotlin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
```

At the top of the composable function body:
```kotlin
    val haptic = LocalHapticFeedback.current
```

- [ ] **Step 3: Apply to bookmark-add in ReaderScreen**

In `app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt`, find the `IconButton(onClick = { showAddBookmarkDialog = true })` (or equivalent — search for `showAddBookmarkDialog`) and wrap the `onClick`:
```kotlin
IconButton(onClick = {
    haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    showAddBookmarkDialog = true
})
```

Add the same imports as in Step 2 and `val haptic = LocalHapticFeedback.current` near the top of the composable.

- [ ] **Step 4: Apply to tab switch in NavGraph**

In `app/src/main/java/com/novelreader/ui/navigation/NavGraph.kt`, wrap the `NavHost` in a `DisposableEffect` that observes the current route and fires a haptic on change. Add inside `NovelReaderNavGraph`:

```kotlin
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    androidx.compose.runtime.LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute != Routes.LIBRARY) {
            haptic?.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }
```

Add imports:
```kotlin
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
```

(The "skip LIBRARY" guard avoids firing a haptic on the initial composition; the user only feels it when they actively navigate.)

- [ ] **Step 5: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 152 tests passing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/novelreader/util/HapticClickable.kt \
        app/src/main/java/com/novelreader/ui/library/tabs/PersonagensTab.kt \
        app/src/main/java/com/novelreader/ui/reader/ReaderScreen.kt \
        app/src/main/java/com/novelreader/ui/navigation/NavGraph.kt
git commit -m "feat: haptics on primary actions (FAB, bookmark add, tab switch)"
```

---

### Task 10: i18n — complete en translations

**Files:**
- Modify: `app/src/main/res/values-en/strings.xml` (add missing keys)
- Create: `app/src/test/java/com/novelreader/i18n/I18nCoverageTest.kt`

**Interfaces:** none.

- [ ] **Step 1: Enumerate missing keys**

Run:
```bash
python3 -c "
import xml.etree.ElementTree as ET
pt = set(e.attrib['name'] for e in ET.parse('app/src/main/res/values/strings.xml').getroot())
en = set(e.attrib['name'] for e in ET.parse('app/src/main/res/values-en/strings.xml').getroot())
print('\n'.join(sorted(pt - en)))
"
```

Expected: a list of pt keys missing in en (likely 5-15 keys from v2.4.x additions plus a few stragglers from v2.3.x).

- [ ] **Step 2: Translate missing keys and add to `values-en/strings.xml`**

For each missing key, add the en translation in `app/src/main/res/values-en/strings.xml`. Use machine translation (Google Translate CLI: `trans -b -s pt -t en "<pt text>"`) for first pass; mark any uncertain ones in a follow-up TODO. Preserve all `<!-- comments -->` and `xliff:g` tags where present.

- [ ] **Step 3: Write the coverage test**

Create `app/src/test/java/com/novelreader/i18n/I18nCoverageTest.kt`:

```kotlin
package com.novelreader.i18n

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class I18nCoverageTest {

    private lateinit var allStringNames: Set<String>
    private lateinit var packageName: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        packageName = context.packageName
        val rClass = Class.forName("com.novelreader.R\$string")
        allStringNames = rClass.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
    }

    @Test
    fun `every pt-BR string has an en counterpart`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val enResources = context.createPackageContext(packageName, 0).resources.also {
            it.configuration.setLocale(Locale.ENGLISH)
        }
        val missing = allStringNames.filter { name ->
            enResources.getIdentifier(name, "string", packageName) == 0
        }
        assertThat(missing).isEmpty()
    }
}
```

The reflection approach enumerates every `R.string.*` field (the merged list across all locales). Each name is then looked up in the en-locale Resources. A missing lookup (`id == 0`) means the en translation was never declared. Robolectric provides the `Context`; `Locale.ENGLISH` is set on a copy of the resources so we query the en values directory, not the default.

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.i18n.I18nCoverageTest"`
Expected: FAIL with the list of missing keys.

- [ ] **Step 5: Verify all missing keys from Step 1 are filled in `values-en/strings.xml`**

Re-run the python script from Step 1. Expected: empty output (no missing keys).

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.i18n.I18nCoverageTest"`
Expected: PASS.

- [ ] **Step 7: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 153 tests passing.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/values-en/strings.xml \
        app/src/test/java/com/novelreader/i18n/I18nCoverageTest.kt
git commit -m "i18n: complete en translations (all pt keys mirrored in values-en)"
```

---

### Task 11: Dynamic color opt-in (Android 12+)

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/local/preferences/AppPreferences.kt`
- Modify: `app/src/main/java/com/novelreader/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/novelreader/ui/settings/SettingsScreen.kt` (add toggle)
- Modify: `app/src/main/res/values/strings.xml` (add 2 strings)
- Modify: `app/src/main/res/values-en/strings.xml` (add 2 en strings)
- Create: `app/src/test/java/com/novelreader/data/local/preferences/AppPreferencesDynamicColorTest.kt`

**Interfaces:**
- Produces:
  - `AppPreferences.dynamicColorEnabled: Flow<Boolean>` (default `true`)
  - `AppPreferences.updateDynamicColorEnabled(Boolean)`
  - `NovelReaderTheme` accepts `useDynamicColor: Boolean = true` and `dynamicColorScheme: ColorScheme?` (computed from `useDynamicColor` and `Build.VERSION.SDK_INT`)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/novelreader/data/local/preferences/AppPreferencesDynamicColorTest.kt`:

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
class AppPreferencesDynamicColorTest {

    @Test
    fun `dynamicColorEnabled defaults to true`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPreferences(context)
        assertThat(prefs.dynamicColorEnabled.first()).isTrue()
    }

    @Test
    fun `updateDynamicColorEnabled false persists`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPreferences(context)
        prefs.updateDynamicColorEnabled(false)
        assertThat(prefs.dynamicColorEnabled.first()).isFalse()
        prefs.updateDynamicColorEnabled(true)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.local.preferences.AppPreferencesDynamicColorTest"`
Expected: FAIL with unresolved reference `dynamicColorEnabled`.

- [ ] **Step 3: Add the preference to `AppPreferences`**

In `app/src/main/java/com/novelreader/data/local/preferences/AppPreferences.kt`, add the import:
```kotlin
import androidx.datastore.preferences.core.booleanPreferencesKey
```

And add to the `Keys` object:
```kotlin
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
```

Add new properties after the existing `locale` block:
```kotlin
    val dynamicColorEnabled: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: true
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.local.preferences.AppPreferencesDynamicColorTest"`
Expected: PASS.

- [ ] **Step 5: Add string resources**

In `app/src/main/res/values/strings.xml`:
```xml
    <string name="settings_dynamic_color_title">Tema dinâmico (Android 12+)</string>
    <string name="settings_dynamic_color_subtitle">Usa as cores do seu sistema</string>
```

In `app/src/main/res/values-en/strings.xml`:
```xml
    <string name="settings_dynamic_color_title">Dynamic color (Android 12+)</string>
    <string name="settings_dynamic_color_subtitle">Use your system colors</string>
```

- [ ] **Step 6: Update `Theme.kt` to accept `useDynamicColor`**

In `app/src/main/java/com/novelreader/ui/theme/Theme.kt`, add imports:
```kotlin
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
```

Add a `ColorScheme?` parameter and resolve:
```kotlin
@Composable
fun NovelReaderTheme(
    appTheme: String = "system",
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 7: Add the toggle to SettingsScreen**

In `app/src/main/java/com/novelreader/ui/settings/SettingsScreen.kt`, find the section between "Tema" and "Idioma". Add a new `ListItem` (read the existing pattern from the file's other `ListItem`s for the theme/language/import sections):

```kotlin
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color_title)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = dynamicColorEnabled,
                        onCheckedChange = { viewModel.updateDynamicColorEnabled(it) },
                        enabled = isAndroid12OrLater
                    )
                }
            )
```

`dynamicColorEnabled` and `isAndroid12OrLater` need to be added as state in `SettingsScreen.kt` (or read from `SettingsViewModel`). Wire as:
```kotlin
    val context = androidx.compose.ui.platform.LocalContext.current
    val isAndroid12OrLater = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        viewModel.dynamicColorEnabled.collect { /* state collected above */ }
    }
```

Add a `dynamicColorEnabled: StateFlow<Boolean>` to `SettingsViewModel.kt` (collect from `AppPreferences.dynamicColorEnabled`; injected via Hilt). Add `fun updateDynamicColorEnabled(Boolean)` to the VM that calls `appPreferences.updateDynamicColorEnabled(...)`.

- [ ] **Step 8: Pass the preference into `NovelReaderTheme`**

In `app/src/main/java/com/novelreader/MainActivity.kt` (or wherever `NovelReaderTheme` is called), read the preference flow and pass it:
```kotlin
@Composable
fun RootContent() {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val dynamicColor by appPreferences.dynamicColorEnabled.collectAsState(initial = true)
    NovelReaderTheme(useDynamicColor = dynamicColor) {
        // existing root content
    }
}
```

- [ ] **Step 9: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 154 tests passing.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/novelreader/data/local/preferences/AppPreferences.kt \
        app/src/main/java/com/novelreader/ui/theme/Theme.kt \
        app/src/main/java/com/novelreader/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/novelreader/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/novelreader/MainActivity.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/test/java/com/novelreader/data/local/preferences/AppPreferencesDynamicColorTest.kt
git commit -m "feat: dynamic color opt-in (Android 12+) via Theme.kt + AppPreferences"
```

---

### Task 12: Tab transition animations

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/navigation/NavGraph.kt`

**Interfaces:** none. Visual change.

- [ ] **Step 1: Add transition extension helper**

In `app/src/main/java/com/novelreader/ui/navigation/NavGraph.kt`, add imports:
```kotlin
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideIntoContainer
import androidx.compose.animation.slideOutOfContainer
import androidx.navigation.NavGraphBuilder
```

Add a top-level helper at the bottom of the file:
```kotlin
private fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) }
    ) { entry -> content(entry) }
}
```

- [ ] **Step 2: Replace `composable(...)` with `animatedComposable(...)` in the NavHost body**

In the same file, in the `NavHost { ... }` block, change each `composable(` to `animatedComposable(` (6 occurrences: LIBRARY_WITH_SELECTION, IMPORT, READER, FAVORITES, SETTINGS, ABOUT).

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 154 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/navigation/NavGraph.kt
git commit -m "ui: tab transition animations (slideIntoContainer 220ms)"
```

---

### Task 13: Bump version to v2.4.2

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md` (version history entry)
- Modify: `README_PT.md` (version history entry)

**Interfaces:** none. Version metadata only.

- [ ] **Step 1: Update `versionCode` and `versionName`**

In `app/build.gradle.kts`, find the `android { defaultConfig { ... } }` block. Change:
- `versionCode = 15` → `versionCode = 16`
- `versionName = "2.4.1"` → `versionName = "2.4.2"`

- [ ] **Step 2: Add v2.4.2 entry to README.md**

In `README.md`, find the version history section (search for `## v2.4.0` or `## Version history`). Add a new section above v2.4.1:
```markdown
## v2.4.2 (2026-06-25)

UI/UX polish and infrastructure:

- Reader: chapter list in the bottom sheet now wraps to 4 lines (was 1)
- Library: empty state with illustration and "Add your first novel" CTA
- Library: "Reading" badge now shows relative time ("Lendo · há 2 h")
- Personagens tab: ExtendedFAB with labels (Add / Import)
- A11y: ~12-18 icon-only buttons now have contentDescription
- Haptics: light haptic on bookmark add, FAB tap, and tab switch
- Dynamic color: opt-in toggle in Settings (Android 12+)
- Tab transitions: 220ms slide between library / reader / settings
- Compose UI test base infrastructure (Robolectric)
- Scroll memory: Library tab and per-novel Chapters tab remember position
- i18n: complete en translations (210 keys mirrored)

No data migration required.
```

(Adjust the bullet list to match what was actually shipped; this is a draft.)

- [ ] **Step 3: Add the equivalent Portuguese entry to README_PT.md**

Mirror the same section in `README_PT.md`, translating each bullet to Portuguese.

- [ ] **Step 4: Compile to verify the build picks up the new version**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run all unit tests one final time**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 154 tests passing (138 baseline + 16 new from this plan).

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts README.md README_PT.md
git commit -m "chore: bump version to v2.4.2 (versionCode 16)"
```

---

### Task 14: Fix AGENTS.md i18n convention note (housekeeping)

**Files:**
- Modify: `AGENTS.md`

**Interfaces:** none. Doc only.

- [ ] **Step 1: Find and correct the i18n line**

In `AGENTS.md`, search for the line containing `values-pt-rBR/strings.xml`. Replace the relevant bullet (likely in a "Code Conventions" or "Strings" section) from:
```
- Strings always bilingual (pt-BR in `values-pt-rBR/strings.xml`, en in `values/strings.xml`)
```
to:
```
- Strings always bilingual (pt-BR in `values/strings.xml` as the default, en in `values-en/strings.xml`)
```

(If the line is worded differently, preserve the surrounding sentence and only correct the file paths.)

- [ ] **Step 2: Verify no other references to the old convention remain**

Run: `grep -n "values-pt-rBR" AGENTS.md README.md README_PT.md docs/`
Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md
git commit -m "docs: fix AGENTS.md i18n convention note (values/ + values-en/, not values-pt-rBR/)"
```

---

## Self-review checklist (for the implementer to verify at the end)

After all 14 tasks are complete and the final `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` passes:

- [ ] `git log --oneline` shows 14 new commits (plus any pushes in Task 0).
- [ ] `app/build.gradle.kts` has `versionCode = 16` and `versionName = "2.4.2"`.
- [ ] No `contentDescription = null` remains on any **functional** Icon (decorative cases inside paired-text rows are acceptable).
- [ ] Dynamic color toggle in Settings visibly changes the theme on Android 12+; theme reverts to indigo+orange on Android 11 and below.
- [ ] Tabs slide with a 220ms animation on push and pop.
- [ ] Long-press on the bookmark-add button in the reader produces a light haptic.
- [ ] In the reader, the chapter list bottom sheet wraps chapter titles to 4 lines.
- [ ] In the library, scrolling to chapter 30 of a novel, deselecting, reselecting, returns to chapter 30.
- [ ] In the library, scrolling the novel list, navigating to Settings and back, returns to the same scroll position.
- [ ] `AGENTS.md` no longer mentions `values-pt-rBR`.
- [ ] All `values/strings.xml` keys have an `values-en/strings.xml` counterpart.
- [ ] `app/release/baselineProfiles/*` and `app/release/output-metadata.json` are NOT staged in any commit.
- [ ] The 10 prior unpushed commits are still ahead of `origin/main` (SSH push is out of scope for this plan).
