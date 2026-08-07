# NovelReader - Project Context for AI Agents

## Overview

NovelReader (v2.5.4) is an offline-first Android novel reader. It imports HTML/MHT files from local storage or fetches chapters from web novel sites. All data stays on the device.

The app is end-user focused: 100% offline, no analytics, no account, no cloud.

## Tech Stack

- Kotlin 2.2.10, AGP 9.2.1, JVM 17
- Jetpack Compose (BOM 2024.12.01) + Material3
- Room 2.8.4 (SQLite, FTS4 for full-text search) — **v8** (7 entities, 5 DAOs + FailedChapterDao)
- Hilt 2.59.2 (DI with multibinding for parsers)
- Jsoup 1.22.1 (HTML parsing)
- Coil 2.7.0 (image loading)
- DataStore 1.1.3 (preferences)
- WorkManager 2.10.0 (background chapter imports)
- KSP 2.3.9 (annotation processing)
- Min SDK 26, Target SDK 34, Compile SDK 35

## Project Structure

```
app/src/main/java/com/novelreader/
  MainActivity.kt          -- Single Activity entry point; deep link handling
  NovelReaderApp.kt        -- @HiltAndroidApp, WorkManager config
  di/                      -- Hilt modules (Database, Parser, Storage, Work, Dispatchers)
  data/
    local/db/              -- Room database v8: 7 entities, 5 DAOs, 8 migrations, FTS4
      entity/              -- NovelEntity, ChapterEntity, ChapterFts, BookmarkEntity,
                              CharacterEntity, CharacterPhotoEntity, FailedChapterEntity
      dao/                 -- NovelDao, ChapterDao, BookmarkDao, CharacterDao,
                              CharacterPhotoDao, FailedChapterDao
      FtsSearchService.kt  -- FTS4 search with FTS-syntax escaping
    local/preferences/     -- DataStore (AppPreferences, ReaderPreferences, ImportPreferences, LibraryPreferences)
    parser/                -- HTML/MHT parsers via Hilt multibinding (FreeWebNovel, ReadNovelFull, Generic, MhtParser)
    storage/               -- CoverStorage (local file I/O)
    remote/                -- MvlempyrCharacterImporter (WordPress API)
    worker/                -- WorkManager workers (ChapterImportWorker, ChapterUpdateCheckWorker, etc.)
  domain/usecase/          -- Business logic
    webimport/             -- ChapterCrawler, ChapterFetcher, CoverDownloader, NovelImporter
    importnovel/           -- FileCharsetDetector, NovelGrouper, ChapterSorter, ChapterInserter
    BackgroundImportManager -- Tracks import state, queue, cancel per novel (via ImportPreferences.getJobsByNovelTitle)
    RetryChapterUseCase    -- Re-fetches failed chapter by URL
    ScanMissingChaptersUseCase -- Scans for missing/empty chapters (web via re-crawl, local via range)
    ChapterOrderNormalizer -- Re-orders chapters by extracted number
    CharacterManagementUseCase, CoverManagementUseCase, ExportDataUseCase, ImportDataUseCase
    ReimportChapterContentUseCase -- Parses MHT/HTML and updates existing chapter content
  ui/
    navigation/NavGraph.kt -- 6 routes; library accepts optional selectedNovelId arg
    navigation/DeepLinkBus.kt -- SharedFlow connecting MainActivity intent handling to NavGraph
    library/               -- Library screen with tabs (novels, chapters, characters)
      tabs/                  -- LibraryTab, ChaptersTab, PersonagensTab
      components/            -- NovelCard, NovelListItem, CharacterCard, ScanRangeDialog, DeleteDialogs
      mvi/                   -- LibraryState
    reader/                -- WebView-based reader with bookmarks, FTS search, settings,
                               EmptyChapterState (MHT recovery), auto-hide controls
    import_novel/          -- Local file import screen
    webimport/             -- Web import ViewModel
    favorites/             -- Bookmarks screen
    settings/              -- Theme, language, queue mode, JSON import/export
    about/                 -- App info
    theme/                 -- Colors, Typography, Theme composable
  util/                    -- LocaleHelper
```

## Architecture

MVVM + UseCase + Hilt DI, with unidirectional data flow:

```
Compose -> ViewModel -> UseCase -> DAO
                        |-> Parser (Set<NovelParser> via multibinding)
                        |-> ChapterFetcher (Hilt-injectable)
```

### Key decisions

- **No repository layer** (removed in v2.2.0). Callers inject DAOs directly. Deletion test confirmed repositories were pure pass-throughs.
- **`ChapterOrderNormalizer`** owns the `ChapterNumberExtractor`-based sort. Used by `ChapterImportWorker` and `NovelImporter` (web).
- **`RetryChapterUseCase`** uses the Hilt-exposed `ChapterFetcher` to re-fetch a single chapter by URL after a failure.
- **`ScanMissingChaptersUseCase`** compares an expected chapter range (web: re-crawl; local: user range) against the DB. Also flags chapters with empty/short content.
- **`DeepLinkBus`** is a `SharedFlow<DeepLinkAction>` injected into `MainActivity` and `NavGraph` for cross-component event delivery (notification taps, future actions).

