# NovelReader UI/UX Polish + Notifications-deferred — Design

**Date:** 2026-06-25
**Target version:** v2.4.2 (versionCode 16) — patch
**Branch:** `main` (direct, one commit per ticket)
**Author of user-facing direction:** User instruction "foca em melhorias nas notificações e UI e UX" (notification workstream explicitly deferred to v2.5.0; this spec covers UI/UX only).
**Prior handoff:** `/tmp/opencode/handoff-20260625-notif-ui.md`

---

## Decisions

- **Scope:** polish + 2 small features (dynamic color opt-in, empty-state illustrations) + Compose UI test base infrastructure. No architectural changes; no new entities/migrations; no new third-party dependencies.
- **Decomposition:** per-feature tickets. 13 tickets total. Order: base infra → small visual fixes → structural changes → invisible-layer polish → version bump.
- **Compose UI test base:** `createComposeRule()` (Robolectric, no Activity) for pure UI tests. `createAndroidComposeRule<ComponentActivity>()` is not introduced in this pass; can be added later if any ticket needs Activity-scoped behavior.
- **Reader chapter list `maxLines`:** 1 → 4. Matches the value used by `ChaptersTab` chapter row (`ChaptersTab.kt:152`).
- **Scroll persistence:** backstack-only via `rememberSaveable(saver = LazyListState.Saver)` for the Library tab. For the Chapters tab, the `LazyListState` is hoisted into `LibraryViewModel` and persisted in `SavedStateHandle` keyed by `novelId` because the composable is destroyed on deselect. Survives rotation and nav round-trips. Does NOT survive a full process kill (user decision).
- **Dynamic color:** gated by `Build.VERSION.SDK_INT >= 31` AND `AppPreferences.dynamicColorEnabled` (default `true`). Fallback to current static palette if either condition fails.
- **Empty-state illustrations:** Material `ImageVector` inline (no asset files), tinted with `MaterialTheme.colorScheme.onSurfaceVariant`.
- **Haptics:** `LocalHapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)` invoked from a new `Modifier.hapticClickable` wrapper. Used on bookmark-add, FABs, and tab-switch. Never crashes if `LocalHapticFeedback` is `null`.
- **i18n:** pt-BR strings live in `values/strings.xml` (default), en in `values-en/strings.xml`. All 210 pt keys must have an en counterpart before merge. Note: `AGENTS.md` documents the convention inverted; this design follows the *actual* repo layout, not the docs.
- **`AGENTS.md` correction:** the line that says pt-BR lives in `values-pt-rBR/strings.xml` is wrong. A separate, small commit fixes that line and only that line in `AGENTS.md` to match the real layout.

---

## Architecture overview

Sixteen file changes, all inside the existing MVVM + Compose + Hilt + DataStore layout. No new modules. No new Gradle dependencies — all Material 3, DataStore, Compose foundation APIs, and `androidx.compose.material:material-icons-extended` (already declared via `material3` + `material.icons`) cover every change. Three new files: `ComposeUiTestBase.kt`, `LibraryEmptyState.kt`, `util/RelativeTime.kt`. One helper `Modifier` extension: `util/HapticClickable.kt`.

### State changes

- `AppPreferences`: new `dynamicColorEnabled: Flow<Boolean>` + `updateDynamicColorEnabled(Boolean)`. Default `true`.
- `LibraryViewModel`: new `ChaptersScrollState(val firstVisibleItemIndex: Int, val firstVisibleItemScrollOffset: Int)` data class; new `chaptersScrollByNovel: StateFlow<Map<Long, ChaptersScrollState>>` backed by `SavedStateHandle` under the key `"chapters_scroll"` (JSON-encoded). New methods `setChaptersScroll(novelId, index, offset)` and `getChaptersScroll(novelId)`. JSON encoding uses `kotlinx.serialization` (already a transitive dep via Hilt/Compose); if unavailable at runtime, fall back to two separate `intPreferencesKey`s per novel.
- `SettingsViewModel`: new `isImporting: StateFlow<Boolean>` set to `true` for the duration of `importSelected(titles)` and reset in `finally`. The "Importar X" button in the preview sheet reads it to swap the label for a `CircularProgressIndicator`.
- `SettingsViewModel` (preview helpers): new `selectAllImportTitles()` and `deselectAllImportTitles()` functions called by the new header row's two `TextButton`s.

