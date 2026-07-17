# UX polish: top cross-cutting friction points

**Date:** 2026-07-16
**Project:** NovelReader
**Mode:** Ponytail full (smallest diffs that work; behavioral changes accepted)

## Problem

An open-ended "make the app more intuitive and fluid" pass. An audit of the
reader (`ui/reader/`) and library/import (`ui/library/`, `ui/import_novel/`,
`ui/webimport/`, `ui/favorites/`) surfaced ~20 friction points. This spec
targets the **6 highest-impact, cross-cutting** ones. Each is independent and
shippable on its own; together they remove the worst daily-use friction without
a large architectural change.

## Goals

1. The novel's context menu (change cover, auto-update, check updates, resync,
   delete) becomes discoverable without an undocumented long-press.
2. An empty/short chapter in the reader shows a clear state with an in-context
   recovery path, instead of a blank page.
3. Deleting a character can no longer happen by accident via swipe.
4. Reader DB failures (save/delete bookmark, create character, save scroll) are
   surfaced to the user with a one-tap retry, instead of failing silently.
5. Haptics are consistent: light on taps, strong on real long-press, none on
   route changes.
6. Changing a cover via URL shows the HTTPS error inline (dialog stays open),
   and cancelling the file picker no longer reopens it on the next novel select.

## Non-goals

- Auto-hide of the reader's tap-to-toggle controls.
- Debounce on the chapter-list filter input.
- Exposing `fontFamily` in the settings UI.
- Changing the "mark as read on open" behavior.
- Reconciling the divergent "retryable" definitions between the per-row Retry
  button and the "Retry all" button in `ChaptersTab`.
- Any web re-crawl / re-fetch from the **reader** (web retry stays in
  `ChaptersTab` where the failed-chapter machinery already lives).
- Writing `FailedChapterEntity` from the reader.
- Analytics, onboarding, icon changes, or refactors that change a public
  composable/ViewModel surface beyond what each fix requires.

## Design

### 1. Novel menu — visible 3-dot button on cards

**Files:** `ui/library/components/NovelCard.kt`, `ui/library/components/NovelListItem.kt`, `ui/library/tabs/LibraryTab.kt` (the existing `NovelMenu` at `:248-302`).

- Add an `IconButton` (`Icons.Default.MoreVert`, `contentDescription = R.string.options`) to both `NovelCard` and `NovelListItem`.
  - `NovelListItem`: trailing the row, always visible.
  - `NovelCard`: top-end corner of the cover, with a circular
    `Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))`
    backdrop so it stays readable over any cover art.
- `onClick` opens the **same** `DropdownMenu` already built by `NovelMenu`
  (`LibraryTab.kt:248-302`). Reuse verbatim; the menu block now takes a
  `showMenu`/`onMenuRequestChange` pair that is set by **either** the button's
  `onClick` or the existing `onLongClick`. No new menu items, no new routes.
- Long-press (`combinedClickable.onLongClick`) keeps working as a shortcut and
  now also emits `HapticFeedbackType.LongPress` (see §5).
