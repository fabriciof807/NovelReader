# Nova UI (biblioteca e leitor) Implementation Plan

> **For agentic workers:** execução pelo método escolhido pelo usuário: um agente Pi por tarefa, invocado com `hc run pi` a partir de um briefing em `.work/briefs/<tarefa>-impl.md`; o coordenador revisa o diff, roda a validação e integra. Passos usam `- [ ]`. Workers **não** comitam, não fazem push e não editam spec nem plano.

**Goal:** biblioteca em grade 3×3 com barra de navegação inferior de 4 destinos, leitor imersivo com indicador de bateria opcional, e movimento com molas, mantendo paletas, papel de parede, gestos e backup.

**Architecture:** camada de tokens em `ui/theme` e componentes compartilhados em `ui/components` (novos); a casca de navegação coloca `AppNavBar` no `bottomBar` das três telas de topo (Biblioteca/Coleções, Favoritos, Ajustes) sem tela-casca única, para não mexer em `Routes` nem em deep links; o leitor passa a sobrepor as barras ao texto quando o usuário toca no centro.

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2024.12.01 (Compose 1.7, sem APIs 1.13+), Material3, Room 12 (sem mudança), DataStore, Hilt, Robolectric/MockK/Turbine.

**Spec:** `docs/superpowers/specs/2026-11-05-ui-redesign-design.md`

## Global Constraints

- Sem comentários no código, salvo PT-BR onde inevitável (`AGENTS.md`).
- Strings sempre bilíngues: `values/` em inglês e `values-pt/` em português.
- Containers de Material **opacos** (nunca `copy(alpha=...)`); texto atenuado só por `mutedForeground(foreground, background)`; contraste mínimo `MinContrast` = 4.5.
- Sobre papel de parede, containers usam `libraryContainerColor`/`barColorFor` e a variante do tom do wallpaper (`WallpaperVariantTheme`); `barColorFor` é chamado dentro do wrapper.
- Chaves por **posição** nas listas de novels; `LibraryBrowseState` saveable; `LibraryTabScrollTest` deve continuar verde.
- `pointerInput` que sobrevive a recomposição lê estado vivo (`rememberUpdatedState`); callbacks do `ReaderJsInterface` postam na thread da view; sliders em tela rolável são `ValueSlider`.
- Sem long-press no leitor e sem seleção de texto; tap zones e swipe inalterados.
- `Routes.*`, `DeepLinkBus` e deep links inalterados.
- Sem dependências novas; sem APIs do Compose 1.13+.
- Validação de cada tarefa: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`.
- Contagem atual: 943 testes; nenhum pode ser removido ou enfraquecido (só substituído quando o componente testado é substituído, com a cobertura equivalente).

## Review Focus

1. Título de novel vazio, só espaços, com 200 caracteres, CJK ou emoji: `NovelCover` mantém a altura fixa e não estoura.
2. Backup antigo sem a chave nova, ou com valor inválido: o leitor cai em "indicador desligado".
3. Deep link/notificação abrindo o detalhe de um novel: a barra inferior fica oculta, e voltar leva à Biblioteca, não a outra pilha.
4. Escala de fonte 2.0 e "remover animações" ligado: a barra de 46 dp não corta rótulos de modo ilegível e as animações viram instantâneas.
5. Busca no leitor ativa com as barras escondidas: a barra superior com o campo de busca permanece visível enquanto a busca estiver ativa.

---

## File Structure

| Arquivo | Responsabilidade |
| --- | --- |
| `ui/theme/AppTokens.kt` (novo) | `AppDimens`, `AppShapes`, `AppMotion` |
| `ui/components/AppNavBar.kt` (novo) | `AppDestination`, `AppNavBar` |
| `ui/components/NovelCover.kt` (novo) | `NovelCover` (capa + faixa de título) |
| `ui/components/AppTopBar.kt` (novo) | barra superior fina reutilizável |
| `ui/library/components/LibraryStatsLine.kt` (novo, substitui `LibraryStatsBar.kt`) | contadores em linha fina |
| `ui/library/components/LibraryTopBar.kt` (novo) | linha única: título, filtros, busca, menu ⋮ |
| `ui/reader/ReaderChrome.kt` (novo) | regra pura de visibilidade das barras do leitor |

---

### Task 1: Tokens e componentes base

**Files:**
- Create: `app/src/main/java/com/novelreader/ui/theme/AppTokens.kt`, `ui/components/AppNavBar.kt`, `ui/components/NovelCover.kt`, `ui/components/AppTopBar.kt`
- Test: `app/src/test/java/com/novelreader/ui/components/AppNavBarTest.kt`, `NovelCoverTest.kt`; estender `ui/theme/AppPaletteTest.kt`

**Interfaces:**
- Produces:
  - `object AppDimens { val NavBarHeight = 46.dp; val TopBarHeight = 48.dp; val StatsLineHeight = 20.dp; val CardRadius = 12.dp; val GridSpacing = 6.dp }`
  - `object AppMotion { const val FadeMillis = 150; fun <T> press(): SpringSpec<T>; fun <T> settle(hasMomentum: Boolean): SpringSpec<T>; fun reduced(scale: Float): Boolean }` onde `reduced` é verdadeiro quando a escala de animação do sistema é 0.
  - `enum class AppDestination { LIBRARY, COLLECTIONS, FAVORITES, SETTINGS }`
  - `@Composable fun AppNavBar(selected: AppDestination, onSelect: (AppDestination) -> Unit, wallpaperActive: Boolean = false, modifier: Modifier = Modifier)`
  - `@Composable fun NovelCover(novel: NovelEntity, readProgress: Float, newChapterCount: Int, importOverlay: NovelImportOverlay, onClick: () -> Unit, onLongClick: () -> Unit, wallpaperActive: Boolean = false, modifier: Modifier = Modifier)`
  - `@Composable fun AppTopBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit)`

- [ ] **Step 1: Write failing tests**
  - `AppNavBarTest`: `renders four items with labels from R.string.library, collections, favorites, settings`; `selected item exposes selected=true and the others false`; `clicking an item calls onSelect with its AppDestination`; `bar height is at most AppDimens.NavBarHeight plus the navigation bar inset`.
  - `NovelCoverTest`: `a one-line and a three-line title produce the same card height`; `title longer than two lines is ellipsized (maxLines = 2)`; `blank title still renders at the same height`; `title with 200 CJK characters keeps the card width`; `progress bar is absent when readProgress is 0`.
  - `AppPaletteTest`: `nav bar label and indicator clear MinContrast on every palette and variant`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*AppNavBarTest" --tests "*NovelCoverTest" --tests "*AppPaletteTest"`. Expected: FAIL (símbolos não existem).
