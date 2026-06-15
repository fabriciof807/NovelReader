# NovelReader Architecture

## Overview

NovelReader follows MVVM + Repository + UseCase + Hilt DI architecture with unidirectional data flow. The app is offline-first — all data is persisted locally using Room and DataStore.

## High-Level Architecture

```
+-------------------+
|    Compose UI     |
+-------------------+
         |
+-------------------+
|    ViewModels     |  @HiltViewModel, StateFlow, errorEvents (SharedFlow)
+-------------------+
         |
+-------------------+
|    Use Cases      |  @Singleton, business logic
+-------------------+
         |
+-------------------+
|   Repositories    |  @Singleton, thin wrappers over DAOs
+-------------------+
         |
+-------------------+
|   Room Database   |  Entities, DAOs, FTS4, Migrations
+-------------------+
```

## Layers

### UI Layer (`ui/`)

Single-Activity architecture with Jetpack Navigation Compose. Each screen is a `@Composable` function backed by a `@HiltViewModel`.

**Screens:**
| Screen | ViewModel | Description |
|---|---|---|
| `LibraryScreen` | `LibraryViewModel` | Novel grid/list, chapters, characters tabs |
| `ReaderScreen` | `ReaderViewModel` | WebView reader with bookmarks, search |
| `ImportScreen` | `ImportViewModel` | Local file import via SAF |
| `WebImportViewModel` | (shared) | Web chapter import flow |
| `FavoritesScreen` | `FavoritesViewModel` | All bookmarks across novels |
| `SettingsScreen` | `SettingsViewModel` | Theme, language, queue mode |
| `AboutScreen` | (none) | App info |

**ViewModel Patterns:**
- Expose state via `StateFlow`
- Use `errorEvents: SharedFlow<String>` for error propagation
- Inject dispatchers via `@IoDispatcher`, `@DefaultDispatcher`
- Use `@ApplicationContext` (not Activity) to avoid leaks

### Domain Layer (`domain/usecase/`)

Business logic classes annotated with `@Singleton`. Each use case handles a single responsibility.

| Use Case | Responsibility |
|---|---|
| `ImportNovelUseCase` | Import chapters from local HTML/MHT files |
| `WebImportUseCase` | Fetch and parse chapters from web URLs |
| `BackgroundImportManager` | Orchestrate background imports via WorkManager |
| `ImportJobSpec` | Define import job parameters with batch chunking |

**ImportNovelUseCase flow:**
1. Read content from URI (charset detection: BOM, meta charset, XML encoding)
2. Parse HTML or fall back to MHT parsing
3. Group files by novel title (extracted from parsed content)
4. Deduplicate by filename
5. Sort by extracted chapter number
6. Insert new chapters and update order indices

**WebImportUseCase flow:**
1. Fetch novel homepage (HTTPS only)
2. Extract chapter links by keyword patterns
3. Paginate up to 50 pages if needed
4. Download and parse each chapter with retry + exponential backoff
5. Save cover image
6. Insert chapters into database

### Data Layer (`data/`)

#### Room Database (`data/local/db/`)

Version 7 with 6 entities, 5 DAOs, and FTS4 full-text search.

**Schema:**
```
novels (1) --< (N) chapters
chapters (1) --< (N) bookmarks
novels (1) --< (N) characters
characters (1) --< (N) character_photos
chapters_fts (FTS4 virtual table)
```

**Entities:**

| Entity | Table | Key Fields |
|---|---|---|
| `NovelEntity` | `novels` | id, title, author, coverPath, sourceFolder, sourceUrl, autoUpdate |
| `ChapterEntity` | `chapters` | id, novelId (FK), title, fileName, orderIndex, content, isRead, lastScrollPosition |
| `BookmarkEntity` | `bookmarks` | id, chapterId (FK), title, note, scrollPosition |
| `CharacterEntity` | `characters` | id, novelId (FK), name, photoPath, notes, isFavorite |
| `CharacterPhotoEntity` | `character_photos` | id, characterId (FK), photoPath, orderIndex |
| `ChapterFts` | `chapters_fts` | FTS4 virtual table on title + content |

**Migrations:**
| Migration | Changes |
|---|---|
| 1 -> 2 | Create core tables (novels, chapters, bookmarks) |
| 2 -> 3 | Add characters table |
| 3 -> 4 | Add character_photos table |
| 4 -> 5 | Add notes, isFavorite to characters |
| 5 -> 6 | Create FTS4 virtual table |
| 6 -> 7 | Add sourceUrl, lastCheckedAt, autoUpdate to novels |

**DAOs:**
| DAO | Key Operations |
|---|---|
| `NovelDao` | CRUD, sorted by lastReadAt, auto-update queries |
| `ChapterDao` | CRUD, FTS4 search via JOIN, order normalization |
| `BookmarkDao` | CRUD, by chapter or all |
| `CharacterDao` | CRUD, favorites-first sort, toggle favorite |
| `CharacterPhotoDao` | CRUD, by character |

#### Preferences (`data/local/preferences/`)

4 DataStore instances, each with its own preferences class:

| DataStore | Class | Stores |
|---|---|---|
| `app_prefs` | `AppPreferences` | Theme ("system"/"light"/"dark"), locale ("pt"/"en") |
| `reader_prefs` | `ReaderPreferences` | Font size (20), font family ("serif"), line height (1.8f), reader theme |
| `import_prefs` | `ImportPreferences` | QueueMode (SEQUENTIAL/PARALLEL), pending import queue (JSON) |
| `library_prefs` | `LibraryPreferences` | Sort order, view mode (GRID/LIST) |

#### Parsers (`data/parser/`)

HTML content extraction using Jsoup. Parser selection via Hilt multibinding.

**Interface:**
```kotlin
interface NovelParser {
    fun canParse(domain: String): Boolean
    fun parse(doc: Document, fileName: String): ParsedChapter
}
```

**Implementations:**

| Parser | Domain | Strategy |
|---|---|---|
| `FreeWebNovelParser` | freewebnovel.com | `div.chapter-content` |
| `ReadNovelFullParser` | readnovelfull.com | `div#chr-content` |
| `GenericFallbackParser` | any | Multiple CSS selectors with fallback chain |
| `MhtParser` | local MHT files | MIME boundary parsing, quoted-printable/base64 decoding |

**Registry flow:**
1. `ParserRegistry` receives `Set<NovelParser>` via multibinding
2. For web imports: match by domain, fallback to `GenericFallbackParser`
3. For local imports: detect MHT vs HTML, route accordingly

**HTML Sanitization:**
`HtmlSanitizer` removes dangerous elements (script, style, iframe, form), strips event handlers, removes `<a>` tags (unwrap), and cleans image sources.

#### Repositories (`data/repository/`)

Thin wrappers over DAOs. `ChapterRepository` adds `reNormalizeOrderIndices` (sort by extracted chapter number) and `searchInNovel` (FTS4 query builder).

#### Cover Storage (`data/storage/`)

`CoverStorageImpl` saves cover images from URI or HTTPS URL (max 10MB) to `filesDir/covers/novel_{id}.jpg`. Handles deletion of covers and character photo folders.

#### Remote (`data/remote/`)

`MvlempyrCharacterImporter` imports character cards from a WordPress REST API endpoint. Scrapes the novel page for a BookId, fetches characters, downloads design images, and inserts into database.

### Background Workers (`data/worker/`)

WorkManager-powered background processing.

**Import Flow:**
```
ImportWorkScheduler
  -> ImportPreferences (queue) or direct WorkManager enqueue
  -> ChapterImportWorker (foreground, with notifications)
    -> WebImportUseCase.importChapters()
  -> WorkCompletionObserver (tracks progress, triggers next job)
```

**Queue Modes:**
- SEQUENTIAL: Jobs queued in ImportPreferences, processed one at a time via WorkCompletionObserver
- PARALLEL: Jobs directly enqueued as independent OneTimeWorkRequests

**Workers:**
| Worker | Type | Purpose |
|---|---|---|
| `ChapterImportWorker` | CoroutineWorker | Foreground import with progress notifications |
| `ChapterUpdateCheckWorker` | PeriodicWorker | Check for new chapters (every 6 hours) |

**Notifications:**
- `ImportNotificationHelper`: Progress bar with cancel action, completion/failure notifications
- `UpdateNotificationHelper`: "New chapters available" with deep-link intent
- `ImportCancelReceiver`: BroadcastReceiver for notification cancel actions

## Dependency Injection (`di/`)

Hilt modules provide all dependencies:

| Module | Provides |
|---|---|
| `DatabaseModule` | Room database singleton, all 5 DAOs, migrations |
| `ParserModule` | Parsers via `@Binds @IntoSet` multibinding |
| `StorageModule` | `CoverStorage` implementation |
| `WorkModule` | WorkManager singleton |
| `DispatchersModule` | `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher` |

**Qualifiers:**
```kotlin
@Qualifier annotation class IoDispatcher
@Qualifier annotation class DefaultDispatcher
@Qualifier annotation class MainDispatcher
```

## Navigation (`ui/navigation/`)

Single-Activity with Jetpack Navigation Compose:

| Route | Screen | Parameters |
|---|---|---|
| `library` | LibraryScreen | (start destination) |
| `import` | ImportScreen | — |
| `reader/{novelId}/{chapterId}` | ReaderScreen | searchQuery (optional) |
| `favorites` | FavoritesScreen | — |
| `settings` | SettingsScreen | — |
| `about` | AboutScreen | — |

## Theme (`ui/theme/`)

Material3 theming with light/dark support:
- `Color.kt`: Color palette for light and dark themes
- `Type.kt`: Serif font family for titles and body
- `Theme.kt`: `NovelReaderTheme` composable with system/light/dark mode, status bar color matching

## Localization (`util/`)

- `LocaleHelper`: Applies saved locale via `createConfigurationContext`
- Default locale: `pt` (Portuguese)
- Supported: `pt`, `en`
- Locale persisted in DataStore `AppPreferences` and synced to SharedPreferences for locale helper access
- Language change triggers `activity.recreate()`

## Security

- HTTPS-only for web imports (enforced in `WebImportUseCase`)
- Network security config disables cleartext HTTP
- HTML sanitization removes XSS vectors (script, event handlers, javascript: URLs)
- User certificates only in debug builds