- The button does **not** replace the tap-to-open behavior: card tap still opens
  the novel; the 3-dot button click stops propagation by handling its own
  `onClick` (the tap target is a distinct `IconButton`, not overlapping the
  card's `combinedClickable`).

### 2. Empty chapter state in the reader

**Files:** `ui/reader/ReaderViewModel.kt`, `ui/reader/ReaderScreen.kt`, new `ui/reader/EmptyChapterState.kt`, `data/local/db/dao/ChapterDao.kt`, new `domain/usecase/ReimportChapterContentUseCase.kt`.

**Detection** (in `ReaderViewModel.loadChapter`, after the chapter is loaded):
- Add `isEmpty: Boolean` to `ReaderState`, derived as
  `chapter.content.isBlank() || chapter.content.length < 200` (same threshold as
  `ScanMissingChaptersUseCase.kt:97`). Recomputed on every chapter load and after
  a recovery action reloads the chapter.
- When `isEmpty == true`, `ReaderScreen` renders `EmptyChapterState` **instead
  of** `ReaderWebView` (no blank page, no WebView mount).

**`EmptyChapterState` composable** (new file):
- Icon `Icons.Outlined.Article`, title `R.string.empty_chapter_title`, body
  `R.string.empty_chapter_body`.
- One `OutlinedButton` **Import MHT** → `viewModel.importMhtForChapter(uri)`.
  Launched via `rememberLauncherForActivityResult(GetContent())` with mime
  `*/*` and filtering handled by the parser (MHT or HTML, mirroring
  `LibraryViewModel.retryFailedChapterManually:746-750`).
- One `TextButton` **Back** → `saveScroll(); onBack()`.
- Subject to insets (`Modifier.systemBarsPadding`/`imePadding` not needed; reuse
  `Scaffold` content padding). Centered in the available space.

**Recovery — Import MHT** (`ReimportChapterContentUseCase`, new, in
`domain/usecase/`):
```kotlin
@Singleton
class ReimportChapterContentUseCase @Inject constructor(
    private val fileCharsetDetector: FileCharsetDetector,
    private val parserRegistry: ParserRegistry,
    private val mhtParser: MhtParser,
    private val chapterDao: ChapterDao,
    @ApplicationContext private val context: Context
) {
    suspend fun importFile(chapterId: Long, novelId: Long, uri: Uri): Result<Unit>
}
```
- Reads `fileName = fileCharsetDetector.getFileName(uri, context)`,
  `raw = fileCharsetDetector.readContent(uri, context)`.
- `parsed = if (mhtParser.isMhtFile(fileName)) parserRegistry.parseRaw(raw, fileName)
   else parserRegistry.parse(raw, fileName)` — identical to
  `LibraryViewModel.kt:746-750`.
- `chapterDao.updateContent(chapterId, parsed.content, parsed.title)` (new DAO
  method — see below). **Does not insert a new row**, avoiding duplicate
  chapters. (The existing `ChapterInserter.insertEntries` path with
  `existingId` only updates `orderIndex`, not content, so it cannot be reused
  here without creating a duplicate row.)
- Returns `Result.success(Unit)`; on exception returns `Result.failure(e)`.

**New DAO method** (`ChapterDao.kt`):
```kotlin
@Query("UPDATE chapters SET content = :content, title = :title WHERE id = :id")
suspend fun updateContent(id: Long, content: String, title: String)
```
Plain `UPDATE` on the existing row; no migration needed (no schema change).

**Wiring** (`ReaderViewModel`):
- Inject `ReimportChapterContentUseCase` (single new dep; keeps the parse logic
  out of the VM and reusable/testable).
- `fun importMhtForChapter(uri: Uri)` launches a coroutine that calls the use
  case with `state.chapter.id`, `state.chapter.novelId`, `uri`. On success,
  reloads the chapter (`loadChapter(chapterId)`) so `isEmpty` recomputes. On
  failure, emits to `errorEvents` (see §4) with the message.
- No `FailedChapterEntity` is written or deleted from the reader (per non-goal
  and the user's chosen approach). The `FailedChapterEntity` that
  `ScanMissingChaptersUseCase` may have created for the same `(novelId,
  fileName)` is reconciled by the existing `ChapterInserter` deletion-on-insert
  path when a later library re-import happens; out of scope here.

**Why no web Retry in the reader:** `ChapterEntity` has no `url` field, and the
`FailedChapterEntity.url` produced by the scan is the novel's `sourceUrl`
(the index page), not a chapter URL (`ScanMissingChaptersUseCase.kt:106`). A
true web retry would require re-crawling `novel.sourceUrl` to find the
chapter's URL by `fileName`/number — non-trivial logic that belongs in the
existing `ChaptersTab` recovery flow. Out of scope (non-goal).

### 3. Remove character swipe-to-dismiss

**File:** `ui/library/components/CharacterCard.kt` (`:81-94`).

- Delete the `SwipeToDismissBox` wrapper and its `confirmValueChange`. The card
  content sits directly in its parent `Box`.
- Deletion remains only via the existing Delete `IconButton` (`:357-367`) →
  `DeleteCharacterDialog`. Confirmation always required.
- No visual rearrangement; swipe's dismiss affordance is simply gone.

### 4. Surface `errorEvents` with a Retry action

**Files:** `ui/reader/ReaderViewModel.kt`, `ui/reader/ReaderScreen.kt`.

**Problem today:** `ReaderViewModel.errorEvents` (`SharedFlow<String>`,
`BufferOverflow.DROP_OLDEST`) is emitted at ~5 points (`:160,188,219,229,322`
— save scroll, add bookmark, delete bookmark, create character, etc.) but is
**never collected** anywhere in `ReaderScreen`, so all reader-DB failures are
silent.

**Retry-by-lambda mechanism** (in `ReaderViewModel`):
- `private var retryAction: (() -> Unit)? = null` — a closure capturing the exact
  redo for the last failed action (each emit site knows its own params; no
  sealed-type plumbing, no param storage in state).
- Each emission site does:
  ```kotlin
  retryAction = { viewModelScope.launch { /* redo this specific action */ } }
  _errorEvents.emit(context.getString(R.string.reader_action_failed, ...))
  ```
  On **success** of any action, set `retryAction = null` (cleared).
- New `StateFlow<Boolean> retryAvailable` (exposed via state — or simply a
  `MutableStateFlow(false)` updated alongside `retryAction`) so the Snackbar
  knows whether to show the Retry action.
- `fun retryLastFailedAction()` = `retryAction?.invoke()`. After invoking, the
  closure itself either clears `retryAction` (success) or re-emits (failure,
  Snackbar reappears). Edge case: if the underlying state is no longer valid
  (e.g., bookmark screen closed), the closure is a no-op that clears
  `retryAction`.

**UI (`ReaderScreen`):**
- Add a `SnackbarHost` to the existing `Scaffold` (`ReaderScreen.kt` already
  uses `Scaffold` with `topBar`/`bottomBar`; add
  `snackbarHost = { SnackbarHost(snackbarHostState) }`). No new nesting.
- `LaunchedEffect` collects `viewModel.errorEvents`; on each, shows a
  `Snackbar` with `actionLabel = getString(R.string.action_retry)` when
  `retryAvailable` is true. On action tap → `viewModel.retryLastFailedAction()`.
  Duration `SnackbarDuration.Short`; dismissed state does not clear `retryAction`
  (the action stays retryable until cleared by success or a new failure
  overwrites it). If a new error arrives while one is showing, the latest wins
  (DROP_OLDEST already ensures this).
- `saveScroll` failure's retry re-persists `lastKnownScrollPosition` (already
  held in VM state) — does not ask the WebView for fresh scroll.

**Scope of retried actions:** save scroll, add bookmark, delete bookmark, create
character — the actions that emit to `errorEvents`. No retry for chapter-load
errors (`state.error` path, which has its own Back button, is untouched).

### 5. Haptics standardization

**Files:** `ui/library/tabs/PersonagensTab.kt` (`:247,263`), `ui/reader/ReaderScreen.kt`
(`:356,406,421,460,467,478`), `ui/navigation/NavGraph.kt` (`:57,83-87`),
`ui/library/components/NovelCard.kt`, `ui/library/components/NovelListItem.kt`.

- **Taps on buttons/FABs** use the lightest available haptic: prefer
  `HapticFeedbackType.ContextClick` (available in Compose UI in this BOM
  2024.12.01); if absent at compile time, fall back to
  `HapticFeedbackType.TextHandleMove`. Apply to the two `PersonagensTab` FABs
  and the six reader tap sites.
- **Real long-press gestures** use `HapticFeedbackType.LongPress`: add it to
  `NovelCard`/`NovelListItem` `onLongClick` (today they rely on
  `combinedClickable`'s default, which is inconsistent with the explicit
  `LongPress` used elsewhere).
- **Remove** the haptic on route change in `NavGraph.kt:57,83-87` (tapping a
  nav destination is not a user-initiated haptic gesture).
- No haptics added to screens that don't already have them (`ChaptersTab`,
  `ImportScreen`, `FavoritesScreen`) — only standardize existing ones.