### New files

- `app/src/test/java/com/novelreader/ui/test/ComposeUiTestBase.kt` — Robolectric + `createComposeRule()` test base. Provides `setNovelReaderContent(content: @Composable () -> Unit)`, `assertTextDisplayed(text)`, `assertTextNotDisplayed(text)`, `performClick(text)`, `assertFirstVisibleItem(index)`. Configured `@Config(sdk = [33], qualifiers = "w400dp-h800dp")`.
- `app/src/main/java/com/novelreader/ui/library/components/LibraryEmptyState.kt` — composable with `ImageVector` slot + headline + body + 2 secondary `OutlinedButton`s. Parameters: `onImportLocal: () -> Unit`, `onImportWeb: () -> Unit`.
- `app/src/main/java/com/novelreader/util/RelativeTime.kt` — `fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String?`. Returns "agora" (< 60 s), "há 5 min" (< 60 min), "há 2 h" (< 24 h), "há 3 dias" (< 30 d), or `null` (≥ 30 d or invalid). Pure function, no Android imports — easy to unit test.
- `app/src/main/java/com/novelreader/util/HapticClickable.kt` — `fun Modifier.hapticClickable(haptic: HapticFeedback?, enabled: Boolean = true, onClick: () -> Unit): Modifier`. Wraps `clickable` and fires a `LongPress` haptic before invoking `onClick`. Safe when `haptic` is `null`.

---

## Per-ticket

### 1. `chore: compose UI test base infra`
- **Files:** new `app/src/test/java/com/novelreader/ui/test/ComposeUiTestBase.kt`; verify `app/build.gradle.kts` has `testImplementation("androidx.compose.ui:ui-test-junit4")` and `testImplementation(platform(libs.compose.bom))` (already present, line 134-135 of build.gradle.kts).
- **Test:** 1 smoke test in `ComposeUiTestBaseSmokeTest.kt` — sets `Text("hello")` content, asserts `onNodeWithText("hello").assertIsDisplayed()`. Confirms the base works before downstream tests depend on it.
- **Risk:** none. Pure test infrastructure.

### 2. `fix: reader — chapter list removes 1-line cap`
- **Files:** `ui/reader/ReaderScreen.kt:246` — `maxLines = 1` → `maxLines = 4`. `overflow = TextOverflow.Ellipsis` retained.
- **Strings:** none new.
- **Test:** none. Visual constant change. Manual verification: open a novel, click the chapter-list button in the toolbar, confirm long titles wrap to 4 lines.

### 3. `feat: library — empty state with illustration`
- **Files:** new `ui/library/components/LibraryEmptyState.kt`; modify `ui/library/tabs/LibraryTab.kt` to render `LibraryEmptyState` when `chipFiltered.isEmpty() && chips.isEmpty() == false` (or unconditionally when `chipFiltered.isEmpty()` AND the user is in the "All" chip). Callbacks `onImportLocal` and `onImportWeb` thread through `LibraryScreen` to the existing import flows.
- **Strings:** new `library_empty_title` ("Adicione sua primeira novel"), `library_empty_body` ("Importe um arquivo local ou baixe da web"), `library_empty_import_local` ("Arquivo local"), `library_empty_import_web` ("Da web"). Bilingual.
- **Test:** Compose UI test `LibraryEmptyStateTest.kt` — renders the composable with mock callbacks, asserts all 3 texts displayed and 2 buttons clickable.

