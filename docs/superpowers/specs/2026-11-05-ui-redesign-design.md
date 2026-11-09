# Nova UI: biblioteca e leitor (spec de design)

Data: 2026-11-05
Status: aguardando revisão do usuário
Escopo desta rodada: **biblioteca + leitor**. As demais telas (importação, favoritos, ajustes, sobre) só herdam a barra inferior e os tokens.

## 1. Problema e objetivo

O NovelReader é funcional, mas o visual é denso e pouco fluido: a biblioteca gasta altura com duas linhas de cabeçalho e uma grade de 2 colunas, Favoritos e Ajustes ficam escondidos como ícones no topo, o leitor mantém duas barras fixas sobre o texto, e as animações são todas um deslize de 220 ms sem resposta ao toque.

Objetivo: um app mais moderno, bonito e fluido para o usuário final, sem perder nada do que existe (paletas, acento, papel de parede, gestos, backup).

Critérios de sucesso, em ordem:
1. Mais conteúdo por tela (9 novels visíveis em vez de 4; texto em tela cheia no leitor).
2. Resposta imediata ao toque e animações interrompíveis.
3. Rolagem e abertura do leitor sem regressão em build release, medidas antes e depois.
4. Nenhuma regressão de comportamento, contraste ou acessibilidade.

## 2. Direção visual (decidida com mockups)

- Base: tema escuro com cards de capa em grade (opção C da exploração), barra inferior compacta (opção B).
- O layout novo vale para **as 6 paletas, claro e escuro**, com acento e papel de parede como hoje. O escuro mostrado vira o visual padrão de quem instala do zero. Nenhuma aparência já escolhida muda.
- Ícones: Material (`Icons.Default.*`, o mesmo conjunto que o app usa). Nada de emoji.
- Título sobre a capa: **faixa sólida opaca abaixo da capa** (tratamento 2). Todos os cards têm a mesma largura e altura; o título ocupa no máximo 2 linhas, com reticências, e a altura das 2 linhas é sempre reservada.

## 3. Não objetivos

- Redesenhar importação, favoritos, ajustes e sobre (só herdam tokens e a barra inferior).
- Trocar o motor do leitor (WebView), o parser, o banco ou o backup (exceto a chave nova da seção 6.4).
- Reintroduzir long-press ou seleção de texto no leitor.
- Dependências novas e APIs do Compose 1.13+ (o projeto está no BOM 2024.12.01).
- Mudar rotas, deep links ou `DeepLinkBus`.

## 4. Estrutura

### 4.1 Base de design (`ui/theme`, camada nova)

- **Tokens:** raios de canto, espaçamentos, alturas (barra inferior 46 dp, barra superior fina), tamanhos de ícone, durações e molas.
- **Componentes compartilhados:** `AppNavBar`, `NovelCover` (capa + faixa de título), `AppTopBar`, `FilterChips`.
- As regras do `AGENTS.md` ficam dentro dos componentes, num lugar só: containers opacos, texto atenuado por `mutedForeground`, contraste mínimo 4,5:1, containers que seguem o tom do papel de parede (`WallpaperVariantTheme`).
- `AppPaletteTest` passa a cobrir cada componente novo em todas as paletas e variantes.

### 4.2 Casca de navegação

- Barra inferior com 4 destinos: **Biblioteca, Coleções, Favoritos, Ajustes**. Ícone e rótulo em todos, 46 dp; o item ativo ganha uma pílula atrás do ícone.
- Saem: as abas Biblioteca/Coleções no topo e os ícones de Favoritos e Ajustes na barra superior.
- Fora da barra (tela cheia): Importar, Leitor, Sobre.
- Os contadores (novels, capítulos, marcadores) que hoje ocupam o `bottomBar` da biblioteca viram uma **linha fina de texto** (~20 dp) logo acima da barra de navegação, só na aba Biblioteca. O FAB continua acima do conjunto. `LibraryStatsBar` dá lugar a `LibraryStatsLine`.
- Detalhe de um novel (capítulos e personagens) continua dentro da biblioteca, **sem** a barra inferior, com voltar na barra superior.
- Troca entre destinos da barra: fade curto. Abrir leitor ou importar mantém o deslize.
- `Routes.*`, deep links e notificações não mudam.

## 5. Biblioteca

- **Barra superior de uma linha (~48 dp):** "Biblioteca", filtros Todos / Lendo / Fim (texto com sublinhado no ativo) e busca (`Icons.Default.Search`). Ordenar e alternar grade/lista vão para um menu "⋮". O filtro de novels favoritos (`NovelFilter.FAVORITES`, a estrela do novel) **continua**, como um quarto filtro só com ícone de estrela; ele é distinto do destino "Favoritos" da barra, que abre a tela de marcadores (`Routes.FAVORITES`, string `favorites`, "Bookmarks" em inglês). O rótulo do destino na barra usa `R.string.favorites`.
- **Grade:** `GridCells.Fixed(3)`. Capa 3:4 mais faixa de título de 2 linhas, cards de tamanho fixo. Selo de novos capítulos no canto da capa; progresso de leitura de 2 px no pé da capa.
- **Importação:** overlay "Importando" mantido; cada card recebe só o seu estado (`NovelImportOverlay`, já implementado nesta sessão) e não recompõe com o progresso dos outros.
- **Lista:** mesmo `NovelCover` em miniatura à esquerda, título de 2 linhas à direita.
- **Menu do novel:** abre por toque longo com feedback tátil. O "⋮" por card sai (3 colunas ficam apertadas).
- **Preservado:** chaves por **posição** nas listas (o scroll não pula ao reabrir; `LibraryTabScrollTest`), `LibraryBrowseState` salvo, FAB acima da barra inferior.

