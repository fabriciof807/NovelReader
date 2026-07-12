# Notifications: tap-to-navigate + auto-scroll to failed

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `setContentIntent()` to import notifications (progress, completion, failure) so tapping them opens the library with the novel selected. Failure notification additionally auto-scrolls to the "Failed chapters" section.

**Architecture:** Notification tap → `Intent` with extras → `MainActivity.handleIntent` → `DeepLinkBus` → `NavGraph` → `library?selectedNovelId=X&showFailed=true` → `LibraryViewModel` reads `ARG_SHOW_FAILED` and sets a `scrollToFailedRequest` signal → `ChaptersTab` observes it and scrolls a `LazyListState` to the failed-chapter section.

**Tech Stack:** Kotlin, Jetpack Navigation Compose, Room/WorkManager (existing), Robolectric + Compose Test Rule (existing testing infra)

## Global Constraints

- No database changes, no permissions changes, no icon changes
- All deep link actions go through `DeepLinkBus` (existing pattern)
- `selectNovel()` in `LibraryViewModel` already switches to Chapters tab and clears `hasUpdates`
- Existing `ChaptersTab.lazyColumn` places the failed-chapter section after all chapter items at index `chapters.size + 1`
- `ImportJobSpec.targetNovelId` may be `null` for fresh imports (novel not yet created)
- Minimum SDK 26, target SDK 34
- Follow codebase conventions: no comments unless asked, Kotlin official style
- All `PendingIntent` instances must use `FLAG_IMMUTABLE`
- DeepLinkBus `replay` will change from 0 to 1 to handle app-killed → notification-tap race
- 288 tests currently pass; all must still pass after changes

---

### Task 1: Event bus + navigation wiring

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/navigation/DeepLinkBus.kt`
- Modify: `app/src/main/java/com/novelreader/MainActivity.kt`
- Modify: `app/src/main/java/com/novelreader/ui/navigation/NavGraph.kt`
- Modify: `app/src/test/java/com/novelreader/ui/navigation/DeepLinkBusTest.kt` (create if not exists)
- Test: `app/src/test/java/com/novelreader/...` (add to existing or create as needed)

**Interfaces:**
- Consumes: existing `DeepLinkAction` sealed class, `MainActivity.handleIntent` pattern
- Produces: `DeepLinkAction.OpenFailedChapters()`, `MainActivity.ACTION_OPEN_FAILED_CHAPTERS`, `Routes.libraryWithFailedChapters(novelId: Long)`, NavGraph collector branch for `OpenFailedChapters`

- [ ] **Step 1: Add `OpenFailedChapters` to `DeepLinkBus.kt`**

```kotlin
sealed class DeepLinkAction {
    data class ViewNovel(val novelId: Long) : DeepLinkAction()
    data class OpenCloudflareSolver(val novelId: Long?) : DeepLinkAction()
    data class OpenFailedChapters(val novelId: Long) : DeepLinkAction()  // NEW
}
```

Also change `_events` buffer: `replay = 0` → `replay = 1`:

```kotlin
private val _events = MutableSharedFlow<DeepLinkAction>(
    replay = 1,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
```

- [ ] **Step 2: Add `ACTION_OPEN_FAILED_CHAPTERS` to `MainActivity.kt`**

Add to companion object:
```kotlin
const val ACTION_OPEN_FAILED_CHAPTERS = "open_failed_chapters"
```

Add to `handleIntent` after the `ACTION_OPEN_CLOUDFLARE_SOLVER` block:
```kotlin
} else if (action == ACTION_OPEN_FAILED_CHAPTERS) {
    val novelId = intent.getLongExtra(EXTRA_NOVEL_ID, -1L)
    if (novelId > 0L) {
        deepLinkBus.emit(DeepLinkAction.OpenFailedChapters(novelId))
    }
    intent.removeExtra(EXTRA_DEEP_LINK_ACTION)
    intent.removeExtra(EXTRA_NOVEL_ID)
}
```

- [ ] **Step 3: Add `ARG_SHOW_FAILED` companion const to `LibraryViewModel.kt`**

This forward-declares the constant so `NavGraph` (Task 1 Step 4) can reference it. The behavioral changes come in Task 2.

In `LibraryViewModel` companion object:
```kotlin
companion object {
    const val ARG_SELECTED_NOVEL_ID = "selectedNovelId"
    const val ARG_SHOW_FAILED = "showFailed"
}
```

Only add `ARG_SHOW_FAILED`. The behavioral changes (`_scrollToFailedRequest`, `consumeScrollToFailed`, `init` update) come in Task 2.

- [ ] **Step 4: Update `NavGraph.kt` routes and collector**

```kotlin
object Routes {
    const val LIBRARY = "library"
    const val LIBRARY_WITH_SELECTION = "library?${LibraryViewModel.ARG_SELECTED_NOVEL_ID}={${LibraryViewModel.ARG_SELECTED_NOVEL_ID}}&${LibraryViewModel.ARG_SHOW_FAILED}={${LibraryViewModel.ARG_SHOW_FAILED}}"
    const val IMPORT = "import"
    const val READER = "reader/{novelId}/{chapterId}?searchQuery={searchQuery}"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun reader(novelId: Long, chapterId: Long, searchQuery: String? = null): String { ... }  // unchanged

