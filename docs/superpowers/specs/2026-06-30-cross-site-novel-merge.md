# Cross-site novel merge + new-chapter badge — design

**Date:** 2026-06-30
**Phase:** B (depends on Phase A)
**Status:** Approved 2026-06-29

## Problem

The user wants to import the same novel from multiple sites (e.g. "Dragon King" from both freewebnovel.com and readnovelfull.com) and have the chapters **complement** each other in the library — no duplicates, fill the gaps. Today this is broken in three ways:

1. `NovelEntity.sourceUrl` is a single column. `NovelImporter.ensureNovel` calls `updateSourceUrl` on every import, **overwriting** the previous source. Auto-update (`ChapterUpdateCheckWorker`) and web-missing-chapter scan (`ScanMissingChaptersUseCase.scanWeb`) only ever see the **last** imported source.
2. Dedup is **purely by `fileName`**. fileNames differ per site (e.g. `chapter-10` vs `chapter-1-10`), so the second import's chapter 10 gets inserted as a **second row** next to the first import's chapter 10.
3. There is no UI affordance indicating which novels have new chapters since the user's last visit.

A second-order problem: titles can differ by case across sites (`"Dragon King"` vs `"dragon king"`), causing the two imports to land in **two different `NovelEntity` rows** instead of one.

## Goal (v1)

1. Importing the same novel from N sites produces **one** `NovelEntity` with chapters from all sources, deduplicated by chapter number.
2. Each source keeps its own `sourceUrl`, `lastCheckedAt`, and `autoUpdate` flag, so the background update worker can check **all** sources for new chapters.
3. A blue badge appears on the library card for any novel that has new chapters since the last time the user opened it.
4. The novel's `coverPath` is set by the **first** source; subsequent imports do not overwrite it.
5. The web-missing-chapter scan does **not** flag a chapter as missing if the same number exists in the DB from a different source.

## Decisions (recap from brainstorming)

- **Dedup key (v1)**: in-memory `ChapterNumberExtractor.extract(...)` over the existing chapter list. No new `chapterNumber` column needed.
- **Title merging (v1)**: `COLLATE NOCASE` lookup, auto-merge. No UI prompt. Titles that diverge beyond case (e.g. "Dragon King" vs "Dragon King: Seven Goddesses") do **not** auto-merge in v1.
- **Schema (v1)**: yes, ship `NovelSourceEntity` and migrate Room v8 → v9. Adds proper multi-source auto-update from day one.
- **Badge (v1)**: simple boolean `hasUpdates` on `NovelEntity`; reset when the user opens the novel. Numeric count is a v2 follow-up.

## Architecture

### Schema changes (Room v8 → v9)

**`novels`** gains one column:
- `hasUpdates INTEGER NOT NULL DEFAULT 0` — badge flag.

**New entity `novel_sources`**:
- `id` (PK auto)
- `novelId` (FK CASCADE → `novels.id`)
- `sourceUrl` (TEXT NOT NULL)
- `domain` (TEXT NOT NULL) — used for parser dispatch
- `isPrimary` (INTEGER NOT NULL DEFAULT 0) — exactly one per novel; cover/title inference target
- `lastCheckedAt` (INTEGER NOT NULL DEFAULT 0)
- `autoUpdate` (INTEGER NOT NULL DEFAULT 1)
- `addedAt` (INTEGER NOT NULL)
- `UNIQUE(novelId, sourceUrl)` — one row per (novel, source) pair

**Migration** `MIGRATION_8_9`:
1. `ALTER TABLE novels ADD COLUMN hasUpdates INTEGER NOT NULL DEFAULT 0`.
2. `CREATE TABLE novel_sources ...` (full schema, indices, FK).
3. Backfill: for every existing novel with `sourceUrl != ''`, insert a `novel_sources` row with `isPrimary=1`, `domain=''` (Kotlin-pass post-migration fills the `domain` from `URI(sourceUrl).host?.removePrefix("www.")`).
4. `MIGRATION_8_9_TEST_REQUIRED` is asserted by adding a new entry to `app/src/androidTest/.../MigrationTest.kt`.

`@Database(version = 9)` bump. Export schema: `./gradlew :app:exportSchema` → commit `app/schemas/com.novelreader.data.local.db.NovelDatabase/9.json`.

`DatabaseModule` adds `MIGRATION_8_9` to `.addMigrations(...)`.

### `NovelSourceDao`

