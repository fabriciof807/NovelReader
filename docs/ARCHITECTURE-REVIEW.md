# Architecture Review — TchelvisNovels

**Data:** 20/06/2025
**Objetivo:** Identificar módulos rasos (shallow modules) e propor oportunidades de aprofundamento (deepening) para melhorar testabilidade e navegabilidade do código.

---

## Ranking (Maior → Menor Impacto)

| # | Candidato | Impacto | Esforço | Linhas |
|---|-----------|---------|---------|--------|
| 1 | `WebImportUseCase` | Alto | Médio | 389 |
| 2 | `ImportNovelUseCase` | Alto | Médio | 239 |
| 3 | `LibraryViewModel` | Alto | Médio | 553 |
| 4 | `ParserRegistry` / `MhtParser` | Médio | Baixo | 130 |
| 5 | Repositories (5 arquivos) | Médio | Baixo | ~200 |

---

## Top Recommendation

**Colapsar `WebImportUseCase`**

At 389 linhas, é o monolito mais raso e de maior impacto. Ele lida com HTTP, parsing HTML, paginação, escrita em banco e I/O de capa — tudo sem uma única seam. Decompô-lo em `ChapterCrawler`, `ChapterFetcher` e `CoverDownloader` melhora imediatamente a testabilidade e dá a cada bug um único lar.

Uma vez decomposto, o mesmo padrão se aplica a `ImportNovelUseCase`.

---

## 1. `WebImportUseCase` — Monolito de Importação Web

**Impacto:** Alto · **Esforço:** Médio · **Arquivos:** 1 + dependências

### Arquivos

| Arquivo | Linhas |
|---------|--------|
| `domain/usecase/WebImportUseCase.kt` | 389 |
| `domain/usecase/WebImportUseCaseTest.kt` | — |

### Problema

Um único use case acumula toda responsabilidade da importação web: HTTP fetching, parsing HTML, extração de links de capítulos, paginação (next page), rate limiting, retry, inserção em banco, download de capa, e progress reporting. O método `invoke()` esconde 389 linhas de código indiferenciado. **Deletion test:** remover a classe destrói a feature inteira porque não existe seam interna.

Módulos rasos aninhados dentro dele:

| Método privado | Função | Linhas |
|----------------|--------|--------|
| `fetchChapterList()` | Crawleia lista paginada de capítulos | ~50 |
| `extractChapterLinks()` | Heurística de extração de links | ~77 |
| `importChapters()` | Fetch individual + DB write | ~80 |
| `findNextPageUrl()` | Heurística de paginação | ~25 |
| `saveCoverImage()` | Download + I/O de arquivo | ~19 |
| `extractNovelTitle()` | Extração de título da URL/HTML | ~16 |

### Solução

Decompor em 4 módulos com seams claras:

```
WebImportUseCase (orquestração pura)
  ├── ChapterCrawler      → Paginação & discovery de capítulos
  ├── ChapterFetcher       → HTTP + parsing individual
  ├── CoverDownloader      → I/O de capa
  └── NovelImporter        → Inserção transactional em banco
```

### Benefícios

- **Locality:** bugs de paginação buscam um único módulo
- **Leverage:** `ChapterFetcher` testado uma vez, reusado por foreground + background worker
- **Interface:** de 1 método gigante para 4 contratos focados
- **Transaction boundary:** `NovelImporter` explicita a fronteira atômica (rollback se algo falhar)
- **Testes:** cada módulo testado isoladamente; use case vira um teste de integração

---

## 2. `ImportNovelUseCase` — Pipeline de Importação Local

**Impacto:** Alto · **Esforço:** Médio · **Arquivos:** 1

### Arquivos

| Arquivo | Linhas |
|---------|--------|
| `domain/usecase/ImportNovelUseCase.kt` | 239 |

### Problema

Script procedural sem seams. `parseUri()` coordena `ParserRegistry`, `MhtParser`, `HtmlSanitizer` e `StringUtils` — mas cada passo é um método privado, impossível de testar isoladamente. `detectCharset()` é pura mas enterrada no monolito.

| Método privado | Função |
|----------------|--------|
| `detectCharset()` | Detecção de charset de arquivo |
| `parseUri()` | Coordenação de múltiplos parsers |
| `groupByNovel()` | Agrupamento de arquivos por novela |
| `sortChapters()` | Ordenação por `ChapterNumberExtractor` |
| `insertToDb()` | Escrita em banco |

