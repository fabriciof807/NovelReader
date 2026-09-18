# Contributing to NovelReader

Thank you for helping improve NovelReader. It is an offline-first Android reader built with Kotlin and Jetpack Compose.

## Before you start

- Search [existing issues](https://github.com/fabriciof807/NovelReader/issues) before opening a new one.
- For a substantial feature or architecture change, open an issue first so the approach can be discussed.
- Keep each pull request focused on one fix or feature.

## Prerequisites

- JDK 17
- Android Studio Ladybug (2024.2) or later
- Android SDK Platform 37
- An emulator or device for instrumented tests

## Set up the project

```bash
git clone https://github.com/fabriciof807/NovelReader.git
cd NovelReader
./gradlew :app:assembleDebug
```

Run the regular verification suite before opening a pull request:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Run instrumented tests when your change needs a device, Android framework behaviour, Room integration, or Compose UI coverage:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Development workflow

1. Create a branch from `main` (or fork the repository if you do not have write access).
2. Make the smallest change that solves the issue.
3. Format Kotlin with Android Studio's built-in formatter and follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
4. Add or update tests for changed behaviour.
5. Run the relevant verification commands.
6. Open a pull request against `main`.

## Project conventions

- Prefer immutable `val` values and Kotlin's official style.
- Keep comments for non-obvious decisions; let clear names and structure explain routine code.
- Keep UI state in `StateFlow`, inject dependencies with Hilt, and call DAOs through use cases where the existing feature follows that pattern. Do not add a repository layer solely as a pass-through.
- Add a new website parser through `NovelParser`/`AbstractNovelParser`, bind it with `@Binds @IntoSet` in `ParserModule`, and add real HTML fixtures and parser tests.
- All production programmatic remote reads must use `HttpClient` and its request policies. Do not add `java.net.URL`, `openConnection`, or `Jsoup.connect` for remote reads.

### Database changes

For a new entity or persisted field:

1. Update the Room entity and `NovelDatabase`.
2. Add and register a manual migration in `NovelDatabase.Companion`.
3. Export and commit the updated schema JSON in `app/schemas/`.
4. Add migration and DAO tests as appropriate.

Use `./gradlew :app:exportSchema` when schema export is needed.

### UI changes

- Put screens and components in the existing feature-oriented `ui/` structure.
- Use `@HiltViewModel` for screen state and add routes in `Routes` and `NovelReaderNavGraph` when navigation changes.
- Include screenshots in the pull request when the visible UI changes.
- Test accessibility labels and state semantics for icon-only controls and custom controls.

## Pull request checklist

- [ ] The PR targets `main` and has a concise description.
- [ ] It is limited to one coherent change.
- [ ] Tests cover changed behaviour and the relevant Gradle commands pass.
- [ ] Room schemas and migrations are included when persistence changes.
- [ ] UI changes include screenshots where practical.
- [ ] No secrets, keystores, local data, generated APKs, or build output are included.

## Reporting bugs

Open an issue with steps to reproduce, expected and actual behaviour, device/Android version, and relevant logs with sensitive data removed.

## Reporting security vulnerabilities

Do not open a public issue for a suspected vulnerability. Instead, use a [private GitHub security advisory](https://github.com/fabriciof807/NovelReader/security/advisories/new) with reproduction steps and impact. Do not include credentials, tokens, private keys, or personal data.

## License

By contributing, you agree that your contribution is licensed under the repository's [MIT License](LICENSE).
