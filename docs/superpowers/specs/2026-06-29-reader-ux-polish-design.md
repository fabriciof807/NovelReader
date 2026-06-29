# NovelReader v2.4.3 — Reader UX Polish

**Date:** 2026-06-29
**Scope:** Polish the reader and its surrounding surfaces — finish v2.4.2's toolbar haptics, add a keep-screen-on toggle, search in the chapter-list sheet, search + created-date in Favorites (with a drive-by fix to the library Reading badge's i18n), and strip the novel-name prefix from chapter titles. No schema changes. No network layer changes. No new top-level dependencies.
**Approach:** Per-surface commits, then a version bump. 4 × `feat`/`fix` + 1 × `chore` = 5 commits. v2.4.3 (versionCode 17).

---

## Context

- v2.4.2 (`e97d735`) is shipped and pushed. 32 unpushed local commits noted in the handoff have been pushed; working tree is dirty with the separate FreeWebNovel 403 fix workstream (`ChapterCrawler`, `ChapterFetcher`, `CoverDownloader`, `HttpClient`, `CloudflareCookieStore`, `ParserRegistry`, `WebImportUseCaseTest`, `StringUtils`, `WebFetchDiagnostic`, `DataStoreCloudflareCookieStore` + tests + the `2026-06-29-freewebnovel-403-fix.md` spec). **This workstream does not touch those files.**
- v2.4.2 (Task 9) added haptics to FABs, bookmark-add, and tab switches — but not the reader toolbar. v2.4.1's reader "perdi meu lugar" cluster (`2026-06-25-reader-place-keeping-design.md`) is fully implemented; the live scroll capture, lifecycle save, JS-injected config/bookmarks, and `configChanges` rotation are all in place. The `H3` keep-screen-on item was explicitly deferred as a "next session" follow-up.
- The handoff's candidate list ("font-size stepper", "no contentDescription", "swipe/tap not implemented", "search panel bolded snippets", "whole-novel search", "real-time settings") was verified against the current code: those items are already implemented. The genuine gaps are listed in the section headings below.

---

## Decisions

- **Keep-screen-on default ON, toggle in reader settings.** Persists via `ReaderPreferences`. The toggle is placed in the reader settings sheet grouped with auto-scroll (both are "behavior while reading" settings). Default `true` means existing users get screen-on after upgrade — acceptable per design discussion.
- **`View.keepScreenOn` for the flag, not `Window.addFlags`.** The Compose-friendly `LocalView.current.keepScreenOn = state.config.keepScreenOn` auto-clears when the reader view leaves the active hierarchy (returning to the library restores normal system timeout with no manual cleanup).
- **Chapter-list search filters by title, not by content.** Content search is already done by the in-reader `SearchResultsPanel` (whole-novel FTS, bolded snippets). The chapter-list search is for "find a named chapter" — a different use case.
- **Favorites search lives in the ViewModel via `combine`, not in the composable.** `FavoritesViewModel.displayItems` becomes a `combine(bookmarksFlow, _searchQuery) { ... }` that produces the filtered+joined list. Keeps the composable thin and makes filtering testable with Turbine, matching the project's ViewModel-test convention.
- **Locale-aware `RelativeTime` rewrite is in-scope as a drive-by.** The v2.4.2 Reading badge (`NovelCard.kt:158`) calls `formatRelativeTime` which returns hardcoded PT-BR strings, so English users get Portuguese relative time. The v2.4.2 i18n task (Task 10) verified `strings.xml` keys but missed dynamic strings. Rewriting `formatRelativeTime` to use `android.text.format.DateUtils` fixes the badge for English users at the same call site (no `NovelCard` change) and produces correct locale-aware output for the new Favorites created-date line.
- **Chapter title cleaning is hybrid: parser source fix + display-side pure function for existing chapters.** No DB migration, no data mutation. Future imports are cleaned at the source (parsers delegate to `TitleExtractor.extractChapterTitle(titleTag, novelTitle)`). Already-imported chapters are cleaned at render time in 4 surfaces via a new `cleanChapterTitleForDisplay(title, novelTitle)` function. Display-side has a separator-after-prefix guard to avoid false positives.
- **No notifications workstream in this cycle.** Still deferred per the v2.4.2 handoff.
- **Per-surface commits + a single version-bump commit.** Matches the v2.4.2 per-task commit pattern. Each commit independently compiles and passes tests.