### Solução

Pipeline composto:

```
ImportPipeline
  ├── FileCharsetDetector  → Detecta charset do arquivo
  ├── NovelGrouper          → Agrupa arquivos por novela
  ├── ChapterSorter         → Ordena capítulos (centraliza ChapterNumberExtractor)
  └── ChapterInserter       → Inserção transactional
```

### Benefícios

- **Locality:** bug de ordenação busca `ChapterSorter`
- **Leverage:** `ChapterSorter` centraliza uso de `ChapterNumberExtractor` (antes espalhado em 3 lugares)
- **Deletion test:** pode substituir estratégia de agrupamento sem tocar em código de banco
- **Testabilidade:** cada stage do pipeline testado com stub/fake

---

## 3. `LibraryViewModel` — God Object

**Impacto:** Alto · **Esforço:** Médio · **Arquivos:** 1

### Arquivos

| Arquivo | Linhas |
|---------|--------|
| `ui/library/LibraryViewModel.kt` | 553 |
| `ui/library/LibraryViewModelTest.kt` | — |

### Problema

God object com 15+ StateFlows. `LibraryIntent` tem 40+ variantes mas `onIntent` é um bloco `when` gigante que redireciona para métodos privados.

**Padrão repetido 8+ vezes:**

```kotlin
viewModelScope.launch {
    try { characterRepository.xxx(...); refreshCharacters(novelId) }
    catch(e) { _errorEvents.emit(...) }
}
```

**MVI raso:** `LibraryState` (34 linhas) só expõe um subconjunto do estado real da ViewModel.

Responsabilidades na mesma classe:

- Novel CRUD
- Character CRUD
- Character Photo CRUD
- Cover Management
- Import State
- Sorting
- View Mode

### Solução

Extrair use cases:

```
LibraryViewModel (~150 linhas)
  ├── CharacterManagementUseCase  → CRUD de personagens
  ├── CoverManagementUseCase       → Gerenciamento de capas
  ├── NovelLibrary                 → Operações de biblioteca
  └── BackgroundImportObserver     → Estado de importação em background
```

### Benefícios

- **Leverage:** Character logic vive em um lugar, não duplicado em 8 blocos `try/catch`
- **Locality:** um bug de character → um use case
- **Interface:** de 553 linhas para ~150
- **Testes:** testes miram use cases, não setup complexo de ViewModel (Hilt, coroutines, etc.)

---

## 4. `ParserRegistry` / `MhtParser` — Vazamento na Seam

**Impacto:** Médio · **Esforço:** Baixo · **Arquivos:** 2

### Arquivos

| Arquivo | Linhas |
|---------|--------|
| `data/parser/ParserRegistry.kt` | 130 |
| `data/parser/MhtParser.kt` | — |
| `data/parser/GenericFallbackParser.kt` | — |

### Problema

`ParserRegistry.parseRaw()` tem conhecimento profundo dos internos de `MhtParser`. Ele chama `isMhtFile()`, `extractHtml()`, `extractSubject()`, e implementa heurísticas próprias de título (`titleFromMhtSubject`, `chapterTitleFromMhtSubject` — 45 linhas).

Isso não é dispatching — é implementar parsing MHT dentro do registry. A abstração vaza.

**Título duplicado em 5 lugares:**

| Local | Função de título |
|-------|------------------|
| `ParserRegistry.titleFromMhtSubject()` | Heurística MHT |
| `GenericFallbackParser.parseNovelTitle()` | Split por pipe/dash |
| `FreeWebNovelParser.parseNovelTitle()` | Split por pipe/dash |
| `ReadNovelFullParser.parseNovelTitle()` | Split por pipe/dash |
| `WebImportUseCase.extractNovelTitle()` | Extração de URL |

### Solução

```
ParserRegistry (dispatch uniforme via canParse)
  ├── MhtParser (implements NovelParser) → MHT agora entra pelo multibinding
  ├── FreeWebNovelParser
  ├── ReadNovelFullParser
  └── TitleExtractor → utility compartilhada
```

`MhtParser` deve ser um `NovelParser` completo, com `canParse()` e `parse()`. O registry não precisa mais de casos especiais MHT.