### ViewModel conventions

- `@ApplicationContext` for Context (no leaks)
- `errorEvents: SharedFlow<String>` with `BufferOverflow.DROP_OLDEST`
- Injected dispatchers via `@IoDispatcher` qualifier
- `StateFlow` for UI state, `MutableStateFlow` for internal
- ViewModels expose typed methods; the library screen calls them directly (no `*Intent` dispatcher)

### Database

- Room v8, 7 entities, 5 DAOs (NovelDao, ChapterDao, BookmarkDao, CharacterDao, CharacterPhotoDao, FailedChapterDao)
- 8 manual migrations, exported to `app/schemas/`
- `chapters_fts` virtual table (FTS4) over `chapters.title` and `chapters.content`
- FKs with `onDelete = CASCADE`; failed chapters deleted when novel is deleted

### Failed chapter capture (multi-pronged)

A chapter can fail in three ways, all persisted as `FailedChapterEntity`:

1. **Exception during import** — `WebImportUseCase` / `ImportNovelUseCase` catch network, parse, I/O errors and write a row with `errorType = network | parse | io`.
2. **Missing chapter number** — `ScanMissingChaptersUseCase` finds chapter numbers present in the source but absent in the DB. `errorType = missing_number`.
3. **Empty content** — Same scan flags chapters in the DB with `content.isBlank() || content.length < 200`. `errorType = empty_content`.

Users see these in the `ChaptersTab` "Failed chapters" section (below the chapter list) and can:
- Retry the URL (web only)
- Import an MHT file (manual file picker)
- Dismiss (delete the entry)

## Build Commands

```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :app:installDebug           # Install on connected device
./gradlew :app:testDebugUnitTest      # Run unit tests (JVM)
./gradlew :app:connectedDebugAndroidTest  # Run instrumented tests (emulator)
./gradlew :app:compileDebugKotlin     # Compile only (fast check)
./gradlew :app:exportSchema           # Export Room schema JSON
```

## Code Conventions

- Kotlin official style
- **No comments unless requested**
- Single-Activity architecture with Jetpack Navigation Compose
- `StateFlow` for ViewModel state exposure
- Hilt for all dependency injection
- Room for all persistence
- DataStore for preferences (not SharedPreferences)
- Jsoup for HTML parsing
- Coroutines + Flow for async operations
- PT-BR comments where unavoidable; strings always bilingual (pt + en)
- ViewModels expose typed methods called directly by Compose (no `*Intent` sealed dispatch)

## Key Patterns

### Parser Multibinding

Parsers are bound via `@Binds @IntoSet` in `ParserModule`. `ParserRegistry` dispatches to the correct parser by domain, falling back to `GenericFallbackParser`.

### Background Import

`ImportWorkScheduler` -> `ChapterImportWorker` -> `WebImportUseCase.importChapters`. Supports SEQUENTIAL (queue-based) and PARALLEL modes via `ImportPreferences`. `WorkCompletionObserver` tracks progress and triggers next job. `WorkManager` shows a foreground notification during import. `BackgroundImportManager` tracks import state, queue, cancel per novel (via `ImportPreferences.getJobsByNovelTitle`).

### Failed Chapter Recovery

`ScanMissingChaptersUseCase` is the primary recovery path:
- `scanWeb(novelId)` re-crawls `novel.sourceUrl` to get the expected chapter list
- `scanLocal(novelId, from, to)` scans a user-provided range
- Both flag missing numbers and empty content
- Triggered from a button in `ChaptersTab`'s failed-chapters section header
- `ChapterInserter.insertEntries` deletes any matching `FailedChapterEntity` by `fileName` on successful insert (handles re-import via MHT/URL)

### Deep Linking

`MainActivity` reads `Intent` extras (`EXTRA_DEEP_LINK_ACTION`, `EXTRA_NOVEL_ID`) in `onCreate` and `onNewIntent`, emits `DeepLinkAction` to the bus. `NavGraph` collects from the bus and navigates accordingly (e.g., notification tap → open library with novel selected).

### Database Migrations

Manual `Migration(start, end)` in `NovelDatabase.Companion`. Each uses raw `execSQL`. New entities / columns must have a matching schema JSON committed to `app/schemas/`. The DI module (`DatabaseModule`) wires them via `.addMigrations(...)`.

### Preferences

4 DataStore instances: `app_prefs`, `reader_prefs`, `import_prefs`, `library_prefs`. Each with its own preferences class.

### Theme

Custom Material 3 colors in `ui/theme/Color.kt` and `ui/theme/Theme.kt`. Light theme uses warm cream (`F5F0E8`) + warm white (`FFF8F0`); dark theme uses dark navy (`1A1A2E`) + dark blue (`16213E`). Primary is deep indigo; secondary is orange. `secondaryContainer` is overridden in both modes to match the orange palette (M3 defaults to pink in dark mode).

## Testing