### 4. `feat: library — relative time in Reading badge`
- **Files:** new `util/RelativeTime.kt`; modify `ui/library/components/NovelCard.kt:156-169` and `NovelListItem.kt:84-89` to render `"Lendo · {rel}"` when `novel.lastReadAt != null`, else current behavior. The badge composable reads `formatRelativeTime(novel.lastReadAt!!)`; `null` is treated as "≥ 30 days old, omit suffix".
- **Strings:** new `library_reading_with_time` ("Lendo · %1$s") with the `%1$s` slot. Bilingual.
- **Test:** unit test `RelativeTimeTest.kt` — 6 cases: now, 5 min, 2 h, 3 d, 60 d → null, invalid (negative) → null.

### 5. `feat: personagens — ExtendedFAB with labels`
- **Files:** `ui/library/tabs/PersonagensTab.kt:239-255` — replace `SmallFloatingActionButton` + `FloatingActionButton` with `ExtendedFloatingActionButton` × 2.
- **Strings:** new `personagens_add_label` ("Adicionar"), `personagens_import_label` ("Importar"). Bilingual.
- **Test:** Compose UI test `PersonagensTabFabTest.kt` — renders with `onImportCharacters = { }`, asserts both labels visible. Skips if no character data wired (use `emptyList()` characters).

### 6. `feat: scroll — Library tab remembers position`
- **Files:** `ui/library/tabs/LibraryTab.kt` — `val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }` at composable scope; pass `state = listState` to the `LazyColumn`.
- **Strings:** none.
- **Test:** Compose UI test `LibraryTabScrollTest.kt` — render with 30 fake novels, scroll to index 15 via `state.scrollToItem(15)`, recompose (e.g., trigger a chip change and back), assert `listState.firstVisibleItemIndex == 15`.

### 7. `feat: scroll — Chapters tab per-novel remembers position`
- **Files:**
  - `ui/library/LibraryViewModel.kt` — add `ChaptersScrollState` data class, `chaptersScrollByNovel` StateFlow, `setChaptersScroll`, `getChaptersScroll`. Persist as a single JSON string in `SavedStateHandle`.
  - `ui/library/tabs/ChaptersTab.kt` — accept new params `initialScroll: ChaptersScrollState?` and `onScroll: (Int, Int) -> Unit`. Create `LazyListState(firstVisibleItemIndex = initialScroll?.firstVisibleItemIndex ?: 0, firstVisibleItemScrollOffset = initialScroll?.firstVisibleItemScrollOffset ?: 0)`. Inside `LaunchedEffect(listState)`, `snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.debounce(300).collect { onScroll(it.first, it.second) }`.
  - `ui/library/LibraryScreen.kt` — pass the current novel's `ChaptersScrollState` from the VM into `ChaptersTab` and wire the `onScroll` callback.
- **Strings:** none.
- **Test:**
  - Unit test `LibraryViewModelScrollTest.kt` — save scroll for novelId=1, assert `getChaptersScroll(1)` returns saved values, `getChaptersScroll(2)` returns null/default.
  - Compose UI test `ChaptersTabScrollTest.kt` — render with 30 fake chapters, scroll to index 12, deselect novel, reselect, assert position restored.

### 8. `feat: a11y — contentDescription audit`
- **Files:** audit the 39 hits found by `grep "contentDescription = null" app/src/main/java/com/novelreader/ui`. For each, decide:
  - **Decorative** (visual flourish, paired with adjacent text): keep `contentDescription = null`. Examples: `Icons.Default.Sort` inside a row that already has a sort-order label; the empty-photo camera icon in `CharacterPhotoPager`.
  - **Functional** (icon-only button or icon-only state indicator): add a `stringResource(R.string....)` with a new bilingual string. Examples: the failed-chapter `CloudOff` icon (add `failed_chapters_section_title_cd`); the sort toggle's `Sort` icon in `LibraryTab` filter row.
- **Strings:** add ~12-18 new bilingual labels (will be enumerated at ticket time, one `strings.xml` + `values-en/strings.xml` edit each).
- **Test:** none. A11y verification is human-driven (TalkBack on a real device or emulator). CI does not enforce.