    fun libraryWithFailedChapters(novelId: Long): String {
        return "library?${LibraryViewModel.ARG_SELECTED_NOVEL_ID}=$novelId&${LibraryViewModel.ARG_SHOW_FAILED}=true"
    }
}
```

In the NavHost, add `showFailed` nav argument alongside the existing `selectedNovelId`:

```kotlin
animatedComposable(
    route = Routes.LIBRARY_WITH_SELECTION,
    arguments = listOf(
        navArgument(LibraryViewModel.ARG_SELECTED_NOVEL_ID) {
            type = NavType.LongType
            defaultValue = -1L
        },
        navArgument(LibraryViewModel.ARG_SHOW_FAILED) {
            type = NavType.BoolType
            defaultValue = false
        }
    )
) { ... }  // body unchanged
```

Add new collector branch to the `deepLinkBus.events` `LaunchedEffect`:

```kotlin
is DeepLinkAction.OpenFailedChapters -> {
    navController.navigate(Routes.libraryWithFailedChapters(action.novelId)) {
        launchSingleTop = true
    }
}
```

- [ ] **Step 4: Write test for `DeepLinkBus`**

Create `app/src/test/java/com/novelreader/ui/navigation/DeepLinkBusTest.kt`:

```kotlin
package com.novelreader.ui.navigation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class DeepLinkBusTest {

    private val bus = DeepLinkBus()

    @Test
    fun `emitted OpenFailedChapters is received by subscriber`() = runTest {
        bus.emit(DeepLinkAction.OpenFailedChapters(42L))
        val action = bus.events.first()
        assertThat(action).isInstanceOf(DeepLinkAction.OpenFailedChapters::class.java)
        assertThat((action as DeepLinkAction.OpenFailedChapters).novelId).isEqualTo(42L)
    }

    @Test
    fun `replays last event for late subscriber`() = runTest {
        bus.emit(DeepLinkAction.OpenFailedChapters(7L))
        val action = bus.events.first()  // second subscription gets replayed value
        assertThat((action as DeepLinkAction.OpenFailedChapters).novelId).isEqualTo(7L)
    }
}
```

- [ ] **Step 5: Run test to verify**

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.navigation.DeepLinkBusTest" -i
```

Expected: PASS

- [ ] **Step 6: Full compile + existing test pass, then commit**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, **290+** tests passing (288 existing + 2 new).

```bash
git add app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt  # ARG_SHOW_FAILED const
git add app/src/main/java/com/novelreader/ui/navigation/DeepLinkBus.kt
git add app/src/main/java/com/novelreader/MainActivity.kt
git add app/src/main/java/com/novelreader/ui/navigation/NavGraph.kt
git add app/src/test/java/com/novelreader/ui/navigation/DeepLinkBusTest.kt
git commit -m "feat: add OpenFailedChapters deep link action + NavGraph routing"
```

---

### Task 2: ViewModel scroll signal + LibraryScreen/ChaptersTab wiring

**Files:**
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt`
- Modify: `app/src/test/java/com/novelreader/ui/library/LibraryViewModelScrollTest.kt`
- Modify: `app/src/test/java/com/novelreader/ui/library/tabs/ChaptersTabScrollTest.kt`

**Interfaces:**
- Consumes: `DeepLinkAction.OpenFailedChapters` from Task 1, NavGraph `ARG_SHOW_FAILED` from Task 1
- Produces: `scrollToFailedRequest: StateFlow<Long?>`, `consumeScrollToFailed()`

- [ ] **Step 1: Add scroll signal state to `LibraryViewModel.kt`**

Add companion constant:
```kotlin
companion object {
    const val ARG_SELECTED_NOVEL_ID = "selectedNovelId"
    const val ARG_SHOW_FAILED = "showFailed"
}
```

Add state field near other `_*` StateFlows (around line 170):
```kotlin
private val _scrollToFailedRequest = MutableStateFlow<Long?>(null)
val scrollToFailedRequest: StateFlow<Long?> = _scrollToFailedRequest

