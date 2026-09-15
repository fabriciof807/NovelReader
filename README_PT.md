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

- Seis paletas (Índigo, Papel, Grafite, Floresta, Ameixa, AMOLED) mais a cor dinâmica do sistema no Android 12+
- Cor de acento própria, escolhida em separado para o app e para o leitor
- Tamanho de fonte, altura da linha, scroll automático ajustáveis
- Cada capítulo lembra onde você parou (mesmo se matar o app)
- **Busca em texto completo** — procure uma palavra ou frase em todos os capítulos de uma novel
- Bookmarks com notas — marque passagens importantes

### Deixe do seu jeito

- **Papel de parede da biblioteca** e um **papel de parede separado para o leitor** — use uma imagem sua ou um dos fundos prontos
- Desfoque em cada papel de parede, na intensidade que você quiser
- **Véu de leitura** — controle quanto do fundo do leitor fica entre o texto e o papel de parede, para a foto nunca custar legibilidade
- O tema do leitor pode seguir o app ou ficar fixo numa variante clara/escura
- **Temas salvos** — guarde até cinco visuais (paleta, cores de acento e tema do leitor) e alterne entre eles; trocar de tema nunca mexe no seu papel de parede
- **Papel de parede atrás das barras** — escolha se a barra de cima e as abas ficam translúcidas sobre o papel de parede, ou sólidas como eram
- **Restaurar aparência** — um botão devolve paleta, acentos, papéis de parede, desfoque e véu ao padrão, mantendo seus temas salvos

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

### v2.10.0 (2026-09-14)

- Feat: seis paletas (Índigo, Papel, Grafite, Floresta, Ameixa, AMOLED) com variante clara e escura, substituindo a escolha simples de claro/escuro/dinâmico; a cor dinâmica vira uma das opções de paleta.
- Feat: cor de acento editável para o app e, em separado, para o leitor — o app deriva um tom legível para a matiz/saturação escolhida, em vez de deixar um acento claro lavar a interface (verificado contra o fundo de todas as paletas).
- Feat: papéis de parede — um para a biblioteca e um para o leitor, cada um com desfoque próprio, usando imagem sua (copiada para o armazenamento privado do app, nunca no backup) ou um dos oito fundos prontos.
- Feat: slider de véu de leitura (padrão 80%) e paleta + variante clara/escura próprias do leitor, para o papel de parede nunca ganhar do texto.
- Fix: o sheet de configurações do leitor agora rola; com as seções novas a metade de baixo ficava inalcançável no celular.
- Temas antigos do leitor (`light`, `dark`, `sepia`, `gray`) mapeiam exatamente para as paletas novas, então quem já lia mantém as cores.
- Feat: até cinco temas salvos (paleta, cores de acento e tema do leitor) mais um botão de restaurar aparência que os mantém.
- Fix: os sliders gravavam a cada frame do arrasto — e o leitor reaplicava o CSS pela ponte JS em cada um deles — então o thumb prendia e pulava; agora o valor grava uma vez, ao soltar.
- Feat: opção de manter o papel de parede visível atrás da barra de cima e das abas (translúcido, com véu na cor do tema) ou deixar essas barras sólidas como antes; ligada por padrão, valendo para biblioteca e leitor.
- 650 testes unitários passando (eram 506).

### v2.9.3 (2026-09-14)

Endurecimento de dependências, tema do leitor e acessibilidade:

- **Leitor**: o sheet de configurações ganhou o chip **Auto**, então dá para voltar a seguir o tema do app depois de escolher uma cor (antes essa escolha ficava inalcançável sem limpar os dados do app)
- **A11y**: os chips de tema são anunciados como radio buttons, e não como views marcáveis genéricas
- **Segurança**: `jsoup` 1.22.1 → 1.23.2, fechando CVE-2026-71497 (o advisory só ocorre em safelists que permitem elementos raw-text, o que o `READER_SAFELIST` nunca permitiu); fixtures dos parsers passam sem mudança
- **Segurança**: advisories da toolchain do landing page corrigidos no lockfile
- **Docs**: o registro de riscos residuais explica por que migrar o bridge JS do leitor para `addWebMessageListener` não reduziria risco neste app

Sem migração de dados necessária.

### v2.9.2 (2026-09-09)

Endurecimento de segurança de uma auditoria externa, mais correções do leitor:

- **Segurança**: a importação de backup é protegida contra injeção CSS/JS via `fontFamily`, contra SSRF em `sourceUrl` restaurado (hosts loopback, privados e link-local são rejeitados) e contra valores arbitrários de `photoPath`
- **Segurança**: os parsers casam hosts exatamente, e deep links de notificação carregam um token por instalação
- **Segurança**: o WebView do leitor usa nonce de CSP por carga (sem `unsafe-inline`), o desafio do Cloudflare valida o host antes de carregar, respostas de DNS privadas são bloqueadas e corpos de resposta são limitados a 8 MiB (16 MiB descomprimidos)
- **Leitor**: título do capítulo de volta no topo do conteúdo (cabeçalhos duplicados da fonte são removidos), barra superior sempre visível, barra de status inferior com a bateria, barra de opções com um único toque
- **Leitor**: mudanças de configuração, favorito e tema aplicam na página aberta sem recarregar, e o capítulo carregado antes da morte do processo é restaurado (antes voltava para um capítulo obsoleto)
- **Correção**: links de capítulo `http://` numa página `https` são promovidos em vez de rejeitados
- **Novo**: o tema do leitor segue o tema do app por padrão