- Haptic behavior is not unit-tested (it is not logic). Verified manually.

### 6. Cover change cluster — inline URL error + clear picker on cancel

**Files:** `ui/library/CoverUrlDialog.kt`, `ui/library/LibraryViewModel.kt`
(`:451-462`), `ui/library/LibraryScreen.kt` (`:102-113`).

**URL dialog (`CoverUrlDialog.kt`):**
- Move the `https://` validation from the VM into the dialog's confirm path.
  On confirm: if `!url.startsWith("https://", ignoreCase = true)`, show an
  inline error `Text(color = error)` below the field with
  `R.string.cover_url_https_required` and **do not** call `onConfirm`. The dialog
  stays open; the user fixes the input in place.
- `LibraryViewModel.kt:451-455`: remove the `_showUrlDialog = false` and
  `_coverError = ...` assignments from the error branch (the dialog no longer
  closes on failure; no snackbar needed for the HTTPS error since it's inline).
  The VM still closes `_showUrlDialog` on success and still handles network
  failures for actual downloads separately (those keep using the snackbar).

**File picker cancel (`LibraryScreen.kt:102-113`):**
- The `rememberLauncherForActivityResult(GetContent())` callback receives a
  nullable `Uri`. When `uri == null` (user cancelled), call
  `viewModel.clearCoverRequest()` (`:466`) so `_coverTargetNovel` is cleared and
  the next novel selection does not reopen the picker. On `uri != null`, the
  existing success path runs as today.

### i18n

New strings (PT in `values/strings.xml`, EN in `values-en/strings.xml`):

| Key | PT | EN |
|-----|----|----|
| `options` | Opções | Options |
| `empty_chapter_title` | Capítulo sem conteúdo | Empty chapter |
| `empty_chapter_body` | Este capítulo está vazio. Importe um arquivo MHT/HTML com o conteúdo, ou volte e tente recuperá-lo pela aba de capítulos. | This chapter is empty. Import an MHT/HTML file with its content, or go back and recover it from the chapters tab. |
| `empty_chapter_import_failed` | Falha ao importar: %1$s | Import failed: %1$s |
| `action_retry` (new) | Repetir | Retry |
| `reader_action_failed` | Ação falhou: %1$s | Action failed: %1$s |

`failed_chapters_retry` ("Repetir"/"Retry") exists but is scoped to the failed-
chapters list; a generic `action_retry` is added for the reader Snackbar. If a
generic `retry` already exists, reuse it instead of adding `action_retry`.

## Testing

- **`ReaderViewModelTest`** (+):
  - `loadChapter sets isEmpty=true when content blank`
  - `loadChapter sets isEmpty=true when content length < 200`
  - `loadChapter sets isEmpty=false when content present`
  - `importMhtForChapter success updates content and clears isEmpty`
  - `importMhtForChart failure emits errorEvents and sets retryAvailable`
  - `retryLastFailedAction re-runs the last failed action`
  - `errorEvents emission sets retryAvailable; success clears it`
- **`ReimportChapterContentUseCaseTest`** (+):
  - `importFile calls updateContent with parsed MHT content`
  - `importFile calls updateContent with parsed HTML content`
  - `importFile returns failure on read error`
  Uses Robolectric for `Context`/`Uri`, mocks `FileCharsetDetector`,
  `ParserRegistry`, `MhtParser`, `ChapterDao`.
- **`LibraryViewModelTest`** (+):
  - `cover URL with non-https does not close dialog` (the VM no longer sets
    `_showUrlDialog=false` on the https error branch)
  - `clearCoverRequest clears _coverTargetNovel` (covers the cancel path)
- **Compose UI tests** (`androidTest`, or Robolectric where the project does
  them — follow `ReaderViewModelTest`/`LibraryViewModelTest` conventions):
  - `EmptyChapterState shown when isEmpty; not shown otherwise`
  - `NovelCard 3-dot button tapping opens the novel menu`
  - `CharacterCard swipe no longer dismisses`
  - `CoverUrlDialog shows inline https error and stays open on bad input`
- **Haptics**: no tests (behavior, not logic). Verified manually.

Existing tests: 305 passing. New ones run via
`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`.

## Manual verification

```
./gradlew :app:installDebug
adb logcat | grep -E "WM-|Notification|DeepLink|Compose"
```

1. **Menu:** tap the 3-dot on any novel card → menu opens; long-press also opens
   it (with haptic); tap card body → opens reader (not the menu).
2. **Empty chapter:** open a chapter with empty content → see `EmptyChapterState`,
   not a blank page. Import a valid MHT for it → chapter reloads with content.
   Cancel the picker → no crash, state unchanged.
3. **Character delete:** swipe a character card → no dismiss; only the Delete
   button + dialog removes it.
4. **Reader errors:** trigger a DB path failure → Snackbar with Retry appears;
   tap Retry → action re-runs.
5. **Haptics:** tap reader buttons and FABs → light haptic; long-press a novel
   → strong haptic; changing screen → no haptic.
6. **Cover URL:** enter `http://x` → inline error, dialog stays open; fix to
   `https://...` → proceeds. Cancel the file picker on another novel →
   selecting a novel does not reopen the picker.

## Spec self-review

- **Placeholders:** none. All identifiers (`ReimportChapterContentUseCase`,
  `updateContent`, `retryAction`, string keys) are concrete.
- **Internal consistency:** §2 deliberately reuses a new use case + new DAO
  method rather than `ChapterInserter.insertEntries` (which can't update
  existing content) — explained inline. §4's retry-lambda (not a sealed type)
  matches the "smallest diff" mode and is consistent with reusing each emit
  site's own params.
- **Scope:** 6 independent fixes, each small enough to be one implementation
  task; no cross-task state. Fits a single plan.
- **Ambiguity:**
  - Haptic type for taps: `ContextClick` preferred, `TextHandleMove` fallback —
    explicit.
  - "retry" string: reuse an existing generic one if present, else add
    `action_retry` — explicit.
  - The §2 `FailedChapterEntity` reconciliation is explicitly out of scope, not
    undefined.
  - Reader empty state recovers **only via MHT import**; web recovery is in
    `ChaptersTab` — explicitly stated to avoid feature drift.

## Migration / schema

No Room migration required. `ChapterDao.updateContent` is a new `@Query`
`UPDATE` on an existing column; no schema change, no new entity, no version
bump. `app/schemas/` is untouched.