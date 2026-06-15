# NovelReader

Offline-first Android novel reader. Import HTML/MHT files from local storage, fetch chapters from web novel sites, manage character cards, full-text search, and customize your reading experience — all data stays on your device.

## Features

- **Local Import** — Import HTML/MHT files via Storage Access Framework
- **Web Import** — Fetch chapters from supported sites with automatic chapter detection, retry, and exponential backoff
- **Reader** — WebView-based reader with theme, font size, and line height controls
- **Full-Text Search** — FTS4-powered search across all chapters
- **Bookmarks** — Save reading positions with scroll restore
- **Character Cards** — Manage characters with photos, notes, and favorites
- **Library Management** — Sort by title, date, or last read; light/dark theme
- **Background Imports** — WorkManager-powered imports with progress notifications
- **Auto-Update** — Periodic check for new chapters on supported sites
- **100% Offline** — No tracking, all data local

## Screenshots

| Library | Reader | Characters |
|---|---|---|
| ![Library](screenshots/library.png) | ![Reader](screenshots/reader.png) | ![Characters](screenshots/characters.png) |

## Tech Stack

| Component | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.2.1 |
| Compose BOM | 2024.12.01 |
| Hilt | 2.59.2 |
| Room | 2.8.4 |
| KSP | 2.3.9 |
| Jsoup | 1.22.1 |
| Coil | 2.7.0 |
| DataStore | 1.1.3 |
| WorkManager | 2.10.0 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| JVM | 17 |

## Architecture

MVVM + Repository + UseCase + Hilt DI with unidirectional data flow:

```
Compose -> ViewModel -> UseCase -> Repository -> Room DAO
                           |-> Parser (Set<NovelParser> via multibinding)
```

- ViewModels use `@ApplicationContext` (no leaks), `errorEvents` (SharedFlow), and injected dispatchers (`@IoDispatcher`)
- Parsers use Hilt multibinding (`@Binds @IntoSet`) with `GenericFallbackParser` as catch-all
- Background imports use WorkManager with SEQUENTIAL (queue) or PARALLEL modes

For detailed architecture documentation, see [docs/architecture.md](docs/architecture.md).

## Project Structure

```
app/src/main/java/com/novelreader/
  MainActivity.kt            Single Activity entry point
  NovelReaderApp.kt          @HiltAndroidApp, WorkManager config
  di/                        Hilt modules (Database, Parser, Storage, Work, Dispatchers)
  data/
    local/db/                Room database, DAOs, entities, migrations
    local/preferences/       DataStore preferences (App, Reader, Import, Library)
    parser/                  HTML/MHT parsers (FreeWebNovel, ReadNovelFull, Generic, MHT)
    repository/              Repository wrappers over DAOs
    storage/                 CoverStorage (local file I/O)
    remote/                  MvlempyrCharacterImporter (WordPress API)
    worker/                  WorkManager workers for background imports
  domain/usecase/            Business logic (ImportNovel, WebImport, BackgroundImportManager)
  ui/
    navigation/NavGraph.kt   6 routes: library, import, reader, favorites, settings, about
    library/                 Library screen with tabs (novels, chapters, characters)
    reader/                  WebView-based reader with bookmarks, search, settings
    import_novel/            Local file import screen
    webimport/               Web import ViewModel
    favorites/               Bookmarks screen
    settings/                Theme, language, queue mode
    about/                   App info
    theme/                   Colors, Typography, Theme composable
  util/                      LocaleHelper
```

## Supported Sites

| Site | Parser |
|---|---|
| FreeWebNovel | `FreeWebNovelParser` |
| ReadNovelFull | `ReadNovelFullParser` |
| Any HTML page | `GenericFallbackParser` |

## Build

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Install on connected device
./gradlew :app:installDebug

# Compile only (fast check)
./gradlew :app:compileDebugKotlin
```

## Testing

```bash
# Unit tests (JVM, no emulator needed)
./gradlew :app:testDebugUnitTest

# Instrumented tests (requires emulator/device)
./gradlew :app:connectedDebugAndroidTest

# Run before pushing
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

| Suite | Type | Count |
|---|---|---|
| Parsers | Unit | 40 |
| Repositories | Unit | 30 |
| ViewModels | Unit | 17 |
| Use Cases | Unit | 13 |
| DAOs | Instrumented | 30 |
| UI Screens | Instrumented | 6 |
| E2E / Regression | Both | 18 |
| **Total** | | **~154** |

For detailed test documentation, see [README-TESTES.md](README-TESTES.md).

## Database

Room database (v7) with 6 entities, 5 DAOs, and FTS4 full-text search:

```
novels (1) --< (N) chapters
chapters (1) --< (N) bookmarks
novels (1) --< (N) characters
characters (1) --< (N) character_photos
chapters_fts (FTS4 virtual table on chapters.title, chapters.content)
```

## i18n

Locales: `pt` (default) and `en`. Selection persisted in DataStore via `AppPreferences`. Locale change triggers activity recreation.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

## License

MIT — see [LICENSE](LICENSE).