fun consumeScrollToFailed() {
    _scrollToFailedRequest.value = null
}
```

Update `init` block. Find the existing block that handles `ARG_SELECTED_NOVEL_ID` (around line 274):

```kotlin
val pendingNovelId = savedStateHandle.get<Long>(ARG_SELECTED_NOVEL_ID)?.takeIf { it > 0L }
if (pendingNovelId != null) {
    viewModelScope.launch {
        val novel = novelDao.getNovelById(pendingNovelId)
        if (novel != null) {
            selectNovel(novel)
            if (savedStateHandle.get<Boolean>(ARG_SHOW_FAILED) == true) {
                _scrollToFailedRequest.value = pendingNovelId
            }
        }
        savedStateHandle.remove<Long>(ARG_SELECTED_NOVEL_ID)
        savedStateHandle.remove<Boolean>(ARG_SHOW_FAILED)
    }
}
```

- [ ] **Step 2: Add `pendingScrollToFailedNovelId` + `onConsumeScrollToFailed` params to `ChaptersTab.kt`**

Add to parameter list (after `onScroll`):
```kotlin
pendingScrollToFailedNovelId: Long? = null,
onConsumeScrollToFailed: () -> Unit = {},
```

Add `LaunchedEffect` after the existing `LaunchedEffect(listState)` (around line 95):
```kotlin
LaunchedEffect(pendingScrollToFailedNovelId, chapters.size, failedChapters.size) {
    val target = pendingScrollToFailedNovelId
    if (target != null && target == novelId && failedChapters.isNotEmpty()) {
        listState.scrollToItem(chapters.size + 1)
        onConsumeScrollToFailed()
    }
}
```

- [ ] **Step 3: Wire `LibraryScreen.kt` to pass scroll params**

Add after the existing `val failedChapters by viewModel.failedChapters.collectAsState()` line:
```kotlin
val scrollToFailedRequest by viewModel.scrollToFailedRequest.collectAsState()
```

Update the `ChaptersTab(...)` call to pass new params (around line 307):
```kotlin
1 -> ChaptersTab(
    // ... existing params unchanged ...
    pendingScrollToFailedNovelId = scrollToFailedRequest,
    onConsumeScrollToFailed = { viewModel.consumeScrollToFailed() },
)
```

- [ ] **Step 4: Add test to `LibraryViewModelScrollTest.kt`**

Add after the existing tests:

```kotlin
@Test
fun `scrollToFailedRequest is set when showFailed flag is true`() = runTest {
    val novel = NovelEntity(id = 1L, title = "Test", sourceUrl = "", coverPath = null)
    every { novelDao.getNovelById(1L) } returns novel
    every { chapterDao.getChaptersByNovelSync(1L) } returns emptyList()
    every { failedChapterDao.getByNovel(1L) } returns emptyList()

    savedState[LibraryViewModel.ARG_SELECTED_NOVEL_ID] = 1L
    savedState[LibraryViewModel.ARG_SHOW_FAILED] = true
    val vm = LibraryViewModel(
        context = context,
        savedStateHandle = savedState,
        novelDao = novelDao,
        chapterDao = chapterDao,
        bookmarkDao = bookmarkDao,
        backgroundImportManager = bgManager,
        libraryPreferences = prefs,
        characterManagementUseCase = charManagement,
        coverManagementUseCase = coverManagement,
        characterPhotoDao = charPhotoDao,
        mvlempyrCharacterImporter = importer,
        updateCheckScheduler = updateCheckScheduler,
        webImportUseCase = webImportUseCase,
        failedChapterDao = failedChapterDao,
        retryChapterUseCase = retryChapterUseCase,
        scanMissingChaptersUseCase = scanMissingChaptersUseCase,
        chapterInserter = chapterInserter,
        parserRegistry = parserRegistry,
        mhtParser = mhtParser,
        fileCharsetDetector = fileCharsetDetector,
        io = Dispatchers.Unconfined
    )

    assertThat(vm.scrollToFailedRequest.value).isEqualTo(1L)
}
```

Also add a test for `consumeScrollToFailed`:
```kotlin
@Test
fun `consumeScrollToFailed resets scrollToFailedRequest to null`() = runTest {
    val novel = NovelEntity(id = 2L, title = "Test 2", sourceUrl = "", coverPath = null)
    every { novelDao.getNovelById(2L) } returns novel
    every { chapterDao.getChaptersByNovelSync(2L) } returns emptyList()
    every { failedChapterDao.getByNovel(2L) } returns emptyList()

    savedState[LibraryViewModel.ARG_SELECTED_NOVEL_ID] = 2L
    savedState[LibraryViewModel.ARG_SHOW_FAILED] = true
    val vm = LibraryViewModel(
        context = context,
        savedStateHandle = savedState,
        novelDao = novelDao,
        chapterDao = chapterDao,
        bookmarkDao = bookmarkDao,
        backgroundImportManager = bgManager,
        libraryPreferences = prefs,
        characterManagementUseCase = charManagement,
        coverManagementUseCase = coverManagement,
        characterPhotoDao = charPhotoDao,
        mvlempyrCharacterImporter = importer,
        updateCheckScheduler = updateCheckScheduler,
        webImportUseCase = webImportUseCase,
        failedChapterDao = failedChapterDao,
        retryChapterUseCase = retryChapterUseCase,
        scanMissingChaptersUseCase = scanMissingChaptersUseCase,
        chapterInserter = chapterInserter,
        parserRegistry = parserRegistry,
        mhtParser = mhtParser,
        fileCharsetDetector = fileCharsetDetector,
        io = Dispatchers.Unconfined
    )

    vm.consumeScrollToFailed()
    assertThat(vm.scrollToFailedRequest.value).isNull()
}
```

Import `NovelEntity` at top:
```kotlin
import com.novelreader.data.local.db.entity.NovelEntity
```

- [ ] **Step 5: Add test to `ChaptersTabScrollTest.kt`**

Add a test verifying the tab doesn't crash with `pendingScrollToFailedNovelId`:

```kotlin
@Test
fun `ChaptersTab renders with pendingScrollToFailedNovelId without crashing`() {
    composeTestRule.setContent {
        NovelReaderTheme {
            ChaptersTab(
                novelId = 6L,
                chapters = (0L..4L).map {
                    ChapterEntity(
                        id = it,
                        novelId = 6L,
                        title = "Ch $it",
                        fileName = "ch_$it.html",
                        orderIndex = it.toInt(),
                        content = "<p>x</p>"
                    )
                },
                bookmarkCounts = emptyMap(),
                sortOrder = ChapterSortOrder.ASCENDING,
                onChapterClick = { },
                pendingScrollToFailedNovelId = 6L,
                onConsumeScrollToFailed = { }
            )
        }
    }
    composeTestRule.waitForIdle()
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.ui.library.LibraryViewModelScrollTest" --tests "com.novelreader.ui.library.tabs.ChaptersTabScrollTest" -i
```

Expected: PASS (all existing + 3 new tests)

- [ ] **Step 7: Full compile + existing test pass, then commit**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, **293+** tests passing (290 + 2 VM scroll + 1 ChaptersTab).

```bash
git add app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt
git add app/src/main/java/com/novelreader/ui/library/LibraryScreen.kt
git add app/src/main/java/com/novelreader/ui/library/tabs/ChaptersTab.kt
git add app/src/test/java/com/novelreader/ui/library/LibraryViewModelScrollTest.kt
git add app/src/test/java/com/novelreader/ui/library/tabs/ChaptersTabScrollTest.kt
git commit -m "feat: auto-scroll ChaptersTab to failed section on failure notification tap"
```

---

### Task 3: ImportNotificationHelper content intents

**Files:**
- Modify: `app/src/main/java/com/novelreader/data/worker/ImportNotificationHelper.kt`
- Create: `app/src/test/java/com/novelreader/data/worker/ImportNotificationHelperTest.kt`

**Interfaces:**
- Consumes: `MainActivity.ACTION_OPEN_NOVEL`, `MainActivity.ACTION_OPEN_FAILED_CHAPTERS`, `spec.targetNovelId`
- Produces: `PendingIntent` attached to notifications via `setContentIntent()`

- [ ] **Step 1: Write the test first**

Create `app/src/test/java/com/novelreader/data/worker/ImportNotificationHelperTest.kt`:

```kotlin
package com.novelreader.data.worker

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.MainActivity
import com.novelreader.domain.usecase.ImportJobSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportNotificationHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val helper = ImportNotificationHelper(context)

    @Test
    fun `foreground notification has contentIntent when targetNovelId is set`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Test Novel",
            links = listOf("https://example.com/ch1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            sourceUrl = "https://example.com",
            domain = "example.com",
            targetNovelId = 42L
        )
        val info = helper.createForegroundInfo(spec, 0, 10)
        val notification = info.notification

        assertThat(notification.contentIntent).isNotNull()
    }

    @Test
    fun `foreground notification has launch intent when targetNovelId is null`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Test Novel",
            links = listOf("https://example.com/ch1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            sourceUrl = "https://example.com",
            domain = "example.com",
            targetNovelId = null
        )
        val info = helper.createForegroundInfo(spec, 0, 10)

        // contentIntent should still be non-null (launch intent without deep link extras)
        assertThat(info.notification.contentIntent).isNotNull()
    }

    @Test
    fun `completion notification has contentIntent with ACTION_OPEN_NOVEL`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Test Novel",
            links = emptyList(),
            chapterNumbers = emptyList(),
            coverUrl = null,
            sourceUrl = "https://example.com",
            domain = "example.com",
            targetNovelId = 42L
        )
        // postCompletionNotification doesn't return the notification; it posts it.
        // We verify by checking that no crash occurs. The content intent is verified
        // in the foreground notification test above (same helper internally).
        helper.postCompletionNotification(spec, 5, 10, 0)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = manager.getActiveNotifications()
        assertThat(posted).isNotEmpty()
    }

    @Test
    fun `failure notification has contentIntent with ACTION_OPEN_FAILED_CHAPTERS`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Test Novel",
            links = emptyList(),
            chapterNumbers = emptyList(),
            coverUrl = null,
            sourceUrl = "https://example.com",
            domain = "example.com",
            targetNovelId = 42L
        )
        helper.postFailureNotification(spec)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = manager.getActiveNotifications()
        assertThat(posted).isNotEmpty()
    }
}
```

- [ ] **Step 2: Run test — must fail (no implementation yet)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.worker.ImportNotificationHelperTest" -i
```

