# Contributing to NovelReader

Thank you for considering contributing to NovelReader.

## Prerequisites

- JDK 17
- Android Studio Ladybug (2024.2+) or later
- Android SDK with API 34 and Build Tools 35
- Emulator or physical device for instrumented tests

## Setup

```bash
git clone https://github.com/your-username/android-book.git
cd android-book
./gradlew :app:assembleDebug
```

## Development Workflow

1. Create a feature branch from `main`
2. Make your changes
3. Run tests before committing:
   ```bash
   ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
   ```
4. Open a pull request

## Code Style

- Follow the [Kotlin官方 coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `ktfmt` or Android Studio's built-in formatter
- No comments in code unless explicitly requested
- Prefer `val` over `var`
- Use sealed classes for state representation

## Architecture Conventions

### Adding a New Parser

1. Create a class implementing `NovelParser` or extending `AbstractNovelParser`
2. Implement `canParse(domain: String): Boolean` and `parse(doc: Document, fileName: String): ParsedChapter`
3. Bind it in `ParserModule.kt` using `@Binds @IntoSet`:
   ```kotlin
   @Binds @IntoSet
   abstract fun bindMyParser(impl: MyParser): NovelParser
   ```
4. Add tests in `data/parser/` with real HTML fixtures

### Adding a New DAO

1. Define the DAO interface with Room annotations
2. Add the abstract method to `NovelDatabase`
3. Provide it in `DatabaseModule.kt`
4. Write instrumented tests in `androidTest/`

### Adding a New Screen

1. Create the screen composable in `ui/yourfeature/`
2. Create a ViewModel with `@HiltViewModel`
3. Add the route to `Routes` object in `NavGraph.kt`
4. Wire up navigation in `NovelReaderNavGraph`

### Adding a New Entity

1. Create the entity class with `@Entity` annotation
2. Add it to the `@Database` entities list in `NovelDatabase`
3. Create a migration in `NovelDatabase.Companion`
4. Increment the database version
5. Test the migration

## Testing Guidelines

- Unit tests: Use Robolectric for Android context, MockK for mocking, Turbine for Flow testing
- Instrumented tests: Use Room in-memory DB, Compose Test Rule for UI
- Parser tests: Use real HTML fixture files from `src/test/resources/`
- Always test edge cases (empty input, malformed HTML, network errors)

## Commit Messages

- Use present tense ("Add feature" not "Added feature")
- Keep first line under 72 characters
- Reference issues when applicable

## Pull Request Guidelines

- PR should target `main`
- Include a clear description of changes
- Add screenshots for UI changes
- Ensure all tests pass
- Keep PRs focused — one feature/fix per PR

## Reporting Issues

- Use GitHub Issues
- Include steps to reproduce
- Include device/OS version
- Attach logs if applicable
