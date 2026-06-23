# NovelReader Issue-Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 6 GitHub bugs (#1, #2, #3, #4, #6, #7) against `fabriciof807/NovelReader`, one commit per issue, direct to `main`.

**Architecture:** MVVM + UseCase + MVI (Library uses `LibraryIntent`). State via `StateFlow`/Room Flow. No new entities, DAOs, or migrations. No new dependencies.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2024.12.01), Room 2.8.4, Hilt 2.59.2, Robolectric, MockK, Turbine.

## Global Constraints
- Kotlin official style; **no comments unless requested**.
- PT-BR comments only where unavoidable; strings always bilingual (existing strings reused — no new string resources needed).
- New Library behaviors go through new `LibraryIntent`s, not direct VM calls from UI.
- `StateFlow` for UI state, `MutableStateFlow` for internal, `@IoDispatcher` for IO.
- One commit per issue, conventional message referencing issue URL (e.g. `fix(library): ... (#1)`), direct to `main`.
- **Always run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` before claiming done.**
- `app/release/baselineProfiles/*` regenerated every build — do NOT stage.

---

### Task 0: Try to push the 6 unpushed commits

**Files:** none

- [ ] **Step 1: Attempt push**

Run: `git push origin main`
Expected: either success (6 commits land on origin) OR failure with SSH/auth error. On failure, continue anyway — fixes stack on top locally.

- [ ] **Step 2: If push fails, raise it but proceed**

No commit step. Do not configure SSH keys.

---

### Task 1: Issue #1 — Make the delete confirm dialog reachable

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt:324-327` (`requestDeleteById`) and `:353-356`, `:360-363` (cover variants share the same bug — fix together)
- Test: `app/src/test/java/com/novelreader/ui/library/LibraryViewModelTest.kt` (add 2 tests)

**Interfaces:**
- Consumes: `viewModel.novels: StateFlow<List<NovelEntity>>` (already exists, line 107-116)
- Produces: working `RequestDelete(id)` intent path → `_showDeleteDialog` becomes non-null when novels contains the id.

- [ ] **Step 1: Write failing tests**

Append to `LibraryViewModelTest.kt`:

```kotlin
@Test
fun `RequestDelete intent sets showDeleteDialog when novels flow contains the id`() = runTest {
    val novel = NovelEntity(id = 7, title = "In Flow")
    val novelsFlow = MutableStateFlow(listOf(novel))
    every { novelDao.getAllNovels() } returns novelsFlow
    // re-create viewModel so it subscribes to the new novels flow
    viewModel = LibraryViewModel(
        context, savedState, novelDao, chapterDao, bookmarkDao, bgManager, prefs,
        charManagement, coverManagement, charPhotoDao, importer, updateCheckScheduler,
        webImportUseCase, failedChapterDao, retryChapterUseCase, scanMissingChaptersUseCase,
        chapterInserter, parserRegistry, mhtParser, fileCharsetDetector, Dispatchers.Unconfined
    )

    viewModel.onIntent(LibraryIntent.RequestDelete(7))

    assertThat(viewModel.showDeleteDialog.value).isEqualTo(novel)
}

@Test
fun `confirmDelete after RequestDelete intent calls cover use case and clears dialog`() = runTest {
    val novel = NovelEntity(id = 7, title = "In Flow")
    val novelsFlow = MutableStateFlow(listOf(novel))
    every { novelDao.getAllNovels() } returns novelsFlow
    viewModel = LibraryViewModel(
        context, savedState, novelDao, chapterDao, bookmarkDao, bgManager, prefs,
        charManagement, coverManagement, charPhotoDao, importer, updateCheckScheduler,
        webImportUseCase, failedChapterDao, retryChapterUseCase, scanMissingChaptersUseCase,
        chapterInserter, parserRegistry, mhtParser, fileCharsetDetector, Dispatchers.Unconfined
    )
    coEvery { coverManagement.deleteNovelCovers(7, null) } returns Result.success(Unit)

    viewModel.onIntent(LibraryIntent.RequestDelete(7))
    viewModel.confirmDelete()

    assertThat(viewModel.showDeleteDialog.value).isNull()
    coVerify { coverManagement.deleteNovelCovers(7, null) }
}
```

Imports to add: `coVerify` and `MutableStateFlow` (MutableStateFlow already imported).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.LibraryViewModelTest"`
Expected: 2 failures — `showDeleteDialog.value` is null because `requestDeleteById` reads `_state.value.novels` (always empty).

- [ ] **Step 3: Implement the fix**

In `LibraryViewModel.kt`, replace `requestDeleteById` (lines 324-327):

```kotlin
private fun requestDeleteById(novelId: Long) {
    val novel = novels.value.find { it.id == novelId } ?: return
    _showDeleteDialog.value = novel
}
```

Apply the same one-line change to `requestChangeCoverById` (lines 353-356) and `requestCoverByUrlById` (lines 360-363).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.LibraryViewModelTest"`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt \
        app/src/test/java/com/novelreader/ui/library/LibraryViewModelTest.kt
git commit -m "fix(library): make delete confirm dialog reachable via novels flow (#1)

https://github.com/fabriciof807/NovelReader/issues/1"
```

---

### Task 2: Issue #2 — System back deselects novel instead of closing app

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt` (add `BackHandler`)
- Test: `app/src/test/java/com/novelreader/ui/library/LibraryViewModelTest.kt` (add 1 intent test — already covers DeselectNovel logic; add explicit intent-route assertion)

- [ ] **Step 1: Write failing test**

Add:
```kotlin
@Test
fun `DeselectNovel intent clears selectedNovel and resets tab`() = runTest {
    val novel = NovelEntity(id = 5, title = "Selected")
    viewModel.selectNovel(novel)
    assertThat(viewModel.selectedNovel.value).isEqualTo(novel)

    viewModel.onIntent(LibraryIntent.DeselectNovel)

    assertThat(viewModel.selectedNovel.value).isNull()
    assertThat(viewModel.selectedTab.value).isEqualTo(0)
}
```

- [ ] **Step 2: Run test to verify it fails or passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.LibraryViewModelTest.*DeselectNovel*"`
Expected: PASS already (DeselectNovel intent already routes to `deselectNovel()`). The test serves as a regression guard — proceed.

- [ ] **Step 3: Add BackHandler to LibraryScreen**

In `LibraryScreen.kt`, near the top of the composable (right after `selectedNovel` is collected — around line 73), add:

```kotlin
val selectedNovel by viewModel.selectedNovel.collectAsState()
BackHandler(enabled = selectedNovel != null) {
    viewModel.onIntent(LibraryIntent.DeselectNovel)
}
```

Imports: `import androidx.activity.compose.BackHandler`.

- [ ] **Step 4: Compile + run tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: compile clean, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt \
        app/src/test/java/com/novelreader/ui/library/LibraryViewModelTest.kt
git commit -m "fix(library): system back deselects novel instead of closing app (#2)

https://github.com/fabriciof807/NovelReader/issues/2"
```

---

### Task 3: Issue #7 — Correct badge labels for MISSING_NUMBER and EMPTY_CONTENT

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt:345-366` (`ErrorTypeBadge` when expression)

- [ ] **Step 1: Make the fix (UI constant change — no unit test feasible; rely on visual + compile)**

Replace the `when` in `ErrorTypeBadge` (lines 348-351):

```kotlin
val label = when (errorType) {
    FailedChapterErrorType.NETWORK -> stringResource(R.string.error_type_network)
    FailedChapterErrorType.PARSE -> stringResource(R.string.error_type_parse)
    FailedChapterErrorType.IO -> stringResource(R.string.error_type_io)
    FailedChapterErrorType.MISSING_NUMBER -> stringResource(R.string.error_type_missing_number)
    FailedChapterErrorType.EMPTY_CONTENT -> stringResource(R.string.error_type_empty_content)
    else -> stringResource(R.string.error_type_io)
}
```

- [ ] **Step 2: Compile + run tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: compile clean, 99 tests still pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt
git commit -m "fix(library): correct badge labels for missing_number and empty_content (#7)

https://github.com/fabriciof807/NovelReader/issues/7"
```

---

### Task 4: Issue #6 — Chapter title maxLines=4 in chapter list

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt:151` (chapter row) and `:286` (FailedChapterRow title)

- [ ] **Step 1: Apply the change**

In ChaptersTab.kt chapter row (line 147-158), change `maxLines = 2` → `maxLines = 4`. In `FailedChapterRow` (around line 286), change its title `maxLines = 2` → `maxLines = 4` for consistency.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt
git commit -m "fix(library): allow chapter titles up to 4 lines in list rows (#6)

https://github.com/fabriciof807/NovelReader/issues/6"
```

---

### Task 5: Issue #3 — Reserve FAB-clearing bottom padding on Personagens tab

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/PersonagensTab.kt:194-199` (LazyColumn contentPadding)

- [ ] **Step 1: Apply the change**

Replace `LazyColumn` contentPadding in `PersonagensTab.kt` (lines 194-199):

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .weight(1f),
    contentPadding = PaddingValues(
        start = 4.dp,
        top = 4.dp,
        end = 4.dp,
        bottom = 140.dp
    )
) {
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/novelreader/ui/library/tabs/PersonagensTab.kt
git commit -m "fix(library): keep last character card above personagens FABs (#3)

https://github.com/fabriciof807/NovelReader/issues/3"
```

---

### Task 6: Issue #4 — Scan range default + fileName mismatch

**Files:**
- Modify: `app/src/main/java/com/novelreader/domain/usecase/ScanMissingChaptersUseCase.kt` (LOCAL fileName key helper, if needed)
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt` (signature + scan dialog default)
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt` (pass `totalChapters`)
- Create: `app/src/test/java/com/novelreader/domain/usecase/ScanMissingChaptersUseCaseTest.kt`

- [ ] **Step 1: Verify the local re-import fileName key**

Confirm the local (MHT) re-import key is `"chapter_${n}"` by reading `app/src/main/java/com/novelreader/domain/usecase/importnovel/NovelImporter.kt` and any local import path. (Use grep / read to verify.)

- [ ] **Step 2: Write the regression test**

Create `ScanMissingChaptersUseCaseTest.kt`:

```kotlin
package com.novelreader.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterErrorType
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.domain.usecase.webimport.NovelImporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScanMissingChaptersUseCaseTest {
    private val novelDao = mockk<NovelDao>(relaxed = true)
    private val chapterDao = mockk<ChapterDao>(relaxed = true)
    private val failedChapterDao = mockk<FailedChapterDao>(relaxed = true)
    private val novelImporter = mockk<NovelImporter>(relaxed = true)

    private val useCase = ScanMissingChaptersUseCase(
        novelDao, chapterDao, failedChapterDao, novelImporter
    )

    @Test
    fun `scanLocal writes FailedChapterEntity with fileName matching local re-import key`() = runTest {
        val novel = NovelEntity(id = 1, title = "X", sourceUrl = "")
        coEvery { novelDao.getNovelById(1) } returns novel
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns listOf<ChapterEntity>()

        useCase.scanLocal(1, from = 5, to = 5)

        coVerify {
            failedChapterDao.insert(match {
                it.chapterNumber == 5 &&
                    it.errorType == FailedChapterErrorType.MISSING_NUMBER &&
                    it.fileName == "chapter_5"
            })
        }
    }
}
```

(Adjust constructor arg order to match `ScanMissingChaptersUseCase` actual signature.)

- [ ] **Step 3: Run test to verify it passes (or fix the key)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.novelreader.domain.usecase.ScanMissingChaptersUseCaseTest"`
Expected: the current `scanLocal` already uses `"chapter_${n}"`, so this test should PASS. If it fails because the key construction moved, fix at `ScanMissingChaptersUseCase.kt:69`.

- [ ] **Step 4: Update the ScanRangeDialog default**

In `ChaptersTab.kt`:
- Add parameter `totalChapters: Int = 0` to the `ChaptersTab` composable signature (after `maxChapterNumber`).
- Change line 99 from `(maxChapterNumber + 5).coerceAtLeast(1)` to:
  ```kotlin
  initialTo = (maxOf(totalChapters, maxChapterNumber) + 10).coerceIn(1, 9999)
  ```

- [ ] **Step 5: Plumb totalChapters from LibraryScreen**

In `LibraryScreen.kt` `ChaptersTab(...)` call (line 293-314), add:
```kotlin
totalChapters = selectedNovel?.totalChapters ?: 0,
```

- [ ] **Step 6: Compile + run tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: compile clean, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/novelreader/domain/usecase/ScanMissingChaptersUseCase.kt \
        app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt \
        app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt \
        app/src/test/java/com/novelreader/domain/usecase/ScanMissingChaptersUseCaseTest.kt
git commit -m "fix(library): widen scan range default and pin local fileName key (#4)

https://github.com/fabriciof807/NovelReader/issues/4"
```

---

### Task 7: Final verification

- [ ] **Step 1: Full build + tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: clean compile, ≥101 tests passing (99 baseline + new tests for #1 and #4).

- [ ] **Step 2: Verify the 6 issues are addressed in commit log**

Run: `git log --oneline -8`
Expected: 6 commits, one per issue, each referencing its GitHub URL.

- [ ] **Step 3: Close the 6 GitHub issues**

Run 6×:
```bash
gh issue close 1  -R fabriciof807/NovelReader --reason completed
gh issue close 2  -R fabriciof807/NovelReader --reason completed
gh issue close 3  -R fabriciof807/NovelReader --reason completed
gh issue close 4  -R fabriciof807/NovelReader --reason completed
gh issue close 6  -R fabriciof807/NovelReader --reason completed
gh issue close 7  -R fabriciof807/NovelReader --reason completed
```

(Issue #5 stays open — enhancement, out of scope.)