```kotlin
@Dao
interface NovelSourceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: NovelSourceEntity): Long

    @Query("UPDATE novel_sources SET sourceUrl=:url, domain=:domain WHERE id=:id")
    suspend fun updateUrl(id: Long, url: String, domain: String)

    @Query("SELECT * FROM novel_sources WHERE novelId = :novelId ORDER BY isPrimary DESC, addedAt ASC")
    suspend fun getByNovel(novelId: Long): List<NovelSourceEntity>

    @Query("SELECT * FROM novel_sources WHERE novelId = :novelId AND isPrimary = 1 LIMIT 1")
    suspend fun getPrimary(novelId: Long): NovelSourceEntity?

    @Query("SELECT * FROM novel_sources WHERE novelId = :novelId AND sourceUrl = :url LIMIT 1")
    suspend fun findByNovelAndUrl(novelId: Long, url: String): NovelSourceEntity?

    @Query("UPDATE novel_sources SET isPrimary = 0 WHERE novelId = :novelId")
    suspend fun clearPrimary(novelId: Long)

    @Query("UPDATE novel_sources SET isPrimary = 1 WHERE id = :id")
    suspend fun setPrimary(id: Long)

    @Query("DELETE FROM novel_sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE novel_sources SET lastCheckedAt = :ts WHERE id = :id")
    suspend fun updateLastChecked(id: Long, ts: Long)

    @Query("UPDATE novel_sources SET autoUpdate = :on WHERE id = :id")
    suspend fun setAutoUpdate(id: Long, on: Boolean)

    @Query("""
        SELECT ns.* FROM novel_sources ns
        INNER JOIN novels n ON n.id = ns.novelId
        WHERE ns.autoUpdate = 1 AND n.autoUpdate = 1
    """)
    suspend fun getAllForAutoUpdate(): List<NovelSourceEntity>
}
```

### `NovelImporter` — multi-source aware

- New constructor param: `novelSourceDao: NovelSourceDao`.
- `ensureNovel(novelTitle, sourceUrl, domain, targetNovelId: Long? = null)`.
  - If `targetNovelId != null`: `novelDao.getNovelById(targetNovelId)`. Use it. (No title lookup; user explicitly chose the target.)
  - Else: `novelDao.getNovelByTitleIgnoreCase(novelTitle)`. Use it or insert new.
- After resolving `novelId` and the `sourceUrl` is non-blank:
  - Find existing `NovelSourceEntity` for `(novelId, sourceUrl)`. If absent, insert one.
  - **First** source for the novel → `isPrimary=1`. Subsequent → `isPrimary=0`.
  - If `isPrimary=1` AND `novelDao.getNovelById(novelId)?.sourceUrl.isNullOrEmpty()` → `novelDao.updateSourceUrl(novelId, sourceUrl)` (backfill the denormalized column for legacy readers).
  - Otherwise: leave `novels.sourceUrl` alone. **Do not** overwrite a non-empty value (this was the v1 regression that broke auto-update).
- `getNovelByTitle` (existing exact-match) is **replaced** by `getNovelByTitleIgnoreCase` everywhere it is used for novel-merge purposes.

### `NovelDao` — additions

- `@Query("SELECT * FROM novels WHERE title = :title COLLATE NOCASE LIMIT 1") suspend fun getNovelByTitleIgnoreCase(title: String): NovelEntity?`
- `@Query("UPDATE novels SET hasUpdates = :on WHERE id = :id") suspend fun setHasUpdates(id: Long, on: Boolean)`
- `@Query("UPDATE novels SET hasUpdates = 0 WHERE id = :id") suspend fun clearHasUpdates(id: Long)`

### `WebImportUseCase.importChapters` — dedup + plumbing

- New params: `domain: String`, `targetNovelId: Long?`.
- After `novelImporter.ensureNovel(...)`:
  - Build `existingChapters = chapterDao.getChaptersByNovelSync(novelId)`.
  - Build `existingNumbers: Set<Int>` = `existingChapters.mapNotNull { ChapterNumberExtractor.extract(it.title, it.fileName).takeIf { n -> n != Int.MAX_VALUE && it.content.isNotBlank() } }.toSet()`.
- Cover guard before `coverDownloader.downloadCover(novelId, coverUrl, filesDir)`:
  - `if (novelDao.getNovelById(novelId)?.coverPath.isNullOrEmpty()) { coverDownloader.downloadCover(...) }`.