---

## Architecture overview

```
                              ReaderConfig (Flow) + bookmarks/chapters flows
                                              │
                                              ▼
                            ReaderViewModel.state.config / .bookmarks / .chapters
                                              │
              ┌───────────────────────────────┼───────────────────────────────┐
              ▼                               ▼                               ▼
   Toolbar haptics +                Chapter-list search                Favorites search
   keep-screen-on                    (ReaderScreen)                    (FavoritesViewModel)
                                                                          via combine
                                              │                               │
                                              ▼                               ▼
                                  filterChaptersByQuery()           displayItems (filtered)
                                  (pure, unit-testable)              (Turbine-testable)
                                              │
                                              ▼
                                RelativeTime → DateUtils
                                (locale-aware; fixes badge)
                                              │
                                              ▼
                            cleanChapterTitleForDisplay()
                            (pure; 4 display sites)

  Parser title extraction: FreeWebNovel/ReadNovelFull → TitleExtractor.extractChapterTitle(tag, novelTitle)
```

Components:

| Component | File | Purpose |
|---|---|---|
| `ReaderConfig.keepScreenOn` | `data/local/preferences/ReaderPreferences.kt` | New boolean field, default `true`, `booleanPreferencesKey("keep_screen_on")` |
| `ReaderPreferences.updateKeepScreenOn` | same | Persists the toggle |
| `ReaderViewModel.updateKeepScreenOn` | `ui/reader/ReaderViewModel.kt` | Single-line `viewModelScope.launch { readerPreferences.updateKeepScreenOn(it) }` |
| `SettingsSheet` Switch row | `ui/reader/ReaderSettingsSheet.kt` | New row in the behavior group (near auto-scroll) |
| `LocalView.current.keepScreenOn` wiring | `ui/reader/ReaderScreen.kt` | Reactive assignment; auto-clears on view detach |
| Toolbar haptics | `ui/reader/ReaderScreen.kt` | `haptic.performHapticFeedback(LongPress)` on 6 `IconButton` onClicks |
| `filterChaptersByQuery(chapters, query)` | `ui/reader/ReaderScreen.kt` (or new `ChapterListFilter.kt`) | Pure function, unit-testable |
| Chapter-list search field + empty state | `ui/reader/ReaderScreen.kt` | `OutlinedTextField` in the sheet's `Column`; no-match line |
| `_searchQuery: MutableStateFlow<String>` + `combine` | `ui/favorites/FavoritesViewModel.kt` | `displayItems` becomes a `combine`; matches by title/chapter-title/note (case-insensitive), null-safe |
| Favorites search field | `ui/favorites/FavoritesScreen.kt` | `OutlinedTextField` below the `TopAppBar` |
| `formatRelativeTime` → `DateUtils` | `util/RelativeTime.kt` | Locale-aware rewrite; existing pure function → Robolectric |
| `cleanChapterTitleForDisplay(title, novelTitle)` | `data/parser/TitleExtractor.kt` | Pure function; case-insensitive prefix + separator-after-prefix guard |
| Parser delegation | `data/parser/FreeWebNovelParser.kt`, `ReadNovelFullParser.kt` | `parseChapterTitle` delegates to `TitleExtractor.extractChapterTitle(tag, novelTitle)`; h2/h1 fallbacks strip a leading `novelTitle` prefix |
| `ChaptersTab` + `LibraryScreen` param | `ui/library/tabs/ChaptersTab.kt`, `ui/library/LibraryScreen.kt:301` | New `novelTitle: String?` param; pass `selectedNovel?.title` |

---

## Per-feature

### Section 1 — Reader toolbar polish (haptics + keep-screen-on)

**Haptics.** The `haptic` is already captured at `ReaderScreen.kt:106` and used once (bookmark-add LongPress at `:163`). Add `haptic?.performHapticFeedback(HapticFeedbackType.LongPress)` to the `onClick` of the 6 toolbar `IconButton`s:

| Button | Current line | Already has haptics? |
|---|---|---|
| Search (top bar) | `:310` | No |
| Previous (bottom bar) | `:358` | No |
| Bookmark (bottom bar) | `:373` | No |
| Settings (bottom bar) | `:412` | No |
| Chapter list (bottom bar) | `:419` | No |
| Next (bottom bar) | `:428` | No |

`HapticFeedbackType.LongPress` matches the established v2.4.2 pattern (FABs, bookmark-add, tab switches).

**Keep-screen-on.** Add `val keepScreenOn: Boolean = true` to `ReaderConfig`; add `booleanPreferencesKey("keep_screen_on")` to `Keys`; add `suspend fun updateKeepScreenOn(value: Boolean)`; add it to the `config: Flow<ReaderConfig>` mapping. In `ReaderViewModel`, add `fun updateKeepScreenOn(value: Boolean)` as a single-line `viewModelScope.launch { readerPreferences.updateKeepScreenOn(value) }`. In `SettingsSheet`, add a new `Row` in the "behavior" group (above or below the auto-scroll slider): `Switch(checked = config.keepScreenOn, onCheckedChange = onKeepScreenOnChange)` with a label using `stringResource(R.string.reader_keep_screen_on)`. Extend the `SettingsSheet` signature with `onKeepScreenOnChange: (Boolean) -> Unit` and plumb it from `ReaderScreen` to `viewModel.updateKeepScreenOn`.

In `ReaderScreen`, set the flag reactively: `LocalView.current.keepScreenOn = state.config.keepScreenOn`. This is a direct property assignment in the composition (or wrapped in a `LaunchedEffect(state.config.keepScreenOn)` if the recomposition-frequency concern materializes; the direct assignment is the simpler form and is sufficient). `View.setKeepScreenOn` keeps the screen on while the view is attached/visible; when the reader leaves the foreground (back to library) the Compose view is detached and the flag is auto-cleared — no manual cleanup.

**Files touched:** `ReaderScreen.kt`, `ReaderSettingsSheet.kt`, `ReaderPreferences.kt`, `ReaderViewModel.kt`, `strings.xml` + `values-en/strings.xml` (new `reader_keep_screen_on` key).

### Section 2 — Chapter list sheet: search-in-chapter-picker

Add an `OutlinedTextField` search field at the top of the `ModalBottomSheet`'s `Column` (just below the "chapters_count" header). Local state `var chapterSearchQuery by remember { mutableStateOf("") }` scoped to the sheet's composition.

Extract a pure function `filterChaptersByQuery(chapters: List<ChapterEntity>, query: String): List<ChapterEntity>`:
- If `query.isBlank()` → return `chapters` unchanged.
- Else return `chapters.filter { it.title.contains(query.trim(), ignoreCase = true) }`.

Use it as `val filtered = remember(chapterSearchQuery, state.allChapters) { filterChaptersByQuery(state.allChapters, chapterSearchQuery) }` and pass `filtered` to the `items()` block.

Header: when `chapterSearchQuery.isNotBlank()`, show `stringResource(R.string.chapters_count_filtered, filtered.size, state.allChapters.size)`; else keep the existing `stringResource(R.string.chapters_count, state.allChapters.size)`.

No-match case: when `chapterSearchQuery.isNotBlank() && filtered.isEmpty()`, show a small `Text` "Nenhum capítulo encontrado" / "No chapters found" (`R.string.chapter_list_no_matches`) instead of the empty `LazyColumn`. Style: `bodyLarge`, `onSurface.copy(alpha = 0.5f)`, centered.

`LazyColumn` `key = { it.id }` stays so item identity persists across filter changes (no recomposition flicker). Current-chapter highlight (`isCurrent`) and read-chapter dimming (`isRead`) work unchanged on the filtered list (per-item checks, unaffected by filtering). `LazyListState` persists across filter changes (no reset).

**Files touched:** `ReaderScreen.kt`, `strings.xml` + `values-en/strings.xml` (new `chapter_list_search_hint`, `chapters_count_filtered`, `chapter_list_no_matches`).

### Section 3 — Favorites screen: search + created-date + RelativeTime i18n fix