### 9. `feat: haptics on primary actions`
- **Files:** new `util/HapticClickable.kt`; apply `Modifier.hapticClickable(...)` to:
  - `ui/library/tabs/PersonagensTab.kt` — both `ExtendedFloatingActionButton`s (wrapping their `onClick`)
  - `ui/reader/ReaderScreen.kt` — the "Add bookmark" `IconButton` at the bookmark-add dialog path
  - `ui/navigation/NavGraph.kt` — wrap the `NavHost` in a `BackHandler` or use `LocalHapticFeedback` in a `DisposableEffect` keyed by current route to fire a light haptic on tab switch.
- **Strings:** none.
- **Test:** none. Side-effect-only.

### 10. `i18n: complete en translations`
- **Files:** `app/src/main/res/values-en/strings.xml`. Read every `<string name="...">` from `app/src/main/res/values/strings.xml`, find missing in `values-en/`, add machine-translated entries (Google Translate via `curl` or local heuristic), and flag any uncertain translations for manual review in a follow-up commit.
- **Verification:** small unit test `I18nCoverageTest.kt` in `app/src/test/java/com/novelreader/i18n/` — reads both `strings.xml` files via `Resources`, asserts `enKeys == ptKeys` set-wise. This is a regression guard.
- **Tooling (optional, in same commit):** `tools/check_i18n.py` — parses both XMLs and prints missing keys. Not part of the build, just a developer aid.

### 11. `feat: dynamic color opt-in (Android 12+)`
- **Files:**
  - `data/local/preferences/AppPreferences.kt` — add `dynamicColorEnabled: Flow<Boolean>` (default `true`) and `updateDynamicColorEnabled(Boolean)` using a `booleanPreferencesKey("dynamic_color_enabled")`.
  - `ui/theme/Theme.kt` — accept new param `useDynamicColor: Boolean = true`. When `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor` and the resolved scheme is `light`, use `dynamicLightColorScheme(context)`; same for dark. Otherwise, fall through to the current static `lightColorScheme`/`darkColorScheme`.
  - `ui/theme/Theme.kt` (or wherever `NovelReaderTheme` is invoked): collect `AppPreferences.dynamicColorEnabled` from `LibraryViewModel.appPreferences` flow (or pass the preference down through `MainActivity`).
  - `ui/settings/SettingsScreen.kt` — add a `ListItem` with `Switch` for "Tema dinâmico (Android 12+)" between "Tema" and "Idioma" sections. Tied to `AppPreferences.updateDynamicColorEnabled`. Disabled with a helper text when `Build.VERSION.SDK_INT < 31`.
- **Strings:** new `settings_dynamic_color_title` ("Tema dinâmico (Android 12+)"), `settings_dynamic_color_subtitle` ("Usa as cores do seu sistema"). Bilingual.
- **Test:** unit test `AppPreferencesDynamicColorTest.kt` — Robolectric, write/read round-trip for the new key. Compose UI test skipped (snapshot).

### 12. `ui: tab transition animations`
- **Files:** `ui/navigation/NavGraph.kt` — for each `composable("route") { ... }` block, add:
  ```
  enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) }
  exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) }
  popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) }
  popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) }
  ```
  Apply uniformly to all 6 routes. Could be hoisted to a `NavGraphBuilder.extendedComposable(...)` helper to avoid repetition.
- **Strings:** none.
- **Test:** none. Visual.

### 13. `chore: bump version to v2.4.2 (versionCode 16)`
- **Files:** `app/build.gradle.kts` — `versionCode = 16`, `versionName = "2.4.2"`. Update both READMEs (`README.md` and `README_PT.md`) with a "v2.4.2" entry in the version history section (mirror the v2.4.0 / v2.4.1 entries).
- **Strings:** none.