- In the link loop, **before** the `fileName in existingFileNames` skip:
  - If `link.chapterNumber != Int.MAX_VALUE && link.chapterNumber in existingNumbers`: skip, `successCount++`, fire `onProgress`, continue.
  - Otherwise proceed (existing `fileName` skip applies as a secondary check).
- After `novelImporter.insertChapters(novelId, importedChapters)`:
  - If `targetNovelId != null OR existingChapters.isNotEmpty()` AND `importedChapters.isNotEmpty()`: `novelDao.setHasUpdates(novelId, true)`.

### Plumbing `targetNovelId` + `domain` end-to-end

Signature chain (all gains the new params; defaults preserve current behavior):

1. `WebImportUseCase.importChapters(novelTitle, links, coverUrl?, filesDir?, orderIndexOffset, sourceUrl, domain = "", targetNovelId = null, onProgress, onError)`.
2. `BackgroundImportManager.startImport(title, links, coverUrl, sourceUrl, domain, targetNovelId, onProgress)` — adds two new args.
3. `ImportJobSpec` gains two fields: `domain`, `targetNovelId`. `ImportJobSpec.create(...)` signature updates.
4. `SpecReader` / `SpecFileStore` — read/write the new fields.
5. `ChapterImportWorker` — read new workData keys, pass through.
6. `WebImportViewModel.startImport` — derive `domain = URI(sourceUrl).host?.removePrefix("www.").orEmpty()` and pass.

### `ChapterUpdateCheckWorker` — iterate `novel_sources`

Refactor: replace `novelDao.getAutoUpdateNovels()` → `novelSourceDao.getAllForAutoUpdate()`. For each `source`:
1. `novel = novelDao.getNovelById(source.novelId)`; skip if null.
2. `webImportUseCase.fetchChapterList(source.sourceUrl)`.
3. On success, compute `existingFileNames` and `existingNumbers` (same as WebImportUseCase dedup).
4. Filter: `link.chapterNumber !in existingNumbers AND fileNameFromUrl(link.url, n) !in existingFileNames`.
5. If any new chapters: `novelDao.setHasUpdates(novel.id, true)` and schedule import.
6. `novelSourceDao.updateLastChecked(source.id, now)` always (success or failure path).

### Badge UI

- `NovelCard.kt` (`app/src/main/java/com/novelreader/ui/library/components/NovelCard.kt`) — add a `Box` overlay in the top-right corner of the cover image: `Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)`. Visible `if (novel.hasUpdates)`. Renders **above** the cover, not inside.
- `NovelListItem.kt` — same indicator, small dot to the right of the title.
- `LibraryViewModel.onIntent(OpenNovel(id))` — call `novelDao.clearHasUpdates(id)` before navigating. (Or hook in `NavGraph` — chosen at implementation time based on what is cleanest.)
- New string: `library_new_chapters_badge` (PT/EN). Value: "Novos capítulos" / "New chapters". Used as contentDescription for accessibility (the dot itself is purely visual).
- `I18nCoverageTest` enforces EN mirror.

### `ScanMissingChaptersUseCase.scanForMissing` — cross-source suppression

Before flagging a `MISSING_NUMBER` for a `link` from the re-crawl, check if any existing chapter has a matching `ChapterNumberExtractor.extract(...) == link.chapterNumber` AND non-blank content. If so, treat as "present" and skip the `FailedChapterEntity` insert.

### Local-path parity (`ChapterSorter`)

`ChapterSorter.buildSortedEntries` — add the same chapter-number skip alongside the existing `fileName` skip. Covers the MHT re-import case ("better translation of chapter 10") so we don't double-insert.

## Files

### New
- `app/src/main/java/com/novelreader/data/local/db/entity/NovelSourceEntity.kt`
- `app/src/main/java/com/novelreader/data/local/db/dao/NovelSourceDao.kt`
- `app/src/test/java/com/novelreader/data/local/db/dao/NovelSourceDaoTest.kt` (androidTest)
- `app/src/test/java/com/novelreader/domain/usecase/NovelImporterMultiSourceTest.kt`
- `app/src/test/java/com/novelreader/data/worker/ChapterUpdateCheckWorkerMultiSourceTest.kt`
- `app/src/test/java/com/novelreader/ui/library/components/NovelCardBadgeTest.kt` (Compose UI test)