## 6. Leitor imersivo

### 6.1 Estados

- **Padrão:** só o texto em tela cheia.
- **Toque no centro:** mostra juntas a barra superior fina (voltar, título do capítulo, busca) e a barra de opções flutuante na base (anterior, marcador, ajustes, capítulos, próximo), com os ícones e formas da biblioteca. Some sozinha em 4 s.

### 6.2 Preservado

Tap zones e swipe (vertical/horizontal/ambos/nenhum), sem long-press nem seleção de texto; painel de ajustes rolável com `ValueSlider`; `ReaderJsInterface` postando na thread da view; política de toque em `ReaderViewModel.tapActionAt`; brilho, timer de sono e fonte.

### 6.3 Barras translúcidas sobre papel de parede

O texto continua sem atenuação (o contraste depende da imagem), como o `AGENTS.md` exige.

### 6.4 Opção "Indicador de bateria"

- Nos ajustes do leitor, com `OptionLabel`. **Desligado (padrão):** a bateria some com o resto e só aparece ao tocar no centro. **Ligado:** fica sempre visível, discreta, mesmo no modo imersivo.
- O percentual de leitura não existe hoje no leitor (a faixa de status mostra só bateria e timer); fica fora desta entrega.
- O contador do timer de sono, enquanto roda, fica sempre visível (evita saída automática por surpresa).
- Chave nova em `reader_prefs`, saneada em `PreferenceAllowlists` (valor inválido vira "desligado"). Entra no backup v3.

### 6.5 Decisão a registrar

A decisão da v2.9.2 (barra superior sempre visível) é substituída. Registrar em `docs/DECISIONS.md` com data e motivo; atualizar a seção do leitor do `AGENTS.md`.

## 7. Movimento e fluidez

- Resposta ao pressionar (escala leve até 0,97 e retorno), em cards e botões.
- Molas (`spring`) nas animações que o usuário toca: amortecimento 1,0 por padrão; ~0,8 só quando o gesto tem impulso.
- Animações interrompíveis, partindo do valor atual (toque no meio reverte sem salto).
- Respeitar "remover animações" do sistema (durações zero).
- Checklist da skill `jetpack-compose-performance`: leituras de estado animado adiadas para draw/layout, `contentType` nas listas, sem `onGloballyPositioned`. Referência de sensação: `apple-design` (somente leitura, nada instalado).

## 8. Fatias de entrega

Cada fatia compila, passa nos testes e deixa o app utilizável.

1. Base de design: tokens e componentes, com testes de contraste por paleta.
2. Casca de navegação: barra inferior, fade, `NavGraph` e `LibraryScreen`.
3. Biblioteca: barra superior, grade 3×3, cards fixos, menu, lista.
4. Leitor imersivo: barras por toque, indicador de bateria opcional, painel de ajustes restilizado.
5. Movimento e fluidez: molas, resposta ao toque, "remover animações", auditoria de performance.

## 9. Testes e validação

- Robolectric e JVM puros para componentes e regras. Sem regressão em `LibraryTabScrollTest`, `AppPaletteTest`, `LibraryStatsBarTest`, `ChaptersTabScrollTest` e os testes do leitor.
- Gestos e brilho do leitor verificados em dispositivo (o Robolectric não os pega, ver `AGENTS.md`).
- Por fatia: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`.
- Fluidez: build release no emulador com **muitos novels** (50+) e uma importação ativa; comparar antes e depois (janky frames na rolagem da biblioteca e na abertura do leitor). Medir com poucos novels não prova nada (tentativa anterior com 1 novel foi inconclusiva).

## 10. Execução

- Implementação por agentes Pi invocados com `hc` (herdr core); eu reviso e integro.
- Um briefing por fatia em `.work/briefs/<fatia>-<papel>.md` (git-ignored): objetivo, arquivos permitidos, validações, caminho do retorno.
- Um agente por vez, em worktree próprio, sem commit, sem push, sem editar spec ou tickets. Retorno em arquivo, com resumo curto.
- Eu leio o retorno, reviso o diff, rodo a validação e integro. Falha que sobreviver a duas correções vai ao usuário.
- Fatias 2 e 4 (maior risco) pedem uma revisão independente com outro modelo.
- Terminais de outros projetos abertos no `hc` não são tocados.

## 11. Riscos

- Barra inferior mexe na navegação: risco de quebrar deep links de notificação. Mitigação: `Routes` e `DeepLinkBus` intactos e testados.
- Toque longo como único acesso ao menu do novel reduz a descoberta. Mitigação: observar no uso; reverter para o "⋮" é uma mudança pequena.
- Leitor sem barras: o toque central passa a ser a única forma de ver os controles; os tap zones já existentes têm de continuar coerentes.
- Paletas claras com o desenho novo: cada componente precisa de contraste verificado em todas as 12 combinações.

## 12. Decisões em aberto

Nenhuma bloqueia a fatia 1. Para confirmar na revisão: (a) manter reticências em títulos longos (decidido); (b) quando o menu "⋮" da barra superior reunir ordenar e alternar grade/lista, a ordem dos itens.
