# NovelReader Test Suite

## Prerequisites

- Emulator or device connected for instrumented tests:
  ```bash
  adb devices
  # Example output: emulator-5554    device
  ```

## Running All Tests

```bash
# Unit tests (fast, no emulator needed)
./gradlew :app:testDebugUnitTest

# Instrumented tests (requires emulator)
./gradlew :app:connectedDebugAndroidTest
```

## Running Specific Suites

### Parsers (Unit)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.data.parser.*"
```

### ViewModels (Unit)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.ui.favorites.FavoritesViewModelTest" \
  --tests "com.novelreader.ui.library.LibraryViewModelTest" \
  --tests "com.novelreader.ui.reader.ReaderViewModelTest"
```

### Use Cases (Unit)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.domain.usecase.*"
```

### Workers (Unit)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.data.worker.*"
```

### UI Screens (Instrumented - Compose + Espresso)
```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.novelreader.ui.*
```

### Integration E2E Flow (Instrumented)
```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.novelreader.data.local.db.E2EFlowTest
```

### Edge Cases / Regression (Unit)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.regression.EdgeCaseTest"
```

### End-to-End (Unit)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.e2e.EndToEndTest"
```

### Edge Cases / Regression (Instrumented)
```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.novelreader.regression.*
```

## Test Reports

After running tests, open the reports in your browser:

- **Unit tests**: `app/build/reports/tests/testDebugUnitTest/index.html`
- **Instrumented tests**: `app/build/reports/androidTest/connected/index.html`

## Test Architecture

### Unit Tests (`src/test/`)
- Use Robolectric for Android context simulation
- Use MockK for dependency mocking
- Use Turbine for StateFlow / SharedFlow testing
- Use MockWebServer for HTTP integration tests
- Run on JVM (no emulator required)
- Coverage: parsers, ViewModels, use cases, workers, regression scenarios

### Instrumented Tests (`src/androidTest/`)
- Run on device or emulator
- Use Room in-memory database
- Use Compose Test Rule for UI tests
- Use AndroidX Test for test orchestration
- Verify real Android framework behavior
- Coverage: DAOs, Compose UI screens, full E2E import + read flow

## Test Coverage Summary (v2.4.0)

Unit tests: **104** total. Instrumented tests: see `app/src/androidTest/`.

| Area | Suite | Tests | Location |
|---|---|---|---|
| Mht Parser | Unit | 7 | `test/.../data/parser/MhtParserTest.kt` |
| FreeWebNovel Parser | Unit | 6 | `test/.../data/parser/FreeWebNovelParserTest.kt` |
| ReadNovelFull Parser | Unit | 8 | `test/.../data/parser/ReadNovelFullParserTest.kt` |
| Generic Fallback Parser | Unit | 6 | `test/.../data/parser/GenericFallbackParserTest.kt` |
| Parser Registry | Unit | 5 | `test/.../data/parser/ParserRegistryTest.kt` |
| HTML Sanitizer | Unit | 7 | `test/.../data/parser/HtmlSanitizerTest.kt` |
| Chapter Number Extractor | Unit | 9 | `test/.../data/parser/ChapterNumberExtractorTest.kt` |
| Library ViewModel | Unit | 10 | `test/.../ui/library/LibraryViewModelTest.kt` |
| Favorites ViewModel | Unit | 6 | `test/.../ui/favorites/FavoritesViewModelTest.kt` |
| Reader ViewModel | Unit | 4 | `test/.../ui/reader/ReaderViewModelTest.kt` |
| WebImport UseCase | Unit | 3 | `test/.../domain/usecase/WebImportUseCaseTest.kt` |
| Background Import Manager | Unit | 6 | `test/.../domain/usecase/BackgroundImportManagerTest.kt` |
| Import Job Spec | Unit | 5 | `test/.../domain/usecase/ImportJobSpecTest.kt` |
| Scan Missing Chapters UseCase | Unit | 2 | `test/.../domain/usecase/ScanMissingChaptersUseCaseTest.kt` |
| Import Work Request Factory | Unit | 2 | `test/.../data/worker/ImportWorkRequestFactoryTest.kt` |
| End-to-End (Unit) | Unit | 10 | `test/.../e2e/EndToEndTest.kt` |
| Edge Cases / Regression | Unit | 8 | `test/.../regression/EdgeCaseTest.kt` |
| **Unit total** | | **104** | |

## Notes

- MockWebServer serves real HTTP responses for `WebImportUseCaseTest` integration
- Tests are independent (fresh database per test class)
- English test data used throughout
- FTS4 queries require exact match syntax (`"term"*`)
- Some tests verify Resource strings via `ApplicationProvider` context
- The repository layer was removed in v2.2.0; ViewModels and use cases inject DAOs directly (no `data.repository.*` test suite exists)