### Modified
- `app/src/main/java/com/novelreader/data/local/db/NovelDatabase.kt` — version 9, add entity, register migration
- `app/src/main/java/com/novelreader/data/local/db/entity/NovelEntity.kt` — `hasUpdates`
- `app/src/main/java/com/novelreader/data/local/db/dao/NovelDao.kt` — `getNovelByTitleIgnoreCase`, `setHasUpdates`, `clearHasUpdates`
- `app/src/main/java/com/novelreader/domain/usecase/NovelImporter.kt` — multi-source `ensureNovel`
- `app/src/main/java/com/novelreader/domain/usecase/WebImportUseCase.kt` — dedupe + cover first-wins + `hasUpdates` setter
- `app/src/main/java/com/novelreader/data/worker/ChapterUpdateCheckWorker.kt` — iterate `novel_sources`
- `app/src/main/java/com/novelreader/domain/usecase/ScanMissingChaptersUseCase.kt` — cross-source dedupe
- `app/src/main/java/com/novelreader/domain/usecase/importnovel/ChapterSorter.kt` — chapter-number skip
- `app/src/main/java/com/novelreader/ui/library/components/NovelCard.kt` — badge
- `app/src/main/java/com/novelreader/ui/library/components/NovelListItem.kt` — badge
- `app/src/main/java/com/novelreader/ui/library/LibraryViewModel.kt` — clear `hasUpdates` on open
- `app/src/main/java/com/novelreader/ui/webimport/ImportScreen.kt` — status text "Merging into existing"
- `app/src/main/java/com/novelreader/data/worker/BackgroundImportManager.kt` — new params
- `app/src/main/java/com/novelreader/data/worker/ImportJobSpec.kt` — new fields
- `app/src/main/java/com/novelreader/data/worker/SpecReader.kt`, `SpecFileStore.kt` — read/write new fields
- `app/src/main/java/com/novelreader/data/worker/ChapterImportWorker.kt` — pass new workData
- `app/src/main/java/com/novelreader/ui/webimport/WebImportViewModel.kt` — derive `domain` + pass `targetNovelId`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` — `library_new_chapters_badge`, `import_merging_into_existing`
- `app/schemas/com.novelreader.data.local.db.NovelDatabase/9.json` (new, generated)
- `app/src/androidTest/java/com/novelreader/data/local/db/MigrationTest.kt` — new `migrate8to9_addsNovelSourcesAndHasUpdates`

## Tests (TDD)

### Schema
- `MigrationTest.migrate8to9_addsNovelSourcesAndHasUpdates` — opens v8 DB, runs `_8_9`, asserts:
  - `PRAGMA table_info(novels)` includes `hasUpdates`.
  - `SELECT * FROM novel_sources` table exists and is empty for a fresh v8 DB.
  - For a v8 DB with 1 novel having `sourceUrl='https://x.com/y'`: post-migration has 1 `novel_sources` row with `isPrimary=1`, `sourceUrl='https://x.com/y'`.

### DAOs
- `NovelSourceDaoTest` (androidTest, in-memory):
  - insert + getByNovel returns 1 row.
  - insert twice for same `(novelId, sourceUrl)` → second ignored (unique).
  - delete novel → sources cascade-delete.
  - `getAllForAutoUpdate` honors both `novels.autoUpdate=0` and `novel_sources.autoUpdate=0`.

### Use cases
- `NovelImporterMultiSourceTest`:
  - `ensureNovel("Dragon King", "https://a.com/x", "a.com", targetNovelId = null)` on empty DB → novelId=1, 1 `novel_sources` row with `isPrimary=1`, `domain='a.com'`, `novels.sourceUrl='https://a.com/x'`.
  - Same call again with `https://b.com/y` → reuses novelId=1, 2nd `novel_sources` row with `isPrimary=0`, `domain='b.com'`, `novels.sourceUrl` **unchanged** (still 'https://a.com/x').
  - `ensureNovel("Dragon King", "https://c.com/z", "c.com", targetNovelId = 7)` → uses novelId=7 regardless of title; inserts/updates source for 7.
  - Title case-insensitive merge: `ensureNovel("dragon king", ...)` on a DB with novel title `"Dragon King"` → same novelId.

- `WebImportUseCaseDedupTest`:
  - Novel has chapter 10 with content "AAA". Import links `[chapter 10, chapter 11]`. `chapterFetcher.fetch` called **once** (for chapter 11). `successCount=2` (both skipped count as success).
  - Cover guard: novel with `coverPath='files/...jpg'` → `coverDownloader.downloadCover` **not** called.
  - `setHasUpdates` called when `importedChapters.isNotEmpty() && (targetNovelId != null || existingChapters.isNotEmpty())`. NOT called when novel was just created and only filled with chapters (first import).