- [ ] **Step 3: Implement** os símbolos da seção Interfaces. `AppNavBar` usa `NavigationBar` sem elevação, container opaco (`libraryContainerColor` com `wallpaperActive`), indicador em pílula atrás do ícone, ícones `Icons.Default.LibraryBooks`/`Folder`/`Bookmark`/`Settings`, rótulos `stringResource(R.string.library|collections|favorites|settings)`, a inset da barra de navegação aplicada ao **conteúdo** (`windowInsetsPadding` dentro do `Surface`, como `LibraryStatsBar` faz). `NovelCover` reserva a altura de duas linhas de `bodySmall` com `Modifier.heightIn(min=...)` calculada a partir de `LocalTextStyle`, faixa de título em `MaterialTheme.colorScheme.surface` opaca, capa `aspectRatio(3f/4f)` com `AsyncImage`, overlay conforme `importOverlay`, progresso de 2 dp no pé da capa, selo de novos capítulos; modificador de pressão com `AppMotion.press()`.
- [ ] **Step 4: Run** o comando do Step 2. Expected: PASS.
- [ ] **Step 5: Run** `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`. Expected: BUILD SUCCESSFUL, 0 falhas.

---

### Task 2: Casca de navegação e contadores em linha

**Files:**
- Modify: `ui/navigation/NavGraph.kt`, `ui/library/LibraryScreen.kt`, `ui/favorites/FavoritesScreen.kt`, `ui/settings/SettingsScreen.kt`
- Create: `ui/library/components/LibraryStatsLine.kt`; **Delete** `LibraryStatsBar.kt`
- Test: `ui/navigation/TopLevelNavigationTest.kt` (novo); substituir `LibraryStatsBarTest.kt` por `LibraryStatsLineTest.kt`