**Search-within-titles.** Move filtering into `FavoritesViewModel`. Add:
- `private val _searchQuery = MutableStateFlow("")`
- `fun setSearchQuery(query: String) { _searchQuery.value = query }`
- Convert `displayItems` to a `combine(bookmarkDao.getAll(), _searchQuery) { bookmarks, query -> ... }` flow. The combined body: load chapters + novels (current logic at `FavoritesViewModel.kt:40-50`), filter by query (case-insensitive match on bookmark title, chapter title, or note), then build `BookmarkDisplayItem`s. Null-safe: a bookmark whose novel/chapter was deleted (`chapter == null` / `novel == null`) still matches on its own `title` and `note`.

The composable becomes thin: `OutlinedTextField` below the `TopAppBar` bound to `_searchQuery` via the ViewModel; `displayItems.collectAsState()` as today. Novel-grouping headers (`isFirstForNovel` at `FavoritesScreen.kt:122`) recompute on the filtered list — the existing logic operates on the current list, which is now the filtered list.

**Show-created-date.** Add a third text line to `BookmarkItem` (`FavoritesScreen.kt:170-195`) showing `formatRelativeTime(bookmark.createdAt)` — `labelSmall`, `onSurface.copy(alpha = 0.5f)`, placed below the note (or below the chapter title if no note). Fallback: if `formatRelativeTime` returns null (`> 1 month`, negative, or `createdAt <= 0`), render an absolute date via `DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(bookmark.createdAt))` (locale-aware, no string needed).

**RelativeTime i18n fix.** Rewrite `formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String?` to use `android.text.format.DateUtils.getRelativeTimeSpanString(timestampMs, nowMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)`. This is fully locale-aware ("2 min. ago" / "há 2 min."). Behavior:
- `timestampMs <= 0L` → return `null` (unchanged).
- `delta < 0L` (future) → return `null` (unchanged).
- Else → return `DateUtils.getRelativeTimeSpanString(...)` as `String`.
- The `> 1 month` case is handled by `DateUtils` itself (produces an absolute-like relative string per locale, e.g. "Jun 29" or "29/06/2026" depending on `FORMAT_ABBREV_RELATIVE`). The Favorites fallback using `DateFormat.getDateInstance` is still useful as a belt-and-suspenders layer and handles the `null` return.

`NovelCard.kt:158` is unchanged (same call site) but now produces locale-correct output for English users. **This is a behavior change visible to existing English users** — it removes the "há 5 min" PT-BR string in the Reading badge. Acceptable per design discussion.

`DateUtils` needs Android — tests move from JVM-pure to Robolectric. Confirm Robolectric is already a test dependency (`AGENTS.md` says yes). The test file path stays `util/RelativeTimeTest.kt`; existing tests are updated for the new return type (String vs null).

**Files touched:** `FavoritesScreen.kt`, `FavoritesViewModel.kt`, `RelativeTime.kt`, `strings.xml` + `values-en/strings.xml` (new `favorites_search_hint` only — the absolute-date fallback uses locale `DateFormat` and needs no string). `NovelCard.kt` — no code change, behavior change.

### Section 4 — Cross-cutting (no new strings for title cleaning; updated commit plan)

**Strings/i18n (consolidated, bilingual pt + en for every new key):**
- `reader_keep_screen_on` — "Manter tela ligada" / "Keep screen on"
- `chapter_list_search_hint` — "Buscar capítulo..." / "Search chapter..."
- `chapters_count_filtered` — "%1$d de %2$d capítulos" / "%1$d of %2$d chapters"
- `chapter_list_no_matches` — "Nenhum capítulo encontrado" / "No chapters found"
- `favorites_search_hint` — "Buscar favoritos..." / "Search favorites..."
- `RelativeTime` no longer needs string-table entries (DateUtils is locale-aware).
- v2.4.2 i18n discipline: every pt key mirrored in en, verified via the DocumentBuilder XML-parsing test approach (Task 10).

**Commit plan (5 commits, direct to `main`):**
1. `feat: reader toolbar haptics + keep-screen-on setting` (Section 1)
2. `feat: chapter-list sheet search` (Section 2)
3. `feat: favorites search + created-date + RelativeTime i18n fix` (Section 3)
4. `fix: strip novel-name prefix from chapter titles (parser + display)` (Section 5)
5. `chore: bump version to v2.4.3` (versionCode 16→17, versionName 2.4.2→2.4.3, READMEs)