### 14. `docs: fix AGENTS.md i18n convention note` (housekeeping)
- **Files:** `AGENTS.md` — correct the line that says pt-BR lives in `values-pt-rBR/strings.xml` to say "pt-BR in `values/strings.xml` (default), en in `values-en/strings.xml`". This is a one-line correction. Out of the v2.4.2 release notes; commits separately so it is not entangled with feature work.

---

## Testing strategy

- **Unit tests (JVM, Robolectric):** ~8 new tests across `RelativeTimeTest`, `LibraryViewModelScrollTest`, `AppPreferencesDynamicColorTest`, `I18nCoverageTest`, `ComposeUiTestBaseSmokeTest`. All use existing `testImplementation` deps (Robolectric, MockK, Truth, Turbine).
- **Compose UI tests (JVM, Robolectric + `createComposeRule`):** ~4 new tests across `LibraryEmptyStateTest`, `PersonagensTabFabTest`, `LibraryTabScrollTest`, `ChaptersTabScrollTest`. All use the new `ComposeUiTestBase`.
- **Total expected:** 138 + 12 ≈ 150 tests passing.
- **CI command** (per `AGENTS.md`): `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` must pass before any claim of completion.

---

## Risks and non-goals

### Risks

- **`SavedStateHandle` JSON size:** the chapters-scroll map is small (one entry per visited novel, ~16 bytes each). Even 50 novels ≈ 800 bytes — well under the 500 KB SavedStateHandle cap. Mitigation: trim entries older than 90 days at VM init (low priority; can be added if any user accumulates thousands).
- **Dynamic color visual regression:** on Android 12+ devices, the indigo+orange palette will be replaced by the user's wallpaper-derived scheme. This is the intended behavior but is a visible change. Mitigation: the toggle is in Settings; the user can revert to the static palette with one tap. Default = `true` per user direction; consider flipping to `false` in a v2.5.x patch if user feedback is negative.
- **Haptics on low-end devices:** `LocalHapticFeedback` is non-null on all modern devices but may be `null` in some Robolectric test contexts. The `?.` null-safety in `HapticClickable` covers this.
- **Tab transitions on slow devices:** 220 ms is a default; if perceived as slow, can drop to 180 ms. No code change needed (constant).

### Non-goals (deferred to v2.5.0)

- Notification group summary (`UpdateNotificationHelper.postNewChaptersNotification` currently sets `setGroup` but no summary is ever posted).
- Notification error channel (separate `IMPORTANCE_DEFAULT` channel for `postFailureNotification`).
- `POST_NOTIFICATIONS` rationale dialog (Android 13+).
- "Queued novel" indicator on the library (issue #5 from handoff 2026-06-23) — requires extending `BackgroundImportState` with a queue list.
- "Perdi meu lugar" toolbar discoverability (already in v2.4.1 via long-press; not revisiting).
- Bilingual content (book content itself, not UI).

---

## Open questions (for follow-up, not blocking)

1. **Default for `dynamicColorEnabled`:** chosen `true` here per user direction. Revisit in v2.5.0 if user feedback is negative.
2. **`Modifier.hapticClickable` vs raw `LocalHapticFeedback` calls at call sites:** the helper is cleaner but adds a small abstraction. Helper wins on readability.
3. **A11y audit enumeration:** the exact 12-18 new labels depend on a per-Icon review at ticket time. Spec is intentionally non-prescriptive here.

---

## Commit message format

All commits follow the existing project convention: `<type>(<scope>): <subject>` with `type ∈ {feat, fix, chore, i18n, ui, docs, test}`. Examples from `git log`:

- `feat: library — empty state with illustration`
- `fix: reader — chapter list removes 1-line cap`
- `chore: bump version to v2.4.2 (versionCode 16)`
- `i18n: complete en translations`

One commit per ticket. Conventional and granular.

---

## Reference

- Prior spec on the same pattern: `docs/superpowers/specs/2026-06-25-reader-place-keeping-design.md`
- Handoff: `/tmp/opencode/handoff-20260625-notif-ui.md` (read-only; do not duplicate)
- Project context: `AGENTS.md` at repo root
