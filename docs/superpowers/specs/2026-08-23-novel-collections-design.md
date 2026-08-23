# Design: Novel Collections (Coleções)

Date: 2026-08-23
Target version: v2.8.0

## Goal

Let the user organize novels into named collections (multi-membership, like
webnovel reading lists). A new "Coleções" tab lists collections; up to 3 can be
pinned as favorites and appear first. Novels can be added to collections from
the novel's 3-dot menu (multi-select) or in bulk from inside a collection.

## Decisions (from brainstorm)

- Multi-membership: a novel can be in several collections (needs join table).
- Navigation: a new "Coleções" tab (separate from Biblioteca/Capítulos/Personagens).
- Add-to-collection: both paths — per-novel (3-dot menu) and bulk (inside collection).
- Existing Favorites (isFavorite flag + FAVORITES filter + star menu) coexist;
  collections are a separate concept and no existing data is migrated.
- Drill-in is in-place via ViewModel state (`selectedFolder`), mirroring how
  `selectedNovel` drives the Capítulos/Personagens tabs. No new NavGraph route.
- Up to 3 collections can be pinned; pinned ones sort first in the Coleções tab.

## Data model (Room migration 11 -> 12)

New table `folders`:
- `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
- `name` TEXT NOT NULL
- `isPinned` INTEGER NOT NULL DEFAULT 0
- `createdAt` INTEGER NOT NULL

New join table `novel_folder`:
- `folderId` INTEGER NOT NULL, FK -> folders(id) ON DELETE CASCADE
- `novelId` INTEGER NOT NULL, FK -> novels(id) ON DELETE CASCADE
- PRIMARY KEY(folderId, novelId)

Entities: `FolderEntity`, `NovelFolderCrossRef`.
DAO: `FolderDao`.
`@Database` entities 8 -> 10; `version = 11` -> `12`; `MIGRATION_11_12` raw
`CREATE TABLE` for both + indexes; schema JSON committed to `app/schemas/`.

`NovelEntity` unchanged. Favorites (`isFavorite`) unchanged.

## FolderDao

- `getAll(): Flow<List<FolderEntity>>` (sorted pinned-first then by name)
- `getFolder(id): FolderEntity?`
- `insert(folder): Long`
- `rename(id, name)`
- `delete(id)`
- `setPinned(id, pinned)`
- `countPinned(): Int`
- `getNovelsInFolder(folderId): Flow<List<NovelEntity>>` (JOIN novels)
- `getFolderIdsForNovel(novelId): List<Long>`
- `addNovelToFolder(folderId, novelId)`, `removeNovelFromFolder(folderId, novelId)`
- `setNovelFolders(novelId, folderIds: Set<Long>)` — diff insert/delete in one call
- `addNovelsToFolder(folderId, novelIds: List<Long>)`

## UI

Tabs: with no novel selected -> `[Biblioteca][Coleções]` (0, 1). With a novel
selected -> `[Biblioteca][Capítulos][Personagens]` (0, 1, 2); Coleções hidden.
`deselectNovel()` resets to tab 0.

Coleções tab (no folder selected): `LazyVerticalGrid` of `CollectionCard`
(name, novel count, pin icon, cover collage of first 3-4 covers). FAB "+"
creates a collection (name dialog). Long-press card -> Rename / Pin(Unpin) /
Delete (with confirmation "Excluir coleção? Os novels não serão removidos.").
Empty -> "Nenhuma coleção, toque em +".

Drill-in (folder selected): content switches to a grid of the folder's novels
+ TopBar with the folder name and a back button (clears `selectedFolder`) +
"Adicionar novels" button (bulk multi-select dialog, searchable, pre-checks
existing members). Tapping a novel here opens it as usual.

Per-novel add: `NovelMenu` (3-dot) gains "Adicionar à coleção" ->
`AddToCollectionDialog` with checkboxes of all collections, pre-checked with
the novel's current collections; saving calls `setNovelFolders(novelId, ids)`.

Pin cap: `togglePin(id)` in ViewModel refuses to pin a 4th and emits an
`errorEvents` message ("Limite de 3 coleções fixadas" / "Limit of 3 pinned
collections"). Unpin always allowed.

## LibraryViewModel

- `folders: StateFlow<List<FolderEntity>>`
- `selectedFolder: StateFlow<FolderEntity?>`
- `novelsInFolder: StateFlow<List<NovelEntity>>` (derived when a folder is open)
- `folderMemberships: StateFlow<Map<Long, List<Long>>>` (folderId -> novelIds,
  for counts and pre-checks) — or compute counts from `getNovelsInFolder` per
  folder; chosen impl detail.
- methods: `createFolder(name)`, `renameFolder(id, name)`, `deleteFolder(id)`,
  `openFolder(id)`, `closeFolder()`, `togglePin(id)`, `setNovelFolders(novelId,
  ids)`, `addNovelsToFolder(folderId, novelIds)`, `removeNovelFromFolder(...)`.
- `selectNovel` also clears `selectedFolder`.

## DI (DatabaseModule)

- Expose `FolderDao`; add `MIGRATION_11_12` to `.addMigrations(...)`.

## New components

- `ui/library/tabs/CollectionsTab.kt` (list + drill-in)
- `ui/library/components/CollectionCard.kt`
- `ui/library/components/AddToCollectionDialog.kt`
- `ui/library/components/AddNovelsToCollectionDialog.kt`
- `ui/library/components/CollectionNameDialog.kt` (create + rename)
- Strings pt+en: `collections`, `create_collection`, `collection_name`,
  `add_to_collection`, `add_novels_to_collection`, `delete_collection`,
  `rename_collection`, `collection_empty`, `collection_novels_count`,
  `pin_collection`, `unpin_collection`, `pinned_collections_limit`.

## Tests (TDD: Robolectric + MockK + Turbine)

- `FolderDaoTest` (Robolectric in-memory DB): insert/getAll order (pinned
  first)/rename/delete (CASCADE on novel_folder), getNovelsInFolder, add/remove
  link, setNovelFolders diff, countPinned.
- `MigrationTest` (instrumented): 11 -> 12 schema + FKs + CASCADE.
- `LibraryViewModelTest`: folders flow, create/rename/delete, openFolder shows
  the right novels, togglePin caps at 3 and emits error on 4th, setNovelFolders
  calls DAO with diff, addNovelsToFolder, selectedFolder cleared on selectNovel.
- Existing tests unchanged.

## Out of scope (YAGNI / follow-up)

Drag-to-reorder collections, custom collection cover, nested collections,
export/import collections in the JSON backup (separate follow-up), auto
"Favorites" collection migration.

## Risks

- `selectedTab` int re-meaning (tab 1 = Coleções when no novel, Capítulos when
  novel selected). Covered by ViewModel tests + emulator verification.
- Deleting a collection only removes associations (CASCADE on join), novels
  stay. Confirmation copy makes this explicit.
- `setNovelFolders` does a diff (insert new, remove unchecked) rather than
  rewrite-all, in one DAO call.
