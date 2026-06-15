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

### DAOs (Instrumented)
```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.novelreader.data.local.db.*
```

### Repositories (Unit - Robolectric)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.data.repository.*"
```

### Parsers (Unit)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.data.parser.*"
```

### ViewModels (Unit)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.ui.favorites.FavoritesViewModelTest"
```

### WebImportUseCase (Unit + MockWebServer)
```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.novelreader.domain.usecase.WebImportUseCaseTest"
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
- Use Room in-memory database for persistence tests
- Use MockK for dependency mocking
- Use Turbine for StateFlow testing
- Use MockWebServer for HTTP integration tests
- Run on JVM (no emulator required)

### Instrumented Tests (`src/androidTest/`)
- Run on device or emulator
- Use Room in-memory database
- Use Compose Test Rule for UI tests
- Use AndroidX Test for test orchestration
- Verify real Android framework behavior

## Test Coverage Summary

| Area | Suite | Tests | Location |
|---|---|---|---|
| Chapter Repository | Unit | 12 | `test/.../data/repository/ChapterRepositoryTest.kt` |
| Bookmark Repository | Unit | 8 | `test/.../data/repository/BookmarkRepositoryTest.kt` |
| Character Repository | Unit | 10 | `test/.../data/repository/CharacterRepositoryTest.kt` |
| Mht Parser | Unit | 5 | `test/.../data/parser/MhtParserTest.kt` |
| WebImport UseCase | Unit | 10 | `test/.../domain/usecase/WebImportUseCaseTest.kt` |
| Favorites ViewModel | Unit | 6 | `test/.../ui/favorites/FavoritesViewModelTest.kt` |
| Edge Cases / Regression | Unit | 8 | `test/.../regression/EdgeCaseTest.kt` |
| Bookmark DAO | Instrumented | 8 | `androidTest/.../data/local/db/BookmarkDaoTest.kt` |
| Character DAO | Instrumented | 10 | `androidTest/.../data/local/db/CharacterDaoTest.kt` |
| Character Photo DAO | Instrumented | 6 | `androidTest/.../data/local/db/CharacterPhotoDaoTest.kt` |
| E2E Flow | Instrumented | 6 | `androidTest/.../data/local/db/E2EFlowTest.kt` |
| Library Screen UI | Instrumented | 3 | `androidTest/.../ui/library/LibraryScreenTest.kt` |
| Favorites Screen UI | Instrumented | 3 | `androidTest/.../ui/favorites/FavoritesScreenTest.kt` |
| Edge Cases / Regression | Instrumented | 4 | `androidTest/.../regression/EdgeCaseTest.kt` |
| **Total** | | **~99** | |

## Notes

- MockWebServer serves real HTTP responses for `WebImportUseCaseTest` integration
- Tests are independent (fresh database per test class)
- English test data used throughout
- FTS4 queries require exact match syntax (`"term"*`)
- Some tests verify Resource strings via `ApplicationProvider` context