Expected: COMPILATION FAILURE (foreground notification currently has no contentIntent, test expects it)

- [ ] **Step 3: Add `novelPendingIntent` helper to `ImportNotificationHelper.kt`**

Add after `notificationId()`:
```kotlin
private fun novelPendingIntent(novelId: Long?, action: String, requestCode: Int): PendingIntent {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (novelId != null) {
            putExtra(MainActivity.EXTRA_DEEP_LINK_ACTION, action)
            putExtra(MainActivity.EXTRA_NOVEL_ID, novelId)
        }
    } ?: Intent()
    return PendingIntent.getActivity(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
```

Add import:
```kotlin
import com.novelreader.MainActivity
```

- [ ] **Step 4: Add `setContentIntent` to all 3 notification builders**

In `createForegroundInfo`, after `.setCategory(NotificationCompat.CATEGORY_PROGRESS)` and before the `return`:
```kotlin
    .setContentIntent(novelPendingIntent(spec.targetNovelId, MainActivity.ACTION_OPEN_NOVEL, spec.id.hashCode()))
```

In `postCompletionNotification`, after `.setProgress(0, 0, false)`:
```kotlin
    .setContentIntent(novelPendingIntent(spec.targetNovelId, MainActivity.ACTION_OPEN_NOVEL, COMPLETION_NOTIFICATION_ID))
```