Sem migração de dados necessária.

### v2.9.0 (2026-08-23)

Coleções e backup completo:

- **Coleções**: criar e fixar coleções e adicionar novels a elas pelo menu da novel
- **Backup v3**: exporta novels (autor, total de capítulos, atualização automática, último capítulo lido), favoritos e personagens, e agora também coleções e configurações; a restauração pendente é aplicada quando o download em background termina
- **Importação**: importações só de configurações são suportadas, e novels apenas locais são reportadas como não restauráveis
- **Banco**: Room v11 → v12 (`folders`, `novel_folder`)

O Room migra automaticamente (v11 → v12).

### v2.7.5 (2026-08-23)

Polimento do leitor e da biblioteca:

- **Leitor**: alternar os controles com um toque curto; a lista de capítulos rola até o capítulo atual; cabeçalho da fonte que duplica o título do capítulo é removido
- **Biblioteca**: o badge da novel mostra a contagem de capítulos novos, com fallback para o ponto
- **Importação**: novels na fila de importação em background aparecem como "Aguardando importação" até chegarem

Sem migração de dados necessária.

### v2.7.0 (2026-08-20)

- **Leitor**: controles alternam com toque longo, ordem reversa da lista de capítulos, e a posição de scroll ao vivo é capturada via JS para retomar certo ao voltar de um capítulo
- **Biblioteca**: sheet "What's New" ao abrir o app quando há capítulos novos
- **Interno**: o dispatcher `LibraryIntent` foi substituído por chamadas diretas ao ViewModel; código morto removido
- **Banco**: Room v10 → v11 (`chapters.isNew`)

O Room migra automaticamente (v10 → v11).

### v2.6.0-fix (2026-08-04)

Correções do swipe no leitor:

- Swipe vertical só troca de capítulo nos limites da página
- O capítulo anterior volta ao fim depois de um swipe para cima

Sem migração de dados necessária.

### v2.6.0 (2026-08-02)

Favoritos, cancelamento direcionado e correções de importação:

- **Biblioteca**: novels favoritas (toggle e filtro no menu de 3 pontos) e backup JSON seletivo
- **Importação**: fila por novel com cancelamento direcionado de jobs em background
- **Leitor**: transição direcional de entrada ao trocar de capítulo por gesto, e posição de scroll correta restaurada entre navegações
- **Correção**: um `StackOverflowError` derrubava o leitor a cada swipe (os campos de callback do bridge sombreavam os métodos anotados)
- **Correção**: o FreeWebNovel importa a lista completa de capítulos (antes só os 40 primeiros), e capas são gravadas em bytes binários exatos
- **Banco**: Room v9 → v10

O Room migra automaticamente (v9 → v10).

### v2.5.4 (2026-07-17)

- **Leitor**: direção do swipe configurável (vertical, horizontal, ambos ou nenhum), estado de capítulo vazio com recuperação via MHT, reimportação de arquivo MHT/HTML para um capítulo existente, e erros do leitor com ação de Retry
- **Biblioteca**: menu de 3 pontos visível nos cards, entrada "Capítulos", validação inline de HTTPS no diálogo de capa, e fim da exclusão acidental de personagem por swipe
- **Notificações**: tocar numa notificação de importação abre a seção de capítulos falhos e rola até ela
- **Hápticos**: padronizados (só toque longo)

Sem migração de dados necessária.

### v2.5.3 (2026-07-10)

Confiabilidade da importação:

- "Retry all" reenfileira todos os capítulos falhos
- Retries com backoff e classificação explícita de rate-limit (429), pacing de 5 s e rejeição de conteúdo vazio ou obsoleto

Sem migração de dados necessária.

### v2.5.2 (2026-06-30)

Importação multi-fonte e checagem de atualização por fonte:

- **Importação**: uma novel pode ter várias fontes, e a checagem de atualização itera todas elas
- **Biblioteca**: ponto azul e badge de contagem quando a novel tem capítulos novos
- **Parsers**: novo layout do FreeWebNovel, arquivo de capítulos do ReadNovelFull e augmentação de lista por domínio
- **Cloudflare**: cookies persistidos por domínio e checagem de host do desafio cobrindo qualquer domínio
- **Correção**: páginas 404 não são mais capturadas como conteúdo de capítulo
- **Banco**: Room v8 → v9 (`novel_sources`, `hasUpdates`)

O Room migra automaticamente (v8 → v9).

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
| Leitor (view model, HTML builder, sheet, tema) | Unit | 137 |
| Parsers | Unit | 78 |
| Use cases + import web | Unit | 143 |
| Telas de biblioteca / lista de capítulos / configurações | Unit | 55 |
| Preferências, storage, tema e customização | Unit | 87 |
| Workers, navegação, favoritos, diversos | Unit | 96 |
| **Total (JVM)** | | **650** |

`./gradlew :app:testDebugUnitTest` roda toda a suíte JVM; a suíte de DAOs abaixo precisa de emulador.

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
