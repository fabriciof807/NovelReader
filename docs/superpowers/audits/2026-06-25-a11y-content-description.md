# A11y contentDescription audit (2026-06-25)

## Outcome

Audit completed 2026-06-25. All 42 `contentDescription = null` instances in the UI code were classified as decorative (paired with adjacent text that already conveys the icon's purpose). No code or string-resource changes were required. Two separate a11y issues were observed (unlabeled clickable Boxes) and are noted as out-of-scope follow-ups below.

## Summary

After auditing all 42 `contentDescription = null` instances in the UI code, **every single instance is decorative** — each is paired with adjacent text that already conveys the icon's purpose. Per the brief's own rule ("Decorative icons paired with adjacent text are left as-is"), **zero new `cd_*` string resources were added and zero code changes were made**.

The brief's starter list of 12 expected `cd_*` strings was based on a spec-author estimate; the brief explicitly allows adjusting the list ("Add/remove keys as needed"). After classification, the correct adjusted list is empty.

No commit was made (nothing to commit — see the original session report at `.git/sdd/task-8-report.md` for the full commit discussion, which has been stripped from this permanent copy).

## Classification table

All 42 instances are decorative. Detailed classification:

| # | File:Line | Icon | Paired with | Class |
|---|-----------|------|-------------|-------|
| 1 | LibraryScreen.kt:210 | SortByAlpha | DropdownMenuItem text "Título" | decorative |
| 2 | LibraryScreen.kt:215 | DateRange | DropdownMenuItem text "Data" | decorative |
| 3 | LibraryScreen.kt:220 | Sort | DropdownMenuItem text "Última leitura" | decorative |
| 4 | NovelCard.kt:71 | AsyncImage (cover) | Card title text below | decorative |
| 5 | AddCharacterDialog.kt:65 | AsyncImage (photo) | "Adicionar foto" button below | decorative |
| 6 | AddCharacterDialog.kt:74 | Add | OutlinedButton text "Adicionar foto" | decorative |
| 7 | CharacterCard.kt:144 | AsyncImage (cover) | Character name text | decorative |
| 8 | CharacterCard.kt:171 | PhotoCamera | Text "Adicionar foto" | decorative |
| 9 | CharacterCard.kt:262 | AsyncImage (thumb) | Inside clickable Box; Box itself has no label (separate a11y issue, not in scope) | decorative |
| 10 | ImportProgressBanner.kt:67 | Download | Banner text "Importando X" + progress | decorative |
| 11 | ImportProgressBanner.kt:149 | Schedule | Row text (novel title) | decorative |
| 12 | LibraryEmptyState.kt:40 | MenuBook (large) | "Adicione sua primeira novel" + body text | decorative |
| 13 | LibraryTab.kt:267 | PhotoCamera | DropdownMenuItem text "Alterar capa" | decorative |
| 14 | LibraryTab.kt:272 | Language | DropdownMenuItem text "Capa via URL" | decorative |
| 15 | LibraryTab.kt:282 | Refresh | DropdownMenuItem text "Ativar verificação de atualizações" | decorative |
| 16 | LibraryTab.kt:287 | Search | DropdownMenuItem text "Verificar atualizações agora" | decorative |
| 17 | LibraryTab.kt:292 | Refresh | DropdownMenuItem text "Resincronizar todos os capítulos" | decorative |
| 18 | LibraryTab.kt:298 | Delete | DropdownMenuItem text "Deletar" | decorative |
| 19 | ChaptersTab.kt:218 | CloudOff | Row text "Capítulos com falha (N)" | decorative |
| 20 | ChaptersTab.kt:267 | CloudOff | Row text "Capítulos com falha (0)" | decorative |
| 21 | ChaptersTab.kt:341 | Refresh | TextButton text "Re-tentar" | decorative |
| 22 | ChaptersTab.kt:351 | FileDownload | TextButton text "Importar arquivo" | decorative |
| 23 | PersonagensTab.kt:168 | Search | OutlinedTextField placeholder text | decorative |
| 24 | PersonagensTab.kt:250 | Public | ExtendedFAB text "Importar" | decorative |
| 25 | PersonagensTab.kt:261 | Add | ExtendedFAB text "Adicionar" | decorative |
| 26 | PersonagensTab.kt:298 | AsyncImage (pager) | Standalone in pager | decorative |
| 27 | PersonagensTab.kt:314 | PhotoCamera | Text "Adicionar foto" below | decorative |
| 28 | ImportScreen.kt:290 | AsyncImage (cover) | Novel title text above | decorative |
| 29 | ReaderSettingsSheet.kt:153 | TouchApp | Row text "Rolagem automática" | decorative |
| 30 | BookmarkDialogs.kt:79 | Bookmark | Row text (bookmark title) | decorative |
| 31 | BookmarkDialogs.kt:118 | Add | Button text "Adicionar" | decorative |
| 32 | CreateCharacterDialog.kt:59 | AsyncImage (photo) | "Adicionar foto" button below | decorative |
| 33 | CreateCharacterDialog.kt:70 | Add | OutlinedButton text "Adicionar foto" | decorative |
| 34 | FavoritesScreen.kt:98 | Bookmark (large) | "Nenhum favorito ainda" + body text | decorative |
| 35 | SettingsScreen.kt:318 | PhoneAndroid | ThemeOption text "Sistema" | decorative |
| 36 | SettingsScreen.kt:326 | BrightnessHigh | ThemeOption text "Claro" | decorative |
| 37 | SettingsScreen.kt:334 | DarkMode | ThemeOption text "Escuro" | decorative |
| 38 | SettingsScreen.kt:385 | FileDownload | Card row text "Exportar dados" | decorative |
| 39 | SettingsScreen.kt:416 | FileUpload | Card row text "Importar dados" | decorative |
| 40 | SettingsScreen.kt:447 | Info | Card row text "Sobre" | decorative |
| 41 | SettingsScreen.kt:484 | ExpandLess/ExpandMore | Row text (section title) | decorative |
| 42 | SettingsScreen.kt:529 | Language | LangOption text (language name) | decorative |

**Functional instances: 0**
**Decorative instances: 42**

## What I tested and test results

- `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL (pre-change, no changes made)
- No code changes, so no test re-run needed (existing unit test suite still applies)

## Files changed

None (UI code and string resources). Only a doc commit records the audit result.

## Self-review findings

1. **Classification rigor:** Each instance was re-checked against the brief's two-pronged rule (decorative = paired with adjacent text; functional = icon-only button or state indicator). Every instance falls into the decorative bucket because the adjacent text (DropdownMenuItem text, button labels, section titles, card titles, placeholders) already conveys what the icon represents.

2. **Brief's starter list:** The 12 expected `cd_*` strings in the brief were the spec author's guess at what might be functional. The brief explicitly says "Adjust the actual list to match what you find. Add/remove keys as needed." The correct adjusted list is empty — all 12 candidates are decorative.

3. **State indicators:** Considered whether the CloudOff icons (ChaptersTab.kt:218, 267) and the expand/collapse chevron (SettingsScreen.kt:484) should be treated as functional "state indicators." But the brief's general rule ("Decorative icons paired with adjacent text are left as-is") applies: both are paired with adjacent text that already conveys the state (section title "Capítulos com falha (N)" and section title "Tema do App" respectively). The whole row is clickable in both cases, so TalkBack announces the clickable row with the title.

4. **Separate a11y issues observed (not in scope):** Two clickable elements have no semantic label at all, which is a different a11y problem:
   - `CharacterCard.kt:262` — the clickable Box wrapping a thumbnail AsyncImage has no `contentDescription` or `semantics` modifier. The fix would be on the Box, not the AsyncImage.
   - `PersonagensTab.kt:304-324` — the clickable Box in the photo pager "add photo" slot has no label. Again, the fix would be on the Box.
   These are out of scope for this task (which is specifically about `contentDescription = null` on Icons/Images).

5. **Count discrepancy:** The brief said 39 instances; the actual count is 42. This is a minor discrepancy (the brief was an estimate). The classification and outcome are unaffected.

## Concerns

- The brief's expected outcome (adding ~12-18 strings) didn't materialize. This is a significant deviation from the brief's plan. The brief's classification rules were followed strictly, and the rules clearly say "Decorative icons paired with adjacent text are left as-is." If the user disagrees with this interpretation and wants strings added for some of these cases (e.g., to give TalkBack users a more descriptive label than the adjacent text), the work would need to be re-scoped.
- No commit was made. If a commit is required, one can be created with an adjusted message.