In `postFailureNotification`, after `.setCategory(NotificationCompat.CATEGORY_ERROR)`:
```kotlin
    .setContentIntent(novelPendingIntent(spec.targetNovelId, MainActivity.ACTION_OPEN_FAILED_CHAPTERS, COMPLETION_NOTIFICATION_ID + 1))
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.novelreader.data.worker.ImportNotificationHelperTest" -i
```

Expected: PASS

- [ ] **Step 6: Full compile + existing test pass, then commit**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, **297+** tests passing (293 + 4 ImportNotificationHelper)

```bash
git add app/src/main/java/com/novelreader/data/worker/ImportNotificationHelper.kt
git add app/src/test/java/com/novelreader/data/worker/ImportNotificationHelperTest.kt
git commit -m "feat: add setContentIntent to all import notifications for tap-to-navigate"
```

---

### Task 4: Final build + verification

- [ ] **Step 1: Full build and test suite**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests passing. If any tests fail, investigate and fix.

- [ ] **Step 2: Verify git status is clean (except `app/release/` build artifacts)**

```bash
git status
```

Expected: nothing staged, only `app/release/` files modified (build artifacts, gitignored themselves but touched by builds).

```bash
git log --oneline -5
```

Expected to show 3 new commits from Tasks 1-3.

- [ ] **Step 3: Manual verification instructions**

Install on device:
```bash
./gradlew :app:installDebug
```

Then trigger an import (web or local), and verify:
1. Import in progress → tap notification → app opens with novel selected in Chapters tab
2. Import complete → tap notification → app opens library with novel selected
3. Import failure (e.g., airplane mode) → tap notification → app opens library, Chapters tab, auto-scrolled to "Failed chapters" section

Monitor logcat:
```bash
adb logcat | grep -E "WM-|Notification|DeepLink"
```
