# NovelReader

**Um leitor de novels para Android que funciona 100% offline.**

Importe novels de sites da web ou de arquivos HTML/MHT que você já tem, leia com conforto, marque onde parou, pesquise em todos os capítulos, e mantenha fichas dos personagens — tudo sem precisar de internet depois de importar, sem cadastro, sem rastreamento, sem nuvem.

---

## Para que serve

NovelReader é para quem lê muito novel/web novel/light novel e quer:

- **Ler offline** — importe uma vez, leia em qualquer lugar (metrô, avião, sem sinal). Os capítulos ficam no seu dispositivo.
- **Não depender de um site específico** — se o site sair do ar, mudar de URL ou colocar paywall, sua biblioteca continua intacta.
- **Organizar uma biblioteca grande** — busca full-text, ordenação por título/data/última leitura, filtro por status, contadores de capítulos e bookmarks.
- **Manter o controle de personagens** — para novels com muitos personagens (xianxia, fantasy), crie fichas com fotos, notas e favoritos.
- **Personalizar a leitura** — tema claro/escuro, tamanho da fonte, altura da linha, scroll automático.
- **Não ser rastreado** — nenhum analytics, nenhuma conta, nenhum servidor próprio. Tudo é local.

---

## O que dá para fazer

### Importar novels

- **Da web** — cole a URL de uma novel em sites suportados; o app descobre a lista de capítulos, baixa o conteúdo e organiza tudo. Downloads rodam em segundo plano com notificação de progresso.
- **De arquivos locais** — selecione arquivos HTML ou MHT pelo app de arquivos do Android. Útil para novels que você já salvou de outros lugares.
- **Auto-update** — novelas com URL de origem podem ser verificadas periodicamente; novos capítulos são baixados sozinhos.

### Ler

- Tema claro ou escuro
- Tamanho de fonte, altura da linha, scroll automático ajustáveis
- Cada capítulo lembra onde você parou (mesmo se matar o app)
- **Busca em texto completo** — procure uma palavra ou frase em todos os capítulos de uma novel
- Bookmarks com notas — marque passagens importantes

### Organizar personagens

Para novels com muitos personagens, cada novel tem uma aba de **Personagens** onde você pode:
- Criar fichas com nome, foto, notas
- Adicionar várias fotos por personagem (galeria)
- Favoritar os principais
- Importar fichas prontas do site MVLEMPYR (parceria com base de dados de personagens)

### Verificar o que deu errado

Quando uma importação falha (URL fora do ar, página de erro no lugar do conteúdo, arquivo corrompido), o app:
- Salva o capítulo com falha no banco
- Mostra na aba "Capítulos com falha" da novel
- Permite **re-tentar o download** (se a URL voltou) ou **importar um arquivo MHT manualmente** para aquele capítulo
- Detecta também capítulos com conteúdo vazio ou ausente (escaneando por número de capítulo)

---

## Como usar

1. **Instale o APK** (veja a seção de build no final ou baixe uma release)
2. **Abra o app** — tela inicial mostra a biblioteca
3. **Toque no "+"** para importar uma novel
4. **Escolha a fonte**:
   - **Web**: cole a URL da novel → selecione os capítulos → import inicia
   - **Arquivos**: selecione arquivos HTML/MHT do seu dispositivo
5. **Toque na novel** na biblioteca para ver os capítulos
6. **Toque em um capítulo** para começar a ler
7. **Use o menu do capítulo** para favoritar, marcar como lido, ver bookmarks

A aba de **Personagens** aparece quando você seleciona uma novel. A aba de **Favoritos** (no menu superior) mostra todos os seus bookmarks em um só lugar.

---

## Privacidade

- Nenhum analytics, telemetria ou tracking
- Nenhuma conta ou login
- Nenhum servidor próprio — o app não envia nada para lugar nenhum
- Imports da web usam apenas HTTPS e a URL que você fornece
- Todos os dados (novels, capítulos, bookmarks, personagens, fotos) ficam no seu dispositivo

---

## Idiomas

Português (padrão) e Inglês. Configurável em **Configurações**.

---

## Screenshots

| Biblioteca | Leitor | Personagens |
|---|---|---|
| ![Biblioteca](screenshots/library.png) | ![Leitor](screenshots/reader.png) | ![Personagens](screenshots/characters.png) |

---

## Sites suportados para import da web

- **FreeWebNovel**
- **ReadNovelFull**
- **Qualquer página HTML** (parser genérico)

Para novels de sites não listados, o parser genérico tenta extrair o conteúdo principal. Funciona bem em sites com estrutura simples.

---

## Histórico de versões

### v2.4.3 (2026-06-26)

Polimento de UI/UX e infraestrutura:

- **Leitor**: lista de capítulos no bottom sheet agora quebra em até 4 linhas (antes 1)
- **Biblioteca**: estado vazio com ilustração e CTA "Adicione sua primeira novel"
- **Biblioteca**: badge "Lendo" agora mostra tempo relativo (ex. "Lendo · há 2 h")
- **Biblioteca** e **Capítulos**: posição de scroll é lembrada entre visitas (por novel na aba de capítulos)
- **Aba Personagens**: ExtendedFAB com rótulos para Adicionar e Importar
- **A11y**: auditoria de `contentDescription` em 42 botões só com ícone (0 alterações funcionais)
- **Hápticos**: feedback háptico leve ao adicionar bookmark, tocar FAB e trocar de aba
- **i18n**: traduções completas para inglês (todas as chaves pt-BR espelhadas em `values-en`)
- **Cor dinâmica**: toggle opt-in em Configurações (Android 12+)
- **Transições de aba**: slide de 220ms entre biblioteca, leitor e configurações
- **Infraestrutura de teste**: base de testes Compose UI (Robolectric)

Sem migração de dados necessária.

---

## Próximos passos

Veja o `git log` para o histórico de melhorias arquiteturais e os arquivos `handoff-*.md` (gitignored) para o estado atual do projeto.

---

# Seção técnica

<details>
<summary>Detalhes para desenvolvedores</summary>

## Stack

| Componente | Versão |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.2.1 |
| Jetpack Compose (BOM) | 2024.12.01 |
| Material 3 | (via Compose BOM) |
| Hilt | 2.59.2 |
| Room | 2.8.4 |
| KSP | 2.3.9 |
| Jsoup | 1.23.2 |
| Coil | 2.7.0 |
| DataStore | 1.1.3 |
| WorkManager | 2.10.0 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Compile SDK | 35 |
| JVM | 17 |

## Arquitetura

MVVM + UseCase + Hilt DI, com fluxo unidirecional:

```
Compose -> ViewModel -> UseCase -> DAO
                        |-> Parser (Set<NovelParser> via multibinding Hilt)
```

- ViewModels expõem `StateFlow` para a UI e `SharedFlow<String>` para erros
- `ChapterFetcher` e parsers injetados via multibinding; `GenericFallbackParser` é o catch-all
- Imports em background via WorkManager (modos SEQUENCIAL e PARALLEL)
- `DeepLinkBus` conecta notificações de update à navegação
- Banco Room v8 com 7 entidades, 5 DAOs, FTS4 para busca
- `ChapterOrderNormalizer` reordena capítulos por número após import
- `RetryChapterUseCase` e `ScanMissingChaptersUseCase` para recuperação de falhas

## Estrutura do projeto

```
app/src/main/java/com/novelreader/
  MainActivity.kt                       Activity única; handle deep links
  di/                                   Módulos Hilt (Database, Parser, Storage, Work, Dispatchers)
  data/
    local/db/                           Room: 7 entidades, 5 DAOs, FTS4, 8 migrations
    local/preferences/                  DataStore (App, Reader, Import, Library)
    parser/                             Parsers HTML/MHT (multibinding Hilt)
    storage/                            CoverStorage (file I/O)
    remote/                             MvlempyrCharacterImporter
    worker/                             WorkManager workers
  domain/usecase/                       Lógica de negócio
    webimport/                          ChapterCrawler, ChapterFetcher, CoverDownloader, NovelImporter
    importnovel/                        FileCharsetDetector, NovelGrouper, ChapterSorter, ChapterInserter
    RetryChapterUseCase, ScanMissingChaptersUseCase, ChapterOrderNormalizer
  ui/
    navigation/                         NavGraph + DeepLinkBus
    library/                            Abas: novels, chapters, characters
    reader/                             WebView com bookmarks e busca
    import_novel/                       Tela de import local
    webimport/                          Tela de import web
    favorites/                          Todos os bookmarks
    settings/                           Tema, idioma, modo de fila
    theme/                              Cores, tipografia
  util/                                 LocaleHelper
```

## Build

```bash
./gradlew :app:assembleDebug            # APK debug
./gradlew :app:installDebug             # Instala no dispositivo
./gradlew :app:compileDebugKotlin       # Compila só (rápido)
```

## Testes

```bash
./gradlew :app:testDebugUnitTest            # Testes JVM (não precisa de emulador)
./gradlew :app:connectedDebugAndroidTest    # Testes instrumentados (precisa de emulador)
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest  # Antes de commit
```

| Suíte | Tipo | ~Contagem |
|---|---|---|
| Parsers | Unit | 40 |
| ViewModels | Unit | 17 |
| Use Cases | Unit | 13 |
| E2E / Regressão | Unit | 18 |
| DAOs | Instrumentado | 30 |
| UI Screens | Instrumentado | 6 |
| **Total** | | **~124** |

Mais detalhes em [`README-TESTES.md`](README-TESTES.md).

## Banco de dados

Room v8. 7 entidades (`Novel`, `Chapter`, `Bookmark`, `Character`, `CharacterPhoto`, `FailedChapter` + `ChapterFts`). 8 migrations manuais. FTS4 sobre `chapters.title` e `chapters.content`.

## i18n

`pt` (padrão) e `en`. Configurável em runtime via `AppPreferences` (DataStore). Mudança de idioma recria a Activity.

## Contributing

Veja [`CONTRIBUTING.md`](CONTRIBUTING.md) para guidelines detalhados.

## Licença

MIT — veja [`LICENSE`](LICENSE).

</details>
