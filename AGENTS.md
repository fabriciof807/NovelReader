# NovelReader - Project Context for AI Agents

## Overview

NovelReader is an offline-first Android novel reader. It imports HTML/MHT files from local storage or fetches chapters from web novel sites. All data stays on the device.

## Tech Stack

- Kotlin 2.2.10, AGP 9.2.1, JVM 17
- Jetpack Compose (BOM 2024.12.01) + Material3
- Room 2.8.4 (SQLite, FTS4 for full-text search)
- Hilt 2.59.2 (DI with multibinding for parsers)
- Jsoup 1.22.1 (HTML parsing)
- Coil 2.7.0 (image loading)
- DataStore 1.1.3 (preferences)
- WorkManager (background chapter imports)
- KSP 2.3.9 (annotation processing)
- Min SDK 26, Target SDK 34, Compile SDK 35

## Project Structure

```
app/src/main/java/com/novelreader/
  MainActivity.kt          -- Single Activity entry point
  NovelReaderApp.kt        -- @HiltAndroidApp, WorkManager config
  di/                      -- Hilt modules (Database, Parser, Storage, Work, Dispatchers)
  data/
    local/db/              -- Room database, DAOs, entities, migrations
    local/preferences/     -- DataStore preferences (App, Reader, Import, Library)
    parser/                -- HTML/MHT parsers (FreeWebNovel, ReadNovelFull, Generic, MHT)
    repository/            -- Repository wrappers over DAOs
    storage/               -- CoverStorage (local file I/O)
    remote/                -- MvlempyrCharacterImporter (WordPress API)
    worker/                -- WorkManager workers for background imports
  domain/usecase/          -- Business logic (ImportNovel, WebImport, BackgroundImportManager)
  ui/
    navigation/NavGraph.kt -- 6 routes: library, import, reader, favorites, settings, about
    library/               -- Library screen with tabs (novels, chapters, characters)
    reader/                -- WebView-based reader with bookmarks, search, settings
    import_novel/          -- Local file import screen
    webimport/             -- Web import ViewModel
    favorites/             -- Bookmarks screen
    settings/              -- Theme, language, queue mode
    about/                 -- App info
    theme/                 -- Colors, Typography, Theme composable
  util/                    -- LocaleHelper
```

## Architecture

MVVM + Repository + UseCase + Hilt DI

```
Compose -> ViewModel -> UseCase -> Repository -> Room DAO
                           |-> Parser (Set<NovelParser> via multibinding)
```

- ViewModels use @ApplicationContext (no leaks), errorEvents (SharedFlow), injected dispatchers (@IoDispatcher)
- Parsers use Hilt multibinding (@Binds @IntoSet) with GenericFallbackParser as catch-all
- Database has 6 entities, 5 DAOs, FTS4 virtual table, 7 migration versions

## Build Commands

```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :app:installDebug           # Install on connected device
./gradlew :app:testDebugUnitTest      # Run unit tests (JVM)
./gradlew :app:connectedDebugAndroidTest  # Run instrumented tests (emulator)
./gradlew :app:compileDebugKotlin     # Compile only (fast check)
```

## Code Conventions

- Kotlin official style
- No comments unless requested
- Single-Activity architecture with Jetpack Navigation Compose
- StateFlow for ViewModel state exposure
- Hilt for all dependency injection
- Room for all persistence
- DataStore for preferences (not SharedPreferences)
- Jsoup for HTML parsing
- Coroutines + Flow for async operations

## Key Patterns

### Parser Multibinding
Parsers are bound via `@Binds @IntoSet` in `ParserModule`. `ParserRegistry` dispatches to the correct parser by domain, falling back to `GenericFallbackParser`.

### Background Import
`ImportWorkScheduler` -> `ChapterImportWorker` -> `WebImportUseCase`. Supports SEQUENTIAL (queue-based) and PARALLEL modes. `WorkCompletionObserver` tracks progress and triggers next job.

### Database Migrations
Manual migrations in `NovelDatabase.Companion`. Always test migrations with Room testing APIs.

### Preferences
4 DataStore instances: app_prefs, reader_prefs, import_prefs, library_prefs. Each with its own preferences class.

## Testing

- Unit tests: Robolectric, MockK, Turbine, MockWebServer
- Instrumented tests: Room in-memory DB, Compose Test Rule, Espresso
- Parser tests use real HTML fixtures
- Always run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` before pushing
