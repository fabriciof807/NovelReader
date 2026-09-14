# NovelReader

**A novel reader for Android that works 100% offline.**

Import novels from the web or from HTML/MHT files you already have, read comfortably, bookmark where you stopped, search across all chapters, and keep character sheets — all without needing internet after import, no signup, no tracking, no cloud.

> Portuguese version: [README_PT.md](README_PT.md)

---

## What it's for

NovelReader is for people who read a lot of web novels / light novels and want to:

- **Read offline** — import once, read anywhere (subway, plane, no signal). Chapters stay on your device.
- **Not depend on a specific site** — if the site goes down, changes its URL, or puts up a paywall, your library stays intact.
- **Manage a large library** — full-text search, sort by title / date / last read, filter by status, chapter and bookmark counts.
- **Keep track of characters** — for novels with many characters (xianxia, fantasy), create sheets with photos, notes, and favorites.
- **Customize the reading experience** — light or dark theme, font size, line height, auto-scroll.
- **Not be tracked** — no analytics, no account, no proprietary server. Everything is local.

---

## What you can do

### Import novels

- **From the web** — paste a novel URL from a supported site; the app discovers the chapter list, downloads the content, and organizes everything. Downloads run in the background with a progress notification.
- **From local files** — pick HTML or MHT files using Android's file picker. Useful for novels you've already saved from elsewhere.
- **Auto-update** — novels with a source URL can be checked periodically; new chapters are downloaded on their own.

### Read

- Light or dark theme
- Adjustable font size, line height, and auto-scroll
- Each chapter remembers where you stopped (even if you kill the app)
- **Full-text search** — find a word or phrase across all chapters of a novel
- Bookmarks with notes — mark important passages

### Organize characters

For novels with many characters, each novel has a **Characters** tab where you can:
- Create sheets with name, photo, notes
- Add multiple photos per character (gallery)
- Favorite the main ones
- Import ready-made sheets from the MVLEMPYR site (a character database partnership)

### Check what went wrong

When an import fails (URL down, error page instead of content, corrupted file), the app:
- Saves the failed chapter in the database
- Shows it in the novel's "Failed chapters" tab
- Lets you **retry the download** (if the URL is back) or **import an MHT file manually** for that chapter
- Also detects chapters with empty or missing content (by scanning for chapter numbers)

---

## How to use

1. **Install the APK** (see the build section at the bottom or download a release)
2. **Open the app** — the home screen shows your library
3. **Tap the "+"** to import a novel
4. **Choose the source**:
   - **Web**: paste the novel URL → select the chapters → import starts
   - **Files**: select HTML/MHT files from your device
5. **Tap the novel** in the library to see its chapters
6. **Tap a chapter** to start reading
7. **Use the chapter menu** to favorite, mark as read, or view bookmarks

The **Characters** tab appears when you select a novel. The **Favorites** tab (in the top menu) shows all your bookmarks in one place.

---

## Privacy

- No analytics, telemetry, or tracking
- No account or login
- No proprietary server — the app sends nothing anywhere
- Web imports use only HTTPS and the URL you provide
- All data (novels, chapters, bookmarks, characters, photos) stays on your device

---

## Languages

Portuguese (default) and English. Configurable in **Settings**.

---

## Screenshots

| Library | Reader | Characters |
|---|---|---|
| ![Library](screenshots/library.png) | ![Reader](screenshots/reader.png) | ![Characters](screenshots/characters.png) |

---

## Supported web import sites

- **FreeWebNovel**
- **ReadNovelFull**
- **Any HTML page** (generic parser)

For novels from sites not listed, the generic parser tries to extract the main content. It works well on sites with a simple structure.

---

## Version history

### v2.4.3 (2026-06-26)

UI/UX polish and infrastructure:

- **Reader**: chapter list in the bottom sheet now wraps to 4 lines (was 1)
- **Library**: empty state with illustration and "Add your first novel" CTA
- **Library**: "Reading" badge now shows relative time (e.g. "Lendo · há 2 h")
- **Library** and **Chapters**: scroll position is remembered between visits (per-novel for the chapters tab)
- **Personagens tab**: ExtendedFAB with labels for Add and Import actions
- **A11y**: contentDescription audit of 42 icon-only buttons (0 functional changes needed)
- **Haptics**: light haptic feedback on bookmark add, FAB tap, and tab switch
- **i18n**: complete English translations (all pt-BR keys mirrored in `values-en`)
- **Dynamic color**: opt-in toggle in Settings (Android 12+)
- **Tab transitions**: 220ms slide between library, reader, and settings
- **Test infrastructure**: Compose UI test base (Robolectric)