- **Unit tests**: Robolectric, MockK, Turbine, MockWebServer (JVM, no emulator)
- **Instrumented tests**: Room in-memory DB, Compose Test Rule, Espresso
- Parser tests use real HTML fixtures
- ViewModel tests inject mocked DAOs/use cases
- **Current count: 322 unit tests** (305 baseline + 17 added in v2.5.3/v2.5.4)
- **Always run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` before pushing**

## Recent Sessions

See git log and `handoff-*.md` files for session handoffs. The handoff file is intentionally gitignored — it documents the active state across AI sessions.

Design specs and implementation plans from past AI sessions are preserved in git history; deferred follow-ups are tracked as GitHub issues.

## Current Version

v2.5.4 (versionCode 22). See [README.md](README.md) (English) and [README_PT.md](README_PT.md) (Portuguese) for the user-facing documentation. Full release history in `git log`.

### v2.5.4 highlights

- Fix: `BackgroundImportManager.cancel()` now cancels all splits for the current novel by title (not just one UUID), and clears stale progress callbacks by resetting `id=null` — no more progress bar reappearing after cancel.
- Fix: Import result dialog in Settings after JSON import shows queued novels, failures (with per-title list), and pending bookmarks/characters.
- Feat: `ImportPreferences.getJobsByNovelTitle` / `removeJobsByNovelTitle` for targeted queue management.
- 322+ unit tests passing (319 baseline + 3 new in SettingsViewModelTest).

### v2.5.3 highlights

- Feat: Novel card 3-dot buttons visible on NovelCard/NovelListItem (was only long-press).
- Feat: Empty chapter state in reader with MHT import recovery (ReimportChapterContentUseCase).
- Feat: Reader DB errors now surface via Snackbar with Retry action.
- Feat: Auto-hide reader controls after 4s of inactivity.
- Feat: "Chapters" option in the 3-dot novel menu.
- Fix: Remove SwipeToDismiss from CharacterCard (deleted without confirmation).
- Fix: Haptics standardized — LongPress only for real long-press, removed from taps and route changes.
- Fix: Cover URL dialog shows inline HTTPS error (stays open); file picker cancel clears request.
- New: `ReimportChapterContentUseCase`, `ChapterDao.updateContent`, `EmptyChapterState` composable.
- 319+ unit tests passing (was 305).

### v2.5.2 highlights

- Fix: `ChapterFetcher` no longer falls back to `chapterDoc.body().html()` when the parser returns empty content. That fallback was capturing the entire 404 page body into stored chapter rows (the "Novel list Your Library..." footer the user reported) and overriding the chapter title with the site suffix.
- Fix: `WebImportUseCase` now detects stale/404 content (empty, < 200 chars, or contains "Page not found"/"Not Found"/"404" markers) and refetches the chapter instead of skipping on fileName dedup. Users with v2.5.0/v2.5.1 broken chapters get a clean re-import on the next run.
- 272+ unit tests passing.

### v2.5.1 highlights

- Fix freewebnovel.com chapter import regression: the site redesigned to a new URL layout (`/novel/<slug>/chapter-N` instead of `/<slug>/chapter-N.html`) and the AJAX chapter-archive endpoint now requires the `articlevisited=1` cookie that the home page sets. The new `FreeWebNovelParser` uses the current selectors (`h1.tit`, `<title>`, `div.chapter-start` / `div.chapter-end`); `HttpClient` persists Set-Cookie across requests via the existing `CloudflareCookieJar` so the AJAX calls carry the cookie. 269/269 unit tests pass.

### v2.5.0 highlights

- readnovelfull.com now imports its full chapter list (200+ instead of 30) via per-domain `NovelListAugmenter` calling `/ajax/chapter-archive?novelId=N`.
- `FreewebnovelListAugmenter` refactored from the inline `ChapterCrawler` block into a Hilt-multibound class (no behavior change).
- Cross-site novel merge: import the same novel from multiple sites; chapters dedupe by chapter number; cover first-wins; one row per novel.
- New `novel_sources` table (Room v8→v9) holds one row per (novel, source) with per-source `lastCheckedAt` and `autoUpdate`.
- `ChapterUpdateCheckWorker` now iterates all `novel_sources` (multi-source auto-update).
- Blue-dot badge on `NovelCard`/`NovelListItem` when a novel has new chapters since last open; cleared on open.
- Generalised `CloudflareChallengeDialog` host check (parameter-driven) + `DataStoreCloudflareCookieStore` cross-host lookup.
- 263 unit tests passing (was 205).

### v2.4.0 highlights

- Fix #1 — Delete confirm dialog now reachable; `RequestDelete` intent path fixed (was reading dead `_state.value.novels`).
- Fix #2 — System back deselects the current novel via `BackHandler` instead of closing the app.
- Fix #3 — Personagens-tab FABs no longer overlap the last character card (140dp bottom contentPadding).
- Fix #4 — Wider default scan range using `novel.totalChapters`; local-scan `fileName` key pinned to `chapter_${n}` with a regression test.
- Fix #6 — Chapter titles in the list now wrap up to 4 lines (was 2).
- Fix #7 — Failed-chapter badge now renders correct labels for `missing_number` and `empty_content` (string resources already existed).