Each commit independently compiles and passes tests. Commits 1–4 touch disjoint file sets; commit 5 is metadata only. **Each commit must scope `git add` to its own files only** to avoid staging the in-progress FreeWebNovel 403 work.

**Testing strategy (TDD per project convention — failing test → impl → passing test, per feature):**
- `ReaderPreferencesTest` — round-trip: default `keepScreenOn = true`, set `false`, read `false`.
- `ReaderViewModelTest` — `updateKeepScreenOn(false)` calls the pref updater; `state.config.keepScreenOn` reflects it.
- `ReaderSettingsSheet` Compose test — new Switch renders, toggling invokes `onKeepScreenOnChange`.
- Haptics: unit-testing `LocalHapticFeedback` is brittle. Add a Compose UI test with a recording `HapticFeedback` spy asserting a haptic fired on each toolbar button tap; if too brittle, fall back to a smoke test (buttons still fire their actions) + manual QA.
- `filterChaptersByQuery` pure-function test: empty → all, non-matching → empty, matching → subset, case-insensitivity.
- Chapter-list sheet Compose test: open sheet, type a query, verify list narrows and current-chapter highlight still works on the filtered list; verify no-match line for a non-matching query.
- `FavoritesViewModelTest` (Turbine): set query, verify `displayItems` filters by title/chapter-title/note; null-chapter case still matches on bookmark title; blank query returns all.
- `RelativeTimeTest` (Robolectric): en locale → English string; pt-BR locale → "há X" string; `ts <= 0` → null; future → null.
- `FavoritesScreen` Compose test: search field types and list narrows; created-date text appears.
- `NovelCard` badge: existing or new Robolectric test verifies it still renders (no `NovelCard` code change; behavior is now locale-aware).
- `TitleExtractorTest.cleanChapterTitleForDisplay`: prefix present (→ stripped), `–`/`—`/`|` separators, case-insensitive, no-prefix (→ unchanged), null novel (→ unchanged), false-positive guard ("The Beginning of the End" with novel "The Beginning" → unchanged).
- Parser tests: HTML fixtures where `doc.title()` uses `–`/`—`/`|` + novel name; assert `parseChapterTitle` returns the chapter part without the novel name. Use the existing parser-test fixture pattern.
- **Baseline 163/163 stays green throughout; new tests add to the count.**

**Out of scope (explicit):**
- The **in-progress FreeWebNovel 403 work** in the working tree — do not touch. UX commits must scope `git add` to UX files only.
- Notifications workstream (group summary, error channel, rationale dialog) — still deferred.
- Auto-scroll-to-current in the chapter list (recommended in design discussion, not chosen) — deferred.
- Font-family settings UI (M7 from v2.4.1 spec), sleep timer, brightness, tap zones (M9), reset-to-defaults, volume/book section headers (needs DB migration) — all deferred.
- DB cleanup of existing chapter titles (the "A — Source fix + DB cleanup" option from design discussion) — deferred. The display-side approach handles existing chapters without data mutation.

**Verification (per AGENTS.md):** before each commit, `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` clean. Final: full 163+new tests green, build green, manual emulator pass on reader (haptics, keep-screen-on, chapter search, title cleaning), favorites (search, created-date), library (Reading badge renders in both pt-BR and en locales after the RelativeTime change).

**Risks:**
- `RelativeTime` rewrite moves tests from JVM-pure to Robolectric (DateUtils needs Android). Mitigation: confirm Robolectric is already a test dependency (`AGENTS.md` confirms).
- Parser title-extraction change could alter output for sources currently working correctly. Mitigation: parser fixture tests cover existing cases (regression guard).
- `View.keepScreenOn` behavior — manual QA on emulator: verify screen stays on while reading, restores timeout in library, works with auto-rotate, works across the "perdi meu lugar" configChanges.
- Working-tree conflict with 403 work — `git add` must be scoped per commit. The 5 commit file sets are documented in this spec for easy scoping.
- Haptics Compose spy test may be brittle — fallback: manual QA + action smoke test.
- `RelativeTime` behavior change for the Reading badge — PT-BR users see no regression; English users see correct locale strings. The change is a bugfix.

