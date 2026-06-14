# NovelReader
Offline-first Android novel reader. Import HTML/MHT files, fetch chapters from the web, manage character cards, full-text search, and customize your reading experience — all data stays on your device.

## Features
- Import HTML/MHT files via Storage Access Framework
- Web import with chapter detection, retry, and exponential backoff
- Reader with theme, font size, and line height controls
- Full-text search (Room FTS4) across all chapters
- Bookmarks with scroll position restore
- Character cards with photos, notes, and favorites
- Sort by title, date, or last read; light/dark theme
- 100% offline — no tracking, all data local

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
| Min/Target SDK | 26 / 34 |
| JVM | 17 |

## Architecture
MVVM + Repository + Use Case + Hilt DI.
ViewModels use `@ApplicationContext` (no leaks), `errorEvents` (SharedFlow), and injected dispatchers (`@IoDispatcher`).
Parsers via Hilt multibinding (`@Binds @IntoSet`) with generic fallback.

```
Compose → ViewModel → UseCase → Repository → Room DAO
                            └→ Parser (Set<NovelParser>)
```

Supported sites: FreeWebNovel, ReadNovelFull (generic fallback for others).

## Screenshots
<!-- Add screenshots to screenshots/ folder -->
| Library | Reader | Characters |
|---|---|---|
| ![Library](screenshots/library.png) | ![Reader](screenshots/reader.png) | ![Characters](screenshots/characters.png) |

## Build
```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## Testing
Unit (JVM): 54 tests
Instrumented: 9 tests

| Suite | Count |
|---|---|
| `data.parser.*` | 40 |
| `domain.usecase.BackgroundImportManagerTest` | 3 |
| `ui.library.LibraryViewModelTest` | 7 |
| `ui.reader.ReaderViewModelTest` | 4 |
| `androidTest.data.local.db.*DaoTest` | 9 |

## i18n
Locales: pt (default) and en. Selection persisted in DataStore via `AppPreferences`.

## Contributing
- Open an issue for substantial changes
- Follow Kotlin official style
- Add tests for new logic (parsers, ViewModels, DAOs)
- Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` before pushing

## License
MIT — see [LICENSE](LICENSE).