### Benefícios

- **Locality:** parsing MHT em um módulo só
- **Menos um branch `when` no registry**
- **TitleExtractor** deduplica lógica de extração de título em 1 lugar (vs 5 hoje)

---

## 5. Repositories — Inline ou Deepen

**Impacto:** Médio · **Esforço:** Baixo · **Arquivos:** 5

### Arquivos

| Arquivo | Linhas |
|---------|--------|
| `data/repository/NovelRepository.kt` | ~40 |
| `data/repository/ChapterRepository.kt` | ~60 |
| `data/repository/BookmarkRepository.kt` | ~30 |
| `data/repository/CharacterRepository.kt` | ~30 |
| `data/repository/CharacterPhotoRepository.kt` | ~30 |

### Problema

Quase todo método de repositório é passagem direta para DAO:

```kotlin
suspend fun getNovelById(id: Long): NovelEntity? = novelDao.getNovelById(id)
```

Mas `ChapterRepository` vaza lógica de domínio:

- `searchInNovel()` (linhas 44-53) — constrói queries FTS. Isso é lógica de domínio (construção de query) vazada no repository.
- `reNormalizeOrderIndices()` (linhas 32-42) — busca todos capítulos, ordena por `ChapterNumberExtractor`, e atualiza. Isso é ordenação de domínio, não persistência.

**Deletion test:** deletar um repository significa inlinar `dao.method()` nos use cases. Eles não adicionam seam ou abstração.

### Solução

Dois caminhos:

**Opção A — Inline (recomendado):**
- Deletar os 5 repositories (~200 linhas de boilerplate)
- Use cases chamam DAOs diretamente (injetados via Hilt)
- `ChapterRepository.searchInNovel()` → `FtsSearchService`
- `ChapterRepository.reNormalizeOrderIndices()` → `ChapterSorter` (do candidate #2)

**Opção B — Deepen:**
- Manter repositories, mas adicionar gerenciamento de transação e validação de domínio
- Repositories viram módulos profundos com responsabilidade real (transactional boundaries, domain invariants)

### Benefícios

- **Deletion test confirma:** inlining DAO calls é trivial → módulos são realmente rasos
- **Se inline:** remove 5 arquivos rasos (~200 linhas de boilerplate)
- **Se deepen:** transaction safety na fronteira de importação
- **Lógica de FTS e ordenação** move para onde pertence (use cases / services)

---

## Glossário Arquitetural

| Termo | Significado |
|-------|-------------|
| **Module** | Unidade de código com responsabilidade coesa (ex: use case, parser) |
| **Interface** | Contrato público de um módulo (métodos, parâmetros, retornos) |
| **Depth (profundidade)** | Proporção entre complexidade da interface e complexidade da implementação. Módulo profundo = interface pequena, implementação rica |
| **Shallow (raso)** | Interface quase tão complexa quanto a implementação (ex: repositories DAO-wrappers) |
| **Seam** | Ponto onde um módulo pode ser substituído sem alterar seus consumidores |
| **Adapter** | Implementação concreta atrás de uma seam. Uma seam hipotética = um adapter. Duas seams reais = dois adapters |
| **Leverage (alavancagem)** | Um módulo testado uma vez, usado em N lugares |
| **Locality (localidade)** | Ao ler um bug, você não precisa sair do módulo para entender o contexto |
| **Deletion test** | "Deletar este módulo concentra complexidade ou só move?" Se "move", é raso |

---

## Histórico

| Data | Mudança |
|------|---------|
| 20/06/2025 | Revisão inicial com 5 candidatos de deepening |
| 23/06/2026 | v2.4.0 — 6 correções de bug em `LibraryScreen` / `LibraryViewModel` / `ChaptersTab` / `PersonagensTab` / `ScanMissingChaptersUseCase`. Resolve `LibraryViewModel` god-object risk identificado nesta revisão ao fixar `requestDeleteById`/`requestChangeCoverById`/`requestCoverByUrlById` (que liam o campo morto `_state.value.novels`) e ao adicionar `BackHandler` explícito em `LibraryScreen` para deseleção de novel. Detalhes em `docs/superpowers/specs/2026-06-23-issue-fixes-design.md` e `docs/superpowers/plans/2026-06-23-issue-fixes.md`. |
