# NovelReader Issue-Fix Batch — Design

**Date:** 2026-06-23
**Scope:** Fix 6 GitHub issues (#1, #2, #3, #4, #6, #7) in `fabriciof807/NovelReader`. Issue #5 (queue-in-library enhancement) is explicitly deferred.
**Author of original reports:** User (QA session, 2026-06-23).
**Approach:** Direct-to-main commits, one commit per issue.

---

## Decisions

- **#2 Back behavior:** Intercept + deselect via `BackHandler` on `LibraryScreen`. When `selectedNovel != null`, system back emits `LibraryIntent.DeselectNovel`. Second press exits app via default Nav dispatcher.
- **#6 maxLines:** 4 lines for chapter list title (ChaptersTab chapter row + FailedChapterRow). `NovelCard` (`maxLines=2`, full-width) and `NovelListItem` (`maxLines=1`) unchanged — they have different layouts.
- **#4 scope:** Both sub-bugs. (a) Default upper bound = `maxOf(novel.totalChapters, maxChapterNumber) + 10`, capped at 9999. (b) Pin the LOCAL `fileName` key as `"chapter_${n}"` with a regression test.
- **#3 FAB fix:** Add 140 dp bottom contentPadding to the `PersonagensTab` LazyColumn. Leaves the last character card fully tappable above the FAB stack.
- **#7 badge fix:** Extend `ErrorTypeBadge` `when` with `MISSING_NUMBER` and `EMPTY_CONTENT` cases. Both string resources already exist in `values/strings.xml` and `values-en/strings.xml`.

---

## Architecture overview

Six independent bugfixes, one commit per issue, direct to `main`. All fixes stay within the existing MVVM + UseCase + MVI pattern. Library uses `LibraryIntent`; new behaviors go through new `LibraryIntent`s (not direct VM calls from UI). State continues to flow through `StateFlow`/Room Flows already in place. No new entities, DAOs, or migrations. No new dependencies.

### Per-issue

#### #1 — Delete dialog never appears
- **Root cause:** `LibraryViewModel.requestDeleteById(id)` looks the novel up in `_state.value.novels`, which is permanently `emptyList()` (MVI refactor left `LoadNovels` as a no-op and `_state.novels` is never written). The find returns null → dialog never opens → `confirmDelete()` is never called. DB cascade and Room Flow refresh are both sound — they're just never reached.
- **Fix:** Source the novel from `novels: StateFlow<List<NovelEntity>>` (a StateFlow derived from `novelDao.getAllNovels()`). Replace `requestDeleteById` body to read `novels.value.find { it.id == novelId } ?: return`. Apply the same one-line fix to `requestChangeCoverById` and `requestCoverByUrlById` (same dead-field bug). Keep the Entity-accepting variants `requestDelete(novel)` etc. (existing tests use them — see `LibraryViewModelTest.kt:115, 122, 152, 166`).
- **Test:** `LibraryViewModelTest` — emit a novel into the `novels` flow, send `LibraryIntent.RequestDelete(id)`, assert `showDeleteDialog` becomes non-null and `confirmDelete()` calls `coverManagementUseCase.deleteNovelCovers` and clears the dialog.

#### #2 — System back closes app when a novel is selected
- **Approach:** Intercept + deselect via `BackHandler` on `LibraryScreen`.
- **Fix:** `BackHandler(enabled = selectedNovel != null) { viewModel.onIntent(LibraryIntent.DeselectNovel) }` after the `selectedNovel` collection in `LibraryScreen.kt`.
- **Test:** `LibraryViewModelTest` — select a novel, send `DeselectNovel`, assert state cleared (regression guard for the intent path; UI behavior verified via instrumented test if added).

#### #3 — FABs overlap character-card action buttons on Personagens tab
- **Approach:** Reserve FAB-clearing bottom contentPadding on the `LazyColumn`.
- **Fix:** Change `contentPadding = PaddingValues(vertical = 4.dp)` to `PaddingValues(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 140.dp)`. 140 dp = 108 dp FAB stack + 16 dp outer inset + small buffer.
- **Test:** Instrumented Compose test (optional). Change is a layout constant; visual verification is the primary check.

#### #4 — Scan range + fileName mismatch
- **Range default fix:** `ChaptersTab` gains a `totalChapters: Int = 0` parameter. Default `initialTo` becomes `(maxOf(totalChapters, maxChapterNumber) + 10).coerceIn(1, 9999)`. `LibraryScreen` passes `selectedNovel?.totalChapters ?: 0` at the call site.
- **fileName dedup fix:** Pin the LOCAL-scan key as `"chapter_${n}"` via a new regression test `ScanMissingChaptersUseCaseTest` asserting `FailedChapterEntity.fileName == "chapter_$n"` for `scanLocal`. No code change needed if the existing code already produces that key (verify at task time).
- **Test:** New `ScanMissingChaptersUseCaseTest`.

#### #6 — Chapter titles truncated too aggressively
- **Fix:** `ChaptersTab.kt` chapter row title `Text` — change `maxLines = 2` to `maxLines = 4`. Apply identically to `FailedChapterRow`'s title.
- **Test:** Skip — constant change; visual verification primary.

#### #7 — Wrong badge for missing_number / empty_content
- **Fix:** `ErrorTypeBadge` `when` extended to handle all five `FailedChapterErrorType` values; new cases reference existing string resources.
- **Test:** Compose test renders `FailedChapterRow` with `errorType = MISSING_NUMBER` and `EMPTY_CONTENT` and asserts the corresponding string. (Optional — strings exist; visual verification primary.)

---

## Cross-cutting

- **Error handling:** No new error paths. #1 surfaces existing `_errorEvents` from `coverManagementUseCase.deleteNovelCovers` on failure — unchanged.
- **Strings:** No new string resources needed.
- **Testing strategy:** TDD per issue where reasonable. Unit tests target #1, #2 (regression), #4. #3, #6, #7 are mostly layout / constant changes — visual verification + compile.
- **Commit plan:** One commit per issue, conventional messages referencing the issue URL.
- **Out of scope (explicit):** Issue #5 enhancement, novel-row creation at enqueue time, unique `(novelId, fileName)` index migration, SSH-key setup for the 6 unpushed commits.