### Section 5 — Chapter title cleaning (hybrid)

**Source fix (future imports).** `TitleExtractor.extractChapterTitle(fullTitle, novelTitle)` (`TitleExtractor.kt:35`) is already fairly robust: it strips a leading `novelTitle` prefix and iterates `SEPARATORS` (` | `, ` – `, ` - `, ` — `, ` :: `, ` « `). `GenericFallbackParser.parseChapterTitle` already delegates to it. The gap is in the other two parsers:

- `FreeWebNovelParser.parseChapterTitle` (`FreeWebNovelParser.kt:46-67`): the `titleTag` split uses `Regex("\\s*-\\s*")` — ASCII hyphen only. If the title tag uses `–`, `—`, or ` | `, the split fails and it falls through to the h2/h1 fallbacks, which return `h2.text()` / `h1.text()` without stripping the novel name.
- `ReadNovelFullParser.parseChapterTitle` (`ReadNovelFullParser.kt:52-74`): the `titleTag` split uses `Regex("\\s*[-–]\\s*")` — hyphen + en-dash, missing em-dash and pipe. The `chr-text` span fallback is usually clean (returns early at `:54`). The h2 fallback (`:56-60`) returns `h2.text()` only if it contains "Chapter" — doesn't strip novel name.

**Fix:** refactor both `parseChapterTitle` methods to delegate to `TitleExtractor.extractChapterTitle(titleTag, novelTitle)`, passing the parsed `novelTitle` so the prefix-stripping + separator logic is centralized. For the h2/h1 fallbacks, strip a leading `novelTitle` prefix (using a small helper or the same prefix-strip logic) before returning. `GenericFallbackParser` is unchanged.

**Display-side (existing chapters, no migration).** New pure function in `TitleExtractor`:

```kotlin
fun cleanChapterTitleForDisplay(title: String, novelTitle: String?): String
```

Logic:
1. If `novelTitle.isNullOrBlank()` → return `title` unchanged.
2. If `title.startsWith(novelTitle, ignoreCase = true)` is `false` → return `title` unchanged.
3. Compute the character offset right after the prefix: `after = title.substring(novelTitle.length).trimStart()`.
4. If `after` starts with any separator in `SEPARATORS` (reuses the existing `TitleExtractor.SEPARATORS` list — see extension below) → return `after.substring(separator.length).trimStart()`.
5. Else (prefix matched but no separator follows) → return `title` unchanged (the false-positive guard — prevents "The Beginning" from stripping "The Beginning of the End - Chapter 5").

The separator list is the existing `TitleExtractor.SEPARATORS = listOf(" | ", " – ", " - ", " — ", " :: ", " « ")` extended with `": "` so the display-side function handles "Novel Name: Chapter 5" as well. Extending `SEPARATORS` is a strict improvement for `extractChapterTitle` too (it now also splits colon-separated titles like "Novel Name: Chapter 5 - Title" → chapter part). Both functions reuse the same list, keeping the source-fix and display-fix consistent.

**Call sites (4):**
1. `ReaderScreen.kt:288` (toolbar) — `cleanChapterTitleForDisplay(state.chapter?.title ?: "", state.novel?.title)`
2. `ReaderScreen.kt:247` (chapter list sheet item) — `cleanChapterTitleForDisplay(chapter.title, state.novel?.title)`
3. `ChaptersTab.kt:172` — add `novelTitle: String?` parameter to `ChaptersTab`; call `cleanChapterTitleForDisplay(chapter.title, novelTitle)`. Parent `LibraryScreen.kt:301` passes `novelTitle = selectedNovel?.title`.
4. `FavoritesScreen.kt:139` — `cleanChapterTitleForDisplay(item.chapter?.title ?: "", item.novel?.title)`