- `ChapterUpdateCheckWorkerMultiSourceTest`:
  - 2 `novel_sources` rows for 2 novels, both auto-update on. Re-crawl finds 0 new chapters each → `setHasUpdates` not called.
  - Re-crawl finds 3 new chapters on source A → `setHasUpdates(novelA.id, true)` called; `setHasUpdates(novelB.id, ...)` not called; `novelSourceDao.updateLastChecked(A, now)` called.
  - `novels.autoUpdate=0` for a novel → its source rows are skipped even if `novel_sources.autoUpdate=1`.

- `ScanMissingChaptersUseCaseTest`:
  - Novel has chapter 10 with content "AAA" (imported from site A). Re-crawl site B with chapter 10 in list → no `MISSING_NUMBER` row inserted for chapter 10.
  - Novel has no chapter 10. Re-crawl with chapter 10 → `MISSING_NUMBER` row inserted (existing behavior).

- `ChapterSorterTest`:
  - Existing chapters `[1,2,3]` for novel. Re-import MHT files for chapters `[1, 2, 3]` → all skipped (chapter-number dedup).
  - Mixed: existing `[1,2,3]`, re-import `[3, 4]` → only chapter 4 is new; `existingId` set for chapter 3, `new` row for chapter 4.

- `NovelDaoTest`:
  - `getNovelByTitleIgnoreCase` matches across case.
  - `setHasUpdates`/`clearHasUpdates` round-trip.

### UI
- `NovelCardBadgeTest` (Compose test):
  - `NovelCard(novel.copy(hasUpdates = true))` → the badge `Box` with primary color exists in the tree.
  - `NovelCard(novel.copy(hasUpdates = false))` → badge absent.
  - `LibraryViewModel.onIntent(OpenNovel(1))` → `novelDao.clearHasUpdates(1)` invoked (verify via fake DAO).

## Out of scope (v1 → v2 follow-ups)

- UI "Merge with existing novel…" picker (for titles that diverge beyond case).
- UI "Manage sources" per novel (list, promote, remove, set auto-update per source).
- Numeric badge (`newChapterCount`).
- Cover selection from multiple sources.
- "Longest content wins" replacement of existing chapters on re-import.
- Chapter-number remap between sites (for sites that number the same story differently).
- "Source" attribution on the reader screen (which site each chapter came from).

## Risks

1. **Migration backfill NULL domain**: handle in Kotlin with a `Cursor` loop after the SQL migration. Pre-compute `domain` from the URL during backfill to avoid a second migration. Test asserts the post-migration `domain` is populated.
2. **`ChapterUpdateCheckWorker` regression for the legacy 1-novel-1-source case**: covered by `ChapterUpdateCheckWorkerMultiSourceTest` baseline. The DAO JOIN includes `novels.autoUpdate=1` so the legacy behavior is preserved.
3. **`hasUpdates` set on the very first import**: mitigated by gating on `existingChapters.isNotEmpty() || targetNovelId != null` AND `importedChapters.isNotEmpty()`. A novel created and filled in one shot does **not** show the badge.
4. **Auto-update frequency**: 2 sources per novel = 2 worker iterations. The worker's existing backoff and pacing should scale, but if it becomes noisy, follow-up with a per-source `lastCheckedAt` minimum interval.
5. **`COLLATE NOCASE` only covers case, not Unicode normalization**: titles that differ by accents or whitespace still do not merge. Documented, follow-up.

## Commit plan

Two logical commits:

1. `feat(db): v8→v9 migration adds novel_sources + hasUpdates` — schema + DAO + NovelImporter multi-source. No UI yet.
2. `feat(webimport): cross-site novel merge + new-chapter badge` — dedup, targetNovelId plumbing, worker refactor, UI badge, status text.

## Verification

- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → all green (target: 230+ tests after Phase A and Phase B).
- `./gradlew :app:exportSchema` → produces 9.json → committed.
- `./gradlew :app:connectedDebugAndroidTest` for `MigrationTest` → green.
- On-device: import "Dragon King" from freewebnovel (200 caps). Then import "Dragon King" from readnovelfull → status "Merging into existing Dragon King". End result: 1 novel row, 200+ chapters (union), blue badge appears.
- On-device: tap the card → badge clears. Re-trigger background update (or wait) → if new chapters detected, badge re-appears.
- On-device: freewebnovel regression — a freewebnovel-only novel still gets checked and updated; no regression.