No data migration required.

---

## What's next

See `git log` for the history of architectural improvements and the `handoff-*.md` files (gitignored) for the current project state.

---

# Technical section

<details>
<summary>Details for developers</summary>

## Stack

| Component | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.2.1 |
| Jetpack Compose (BOM) | 2024.12.01 |
| Material 3 | (via Compose BOM) |
| Hilt | 2.59.2 |
| Room | 2.8.4 |
| KSP | 2.3.9 |
| Jsoup | 1.23.2 |
| Coil | 2.7.0 |
| DataStore | 1.1.3 |
| WorkManager | 2.10.0 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Compile SDK | 35 |
| JVM | 17 |

## Architecture

MVVM + UseCase + Hilt DI, with unidirectional flow:

```
Compose -> ViewModel -> UseCase -> DAO
                        |-> Parser (Set<NovelParser> via Hilt multibinding)
```

- ViewModels expose `StateFlow` for UI state and `SharedFlow<String>` for errors
- `ChapterFetcher` and parsers are injected via multibinding; `GenericFallbackParser` is the catch-all
- Background imports via WorkManager (SEQUENTIAL and PARALLEL modes)
- `DeepLinkBus` connects update notifications to navigation
- Room v8 database with 7 entities, 5 DAOs, FTS4 for search
- `ChapterOrderNormalizer` reorders chapters by number after import
- `RetryChapterUseCase` and `ScanMissingChaptersUseCase` for failure recovery

## Project structure

```
app/src/main/java/com/novelreader/
  MainActivity.kt                       Single Activity; handles deep links
  NovelReaderApp.kt                     @HiltAndroidApp, WorkManager config
  di/                                   Hilt modules (Database, Parser, Storage, Work, Dispatchers)
  data/
    local/db/                           Room: 7 entities, 5 DAOs, FTS4, 8 migrations
    local/preferences/                  DataStore (App, Reader, Import, Library)
    parser/                             HTML/MHT parsers (Hilt multibinding)
    storage/                            CoverStorage (file I/O)
    remote/                             MvlempyrCharacterImporter
    worker/                             WorkManager workers
  domain/usecase/                       Business logic
    webimport/                          ChapterCrawler, ChapterFetcher, CoverDownloader, NovelImporter
    importnovel/                        FileCharsetDetector, NovelGrouper, ChapterSorter, ChapterInserter
    RetryChapterUseCase, ScanMissingChaptersUseCase, ChapterOrderNormalizer
  ui/
    navigation/                         NavGraph + DeepLinkBus
    library/                            Tabs: novels, chapters, characters
    reader/                             WebView with bookmarks and search
    import_novel/                       Local import screen
    webimport/                          Web import screen
    favorites/                          All bookmarks
    settings/                           Theme, language, queue mode
    theme/                              Colors, typography
  util/                                 LocaleHelper
```

## Build

```bash
./gradlew :app:assembleDebug            # Debug APK
./gradlew :app:installDebug             # Install on connected device
./gradlew :app:compileDebugKotlin       # Compile only (fast)
```

## Tests

```bash
./gradlew :app:testDebugUnitTest            # JVM tests (no emulator needed)
./gradlew :app:connectedDebugAndroidTest    # Instrumented tests (emulator needed)
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest  # Before committing
```

| Suite | Type | ~Count |
|---|---|---|
| Parsers | Unit | 40 |
| ViewModels | Unit | 17 |
| Use Cases | Unit | 13 |
| E2E / Regression | Unit | 18 |
| DAOs | Instrumented | 30 |
| UI Screens | Instrumented | 6 |
| **Total** | | **~124** |

More details in [`README-TESTES.md`](README-TESTES.md).

## Database

Room v8. 7 entities (`Novel`, `Chapter`, `Bookmark`, `Character`, `CharacterPhoto`, `FailedChapter` + `ChapterFts`). 8 manual migrations. FTS4 over `chapters.title` and `chapters.content`.

## i18n

`pt` (default) and `en`. Configurable at runtime via `AppPreferences` (DataStore). Language switch recreates the Activity.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for detailed guidelines.

## License

MIT — see [`LICENSE`](LICENSE).

</details>