**Files touched:** `TitleExtractor.kt` (new function), `FreeWebNovelParser.kt`, `ReadNovelFullParser.kt`, `ReaderScreen.kt` (2 sites), `ChaptersTab.kt` (+ param), `LibraryScreen.kt:301` (pass title), `FavoritesScreen.kt` (1 site). **Does not touch the dirty 403 files** (`ParserRegistry.kt`, `ChapterCrawler.kt`, `ChapterFetcher.kt`, `CoverDownloader.kt`, `HttpClient`, `CloudflareCookieStore`, `WebFetchDiagnostic`, `StringUtils`, `WebImportUseCaseTest`, `DataStoreCloudflareCookieStore*Test.kt`) — `git add` is scoped to the files above.

**Edge cases:** case-insensitive prefix match (handles "novel name - ..." vs parsed "Novel Name"); separator-after-prefix guard (handles "The Beginning of the End" with novel "The Beginning"); null/blank novel title (early return); novel title containing a separator itself (the exact-prefix match still works because `startsWith` is applied to the full `novelTitle` string).

**Testing:**
- `TitleExtractorTest.cleanChapterTitleForDisplay`: prefix present (→ stripped), en-dash/em-dash/pipe separators, case-insensitive, no-prefix (→ unchanged), null novel (→ unchanged), false-positive guard ("The Beginning of the End" + "The Beginning" → unchanged).
- Parser tests: HTML fixtures where `doc.title()` uses `–`/`—`/`|` + novel name; assert `parseChapterTitle` returns the chapter part without the novel name. Add regression tests covering the current-case titles (ensure no existing case is broken by the delegation).
- Display sites: pure function is unit-tested; Compose smoke or manual QA verifies the 4 surfaces render the cleaned title (no Compose test required if the function is fully tested and the call sites are trivially correct).

---

## Out of scope (consolidated)

- FreeWebNovel 403 work — separate workstream, do not touch.
- Notifications workstream (group summary, error channel, rationale dialog) — deferred.
- Auto-scroll-to-current in chapter list, font-family settings UI (M7), sleep timer, brightness, tap zones (M9), reset-to-defaults, volume/book section headers, DB cleanup of existing chapter titles — all deferred.
- `H5` (sync reader theme with system) from the v2.4.1 spec — deferred.
- New `ChapterEntity` fields (e.g., volume/book) — deferred (would need a migration).
- `app/release/baselineProfiles/*` and `app/release/output-metadata.json` — regenerated by every build, not staged (per `AGENTS.md`).
- Push to remote — SSH key not configured in this environment; commits stay local like the v2.4.2 commits before they were pushed. Not in this workstream's scope.

---

## Reference (do not duplicate)

- This handoff: `/tmp/opencode/handoff-20260626-reader-ux-polish.md` (the input that started this cycle).
- v2.4.2 ship handoff (referenced, not present in `/tmp/opencode/` anymore): the v2.4.2 cycle shipped 14 tasks in one session. See `docs/superpowers/specs/2026-06-25-ui-ux-polish-design.md` and `docs/superpowers/plans/2026-06-25-ui-ux-polish.md`.
- v2.4.1 reader "perdi meu lugar" spec: `docs/superpowers/specs/2026-06-25-reader-place-keeping-design.md` (H3 keep-screen-on is explicitly deferred as a "next session" follow-up; H5/M7/M9 deferred).
- In-progress FreeWebNovel 403 spec: `docs/superpowers/specs/2026-06-29-freewebnovel-403-fix.md` and handoff `/tmp/opencode/handoff-20260626-freewebnovel-403.md` (separate workstream; do not touch).
- v2.4.2 progress ledger: `.git/sdd/progress.md` (163/163 baseline).
- Project context: `AGENTS.md` (tech stack, conventions, build commands, testing approach).
- Existing parser/extractor code: `data/parser/{TitleExtractor,ChapterNumberExtractor,FreeWebNovelParser,ReadNovelFullParser,GenericFallbackParser}.kt`.
- Reader surfaces: `ui/reader/{ReaderScreen,ReaderSettingsSheet,BookmarkDialogs,SearchResultsPanel,ReaderWebView,ReaderHtmlBuilder,ReaderViewModel,CreateCharacterDialog}.kt`.
- Favorites: `ui/favorites/{FavoritesScreen,FavoritesViewModel}.kt`.
- `util/RelativeTime.kt` and its single call site `ui/library/components/NovelCard.kt:158`.

---

## Sensitive info

No API keys, passwords, or PII in this spec or its referenced artifacts.
