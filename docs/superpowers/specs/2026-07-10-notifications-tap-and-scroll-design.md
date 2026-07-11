# Notifications: tap-to-navigate + auto-scroll to failed

**Date:** 2026-07-10
**Project:** NovelReader

## Problem

Notifications posted by `ImportNotificationHelper` (`createForegroundInfo`,
`postCompletionNotification`, `postFailureNotification`) are built without
`setContentIntent()`. Tapping them does nothing. The "new chapters" and
"cloudflare re-verify" notifications in `UpdateNotificationHelper` already
work via `DeepLinkBus` — the import path is inconsistent.

Separately, tapping the failure notification should land the user on the
"Failed chapters" section of the Chapters tab. Today that section is at the
bottom of a `LazyColumn` — for novels with 1000+ chapters, it's unreachable
by manual scroll.

## Goals

1. **Tap any import notification** opens the app at the right screen via
   `DeepLinkBus` (same pattern as `UpdateNotificationHelper`).
2. **Tap failure notification** opens the Chapters tab and auto-scrolls to
   the "Failed chapters" section.
3. No regression of existing flows (fresh import without `targetNovelId`,
   queue mode, permission gate, cancel action).

## Non-goals

- Notification icon changes (not reported as broken).
- Settings toggle for notifications.
- Conditional scroll based on failed-count heuristics.
- Grouped notifications ("3 queued imports" → 1 notification).
- Pause/Resume action buttons (WorkManager has no native support).

## Design

### Data flow

```
[Notification] → tap → Intent(ACTION_OPEN_NOVEL | ACTION_OPEN_FAILED_CHAPTERS, novelId)
            → MainActivity.handleIntent (existing)
            → DeepLinkBus.emit(DeepLinkAction.ViewNovel | OpenFailedChapters)
            → NavGraph collector (existing)
            → navController.navigate(library?selectedNovelId=X&showFailed=Y)
            → LibraryViewModel.init reads SavedStateHandle
                → selectNovel(novel) (existing: tab=1, loads chapters + failedChapters)
                → if showFailed → _scrollToFailedRequest.value = novelId
            → ChaptersTab LaunchedEffect(pendingScrollToFailedNovelId, failedChapters)
                → scrollToItem(chapters.size + 1) + consumeScrollToFailed()
```

### `DeepLinkBus` replay change

`DeepLinkBus` changes from `replay = 0` to `replay = 1` so that
notification taps arriving before the NavGraph subscriber is attached (e.g.
app was killed) are replayed and still trigger navigation.

### Components

#### 1. `ImportNotificationHelper.kt`

New private helper to build the `PendingIntent` matching the
`UpdateNotificationHelper` pattern. Applied to:

- `createForegroundInfo()` — when `spec.targetNovelId != null`, tap opens
  the library with the novel selected. When null, tap opens the app but
  without a deep link (the novel hasn't been created yet).
- `postCompletionNotification()` — same.
- `postFailureNotification()` — same, but uses `ACTION_OPEN_FAILED_CHAPTERS`.

#### 2. `MainActivity.kt`

New companion constant `ACTION_OPEN_FAILED_CHAPTERS = "open_failed_chapters"`.
New branch in `handleIntent` that emits `DeepLinkAction.OpenFailedChapters`.

#### 3. `DeepLinkBus.kt`

New `DeepLinkAction.OpenFailedChapters(novelId: Long)` variant.
`_events` replay set to 1.

#### 4. `NavGraph.kt`

- `Routes.LIBRARY_WITH_SELECTION` gains `&showFailed={showFailed}` query param.
- New `Routes.libraryWithFailedChapters(novelId)` factory.
- New collector branch for `DeepLinkAction.OpenFailedChapters`.
- New `navArgument` `NavType.BoolType` for `showFailed`.

#### 5. `LibraryViewModel.kt`

- New `ARG_SHOW_FAILED = "showFailed"` companion const.
- New `_scrollToFailedRequest: MutableStateFlow<Long?>` + public
  `scrollToFailedRequest: StateFlow<Long?>` + `consumeScrollToFailed()`.
- In `init`, after `selectNovel`, if `showFailed` flag is true, set
  `_scrollToFailedRequest.value = pendingNovelId`.

#### 6. `LibraryScreen.kt`

Pass `scrollToFailedRequest` and `consumeScrollToFailed` to `ChaptersTab`.

#### 7. `ChaptersTab.kt`

New params `pendingScrollToFailedNovelId: Long?` and
`onConsumeScrollToFailed: () -> Unit`.

New `LaunchedEffect(pendingScrollToFailedNovelId, chapters.size, failedChapters.size)`
that calls `listState.scrollToItem(chapters.size + 1)` when
`pendingScrollToFailedNovelId == novelId && failedChapters.isNotEmpty()`.

### Error handling

| Condition | Behavior |
|-----------|----------|
| `targetNovelId == null` | Tap opens app, no deep link (novel doesn't exist yet) |
| Novel deleted between post and tap | `selectNovel` skipped, no scroll, user stays on Library |
| `POST_NOTIFICATIONS` not granted | Notification never appears; no change |
| App killed | `replay = 1` ensures the notification tap survives initialisation race |
| Multiple notification taps | `launchSingleTop` deduplicates navigation |

### Testing

1. **`ImportNotificationHelperTest`** (new): Robolectric. For each of
   `createForegroundInfo`, `postCompletionNotification`,
   `postFailureNotification`:
   - `targetNovelId != null` → `contentIntent` has `EXTRA_DEEP_LINK_ACTION`.
   - `targetNovelId == null` → `contentIntent` is launch intent without extras.
2. **`LibraryViewModelTest`** (extended): SavedStateHandle with
   `selectedNovelId=1, showFailed=true` → `scrollToFailedRequest.value == 1L`.
3. **`DeepLinkBusTest`** (extended): Verify `OpenFailedChapters` emits and
   reaches subscriber.

### Manual verification

```
adb logcat | grep -E "WM-|Notification|DeepLink"
```

- Trigger import → tap progress notification → library opens with novel
  selected.
- Trigger failure → tap failure notification → library opens, Chapters tab,
  auto-scrolled to Failed chapters section.