**Interfaces:**
- Consumes: `AppDestination`, `AppNavBar`, `AppDimens.StatsLineHeight`, `AppMotion.FadeMillis` (Task 1).
- Produces:
  - `fun topLevelRoute(destination: AppDestination): String` em `NavGraph.kt` (`LIBRARY`/`COLLECTIONS` → `Routes.LIBRARY`, `FAVORITES` → `Routes.FAVORITES`, `SETTINGS` → `Routes.SETTINGS`).
  - `fun destinationFor(route: String?, selectedTab: Int, hasSelectedNovel: Boolean): AppDestination?`; devolve `null` quando o detalhe de um novel está aberto (barra oculta).
  - `@Composable fun LibraryStatsLine(stats: LibraryStats, wallpaperActive: Boolean, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write failing tests**
  - `TopLevelNavigationTest`: `topLevelRoute maps each destination`; `destinationFor library tab 0 without novel is LIBRARY`; `destinationFor library tab 1 without novel is COLLECTIONS`; `destinationFor library with a selected novel is null`; `destinationFor favorites route is FAVORITES`.
  - `LibraryStatsLineTest` (porta do `LibraryStatsBarTest`): `the FAB clears the stats line and the nav bar` (assert de gap em relação à borda superior da linha, como o teste atual faz); `counters show novels, chapters and bookmarks`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*TopLevelNavigationTest" --tests "*LibraryStatsLineTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.** `NavGraph`: rotas de topo (`LIBRARY_WITH_SELECTION`, `FAVORITES`, `SETTINGS`) usam `fadeIn/fadeOut(tween(AppMotion.FadeMillis))`; `IMPORT`, `READER`, `ABOUT` mantêm o deslize. A seleção na barra navega com `navigate(topLevelRoute(d)) { popUpTo(Routes.LIBRARY) { saveState = true }; launchSingleTop = true; restoreState = true }`; escolher `COLLECTIONS` chama `viewModel.selectTab(1)` sem navegar. `LibraryScreen`: `bottomBar = { Column { if (selectedTab == 0 && stats.totalNovels > 0) LibraryStatsLine(...); if (selectedNovel == null) AppNavBar(...) } }`; remover as `Tab` Biblioteca/Coleções do `TabRow` (o `TabRow` fica só com Capítulos/Personagens quando há novel selecionado) e os ícones de Favoritos e Ajustes do topo. `FavoritesScreen` e `SettingsScreen` ganham `AppNavBar` no `bottomBar` do seu `Scaffold`.
- [ ] **Step 4: Run** o comando do Step 2. Expected: PASS.
- [ ] **Step 5: Run** a suíte completa. Expected: 0 falhas; `LibraryTabScrollTest`, `WallpaperBarsTest` e testes de deep link inalterados e verdes.

---

### Task 3: Biblioteca (barra superior, grade 3×3, lista, menu)

**Files:**
- Create: `ui/library/components/LibraryTopBar.kt`
- Modify: `ui/library/tabs/LibraryTab.kt`, `ui/library/components/NovelListItem.kt`, `ui/library/LibraryScreen.kt`; **Delete** `ui/library/components/NovelCard.kt` (substituído por `NovelCover`)
- Test: `ui/library/tabs/LibraryTabGridTest.kt` (novo), `ui/library/components/LibraryTopBarTest.kt` (novo); manter `LibraryTabScrollTest`, `LibraryTabFavoriteIconTest`, `LibraryTabQueuedImportTest`

**Interfaces:**
- Consumes: `NovelCover`, `AppTopBar`, `AppDimens` (Task 1); `NovelImportOverlay`, `novelImportOverlay`, `queuedTitleKeys` (já existem).
- Produces: `@Composable fun LibraryTopBar(browse: LibraryBrowseState, viewMode: ViewMode, onSortSelected: (SortOrder) -> Unit, onViewModeChange: (ViewMode) -> Unit, wallpaperActive: Boolean, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write failing tests**
  - `LibraryTabGridTest`: `the grid lays out three columns`; `all novel cards have the same width and height`; `long-press on a card opens its menu`; `no per-card options button is shown`.
  - `LibraryTopBarTest`: `title, four filters and search share one row`; `the favorite filter is an icon-only star with a content description`; `sort and view mode live in the overflow menu`; `selecting a filter updates browse.filterChip`.
  - Manter os testes de scroll: `opening the topmost visible novel keeps the viewport` deve passar com `contentType` e chaves por posição.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*LibraryTabGridTest" --tests "*LibraryTopBarTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.** `LazyVerticalGrid(columns = GridCells.Fixed(3), ...)` com `contentType = NOVEL_CONTENT_TYPE`; itens chamam `NovelCover(...)` com `onLongClick = { menuNovelId = novel.id }` e o `NovelMenu` já existente (lambda `expanded`). `NovelListItem` usa `NovelCover` em miniatura à esquerda (largura fixa 56 dp) e título de 2 linhas. `LibraryTopBar` num `AppTopBar` de uma linha: "Biblioteca", chips Todos/Lendo/Fim (texto, sublinhado no ativo) e estrela (`NovelFilter.FAVORITES`), `Icons.Default.Search`, menu ⋮ com Ordenar (três opções atuais) e Alternar grade/lista. `LibraryScreen` troca o `TopAppBar` atual por `LibraryTopBar` quando `selectedTab == 0 && selectedNovel == null`. Novas strings pt/en para rótulos que ainda não existem.
- [ ] **Step 4: Run** o comando do Step 2. Expected: PASS.
- [ ] **Step 5: Run** a suíte completa. Expected: 0 falhas.

---

### Task 4: Preferência "Indicador de bateria" (dados e backup)

**Files:**
- Modify: `data/local/preferences/ReaderPreferences.kt`, `domain/usecase/ExportDataUseCase.kt`, `domain/usecase/ImportDataUseCase.kt`, `ui/reader/ReaderViewModel.kt`, `ui/reader/ReaderSettingsSheet.kt`, `res/values/strings.xml`, `res/values-pt/strings.xml`
- Test: `ReaderPreferencesTest`, `ExportDataUseCaseTest`, `ImportDataUseCaseTest`, `ReaderViewModelTest`, `ReaderSettingsSheetTest`

**Interfaces:**
- Produces: `ReaderConfig.batteryAlwaysOn: Boolean = false`; `ReaderPreferences.updateBatteryAlwaysOn(value: Boolean)`; `ReaderViewModel.updateBatteryAlwaysOn(value: Boolean)`; chave DataStore `reader_battery_always_on`; chave de backup `batteryAlwaysOn`; strings `reader_battery_always_on` e `reader_battery_always_on_desc`.

- [ ] **Step 1: Write failing tests** (siga o caminho do `tapZones`, que passa pelos mesmos sete pontos):
  - `ReaderPreferencesTest`: `batteryAlwaysOn defaults to false`; `updateBatteryAlwaysOn persists and is read back`.
  - `ExportDataUseCaseTest`: `reader settings include batteryAlwaysOn`.
  - `ImportDataUseCaseTest`: `restore applies batteryAlwaysOn true`; `a backup without the key leaves it false`; `a non-boolean value is ignored`.
  - `ReaderViewModelTest`: `updateBatteryAlwaysOn writes through to preferences`.
  - `ReaderSettingsSheetTest`: `the battery toggle shows its title and reflects config.batteryAlwaysOn`; `toggling calls onBatteryAlwaysOnChange`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*ReaderPreferencesTest" --tests "*ExportDataUseCaseTest" --tests "*ImportDataUseCaseTest" --tests "*ReaderViewModelTest" --tests "*ReaderSettingsSheetTest"`. Expected: FAIL.
- [ ] **Step 3: Implement** nos sete pontos do `tapZones` (config, chave, update, export, import, ViewModel, `ToggleRow` na `ReaderSettingsSheet` com `OptionLabel` via `ToggleRow(title, description, checked, onCheckedChange)` e parâmetro `onBatteryAlwaysOnChange`), mais as strings pt/en.
- [ ] **Step 4: Run** o comando do Step 2. Expected: PASS.
- [ ] **Step 5: Run** a suíte completa. Expected: 0 falhas.

---

### Task 5: Leitor imersivo

**Files:**
- Create: `ui/reader/ReaderChrome.kt`
- Modify: `ui/reader/ReaderScreen.kt`, `ui/reader/ReaderTopBar.kt`, `ui/reader/ReaderStatusBar.kt`
- Test: `ui/reader/ReaderChromeTest.kt` (novo), `ReaderStatusBarTest`, testes de tela existentes do leitor

**Interfaces:**
- Consumes: `ReaderConfig.batteryAlwaysOn` (Task 4); `AppDimens`, `AppMotion` (Task 1).
- Produces:
  - `data class ReaderChromeState(val topBar: Boolean, val optionsBar: Boolean, val status: Boolean, val sleepPill: Boolean)`
  - `fun readerChrome(optionsVisible: Boolean, searchActive: Boolean, batteryAlwaysOn: Boolean, sleepRunning: Boolean): ReaderChromeState`

- [ ] **Step 1: Write failing tests** (`ReaderChromeTest`, JVM puro):
  - `default state hides everything`: `readerChrome(false,false,false,false)` → todos falsos.
  - `tap shows top bar, options bar and status`: `optionsVisible=true` → `topBar && optionsBar && status`.
  - `battery always on keeps status without tap`: `batteryAlwaysOn=true, optionsVisible=false` → `status` verdadeiro e `topBar`/`optionsBar` falsos.
  - `search active forces the top bar`: `searchActive=true, optionsVisible=false` → `topBar` verdadeiro.
  - `running sleep timer keeps the sleep pill visible`: `sleepRunning=true, optionsVisible=false, batteryAlwaysOn=false` → `sleepPill` verdadeiro e `status` falso.
  - Em `ReaderStatusBarTest`: `translucent bar keeps full-strength text` continua verde.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*ReaderChromeTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.** `ReaderScreen`: o `Scaffold` deixa de usar `topBar`/`bottomBar` fixos; o conteúdo ocupa a tela toda com `statusBarsPadding` e `navigationBarsPadding`; `readerChrome(...)` decide o que é composto; barra superior e barra de opções aparecem como sobreposição com `AnimatedVisibility` (fade + slide curto, `AppMotion`), e o auto-hide de 4 s existente (`LaunchedEffect(isOptionsVisible)`) continua. Indicador de bateria e percentual de leitura obedecem a `chrome.status`; o contador do timer a `chrome.sleepPill`. Barras translúcidas mantêm texto sem atenuação (`ReaderStatusBar` inalterado nessa regra). O `onTap` do WebView continua alternando `isOptionsVisible` por `ReaderViewModel.tapActionAt`.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest --tests "*ReaderChromeTest" --tests "*ReaderStatusBarTest" --tests "*ReaderViewModelTest"`. Expected: PASS.
- [ ] **Step 5: Run** a suíte completa. Expected: 0 falhas.
- [ ] **Step 6 (dispositivo):** instalar `app:installDebug` no emulador; verificar com um capítulo real que: toque no centro mostra e esconde as barras; busca ativa mantém a barra superior; swipes e tap zones continuam; rolagem não é coberta pela barra de status do sistema. Registrar o resultado em `.work/returns/task5.md`.

---

### Task 6: Movimento e fluidez

**Files:**
- Modify: `ui/components/NovelCover.kt`, `ui/components/AppNavBar.kt`, `ui/reader/ReaderScreen.kt`, `ui/navigation/NavGraph.kt`, `ui/theme/AppTokens.kt`
- Test: `ui/theme/AppMotionTest.kt` (novo)

**Interfaces:**
- Consumes: `AppMotion` (Task 1).
- Produces: `@Composable fun rememberReducedMotion(): Boolean` em `AppTokens.kt` (lê `Settings.Global.ANIMATOR_DURATION_SCALE`); `Modifier.pressable(onClick, onLongClick)`.

- [ ] **Step 1: Write failing tests** (`AppMotionTest`): `reduced is true when animator scale is 0`; `press spring is critically damped (dampingRatio 1f)`; `momentum spring bounces (dampingRatio below 1f)`; `with reduced motion the fade duration is zero`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*AppMotionTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.** Escala 0,97 ao pressionar em `NovelCover` e itens da barra, usando `graphicsLayer { }` com valor lido na fase de draw; `AppMotion.settle(hasMomentum)` nas animações do leitor; `rememberReducedMotion()` zera durações no `NavGraph` e nas barras do leitor. Checklist da skill `jetpack-compose-performance` aplicado aos arquivos tocados (`contentType`, sem `onGloballyPositioned`, leituras de estado adiadas).
- [ ] **Step 4: Run** o comando do Step 2. Expected: PASS.
- [ ] **Step 5: Run** a suíte completa. Expected: 0 falhas.

---

### Task 7: Documentação, decisão e medição

**Files:**
- Modify: `docs/DECISIONS.md`, `AGENTS.md` (seções do leitor e da barra inferior da biblioteca), `.gitignore` (adicionar `.superpowers/` e `.work/`)
- Create: `.work/returns/measurement.md` (git-ignored)

- [ ] **Step 1:** em `docs/DECISIONS.md`, com data 2026-11-05: o leitor passa a ser imersivo (substitui a barra superior sempre visível da v2.9.2); Favoritos e Ajustes viram destinos da barra inferior; contadores viram `LibraryStatsLine`; o indicador de bateria é opcional.
- [ ] **Step 2:** atualizar no `AGENTS.md` as seções "Library bottom bar" (agora barra de navegação + linha de contadores), "Reader gestures" (barras por toque) e a contagem de testes.
- [ ] **Step 3 (medição):** gerar build release; instalar no emulador com **50 ou mais novels** e uma importação ativa (montar com arquivos locais); medir `dumpsys gfxinfo` na rolagem da biblioteca e na abertura do leitor, antes (APK de `1475a41`) e depois; registrar janky frames e percentis em `.work/returns/measurement.md`. Sem 50 novels a medição não vale (ver spec §9).
- [ ] **Step 4: Run** `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`. Expected: BUILD SUCCESSFUL.

---

## Ordem e dependências

`1 → 2 → 3` em sequência. `4` é independente de 1–3 e pode rodar antes ou depois, mas `5` depende de `1` e `4`. `6` depois de `1–5`. `7` por último. Um agente por vez, em worktree próprio; revisão independente com outro modelo nas Tasks 2 e 5.
