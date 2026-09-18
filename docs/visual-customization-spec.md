# Customização visual — v2.10.0

Documento de referência do que foi implementado (temas, acento e wallpapers).
Decisões de produto fechadas em sessão; a implementação segue fiel a elas.

## 1. Escopo

| Tema | Decisão |
| --- | --- |
| Paletas | 6: `indigo` (atual), `papel`, `grafite`, `floresta`, `ameixa`, `amoled` |
| Cor de acento | Duas chaves independentes: `accent_color` (app) e `reader_accent_color` (leitor) |
| Wallpaper | Dois slots globais independentes: home (biblioteca) e leitor |
| Alcance | Global. Não há wallpaper por novel |
| Origem | Imagem do usuário (SAF) + 11 fundos embutidos (8 gradientes + 3 cores sólidas) |
| Blur | Slider 0..60 por slot |
| Véu do leitor | Slider 0..100%, default 80%, na cor de fundo da paleta do leitor |

## 2. Paletas

`ui/theme/AppPalette.kt` é a fonte única. Cada paleta tem `light`/`dark`
(`ColorScheme`) e `readerLight`/`readerDark` (`ReaderSurface`: `bg`, `text`,
`accent`, `link` em hex CSS).

| Canônico | Legado | Reader light (bg/text) | Reader dark (bg/text) |
| --- | --- | --- | --- |
| `indigo` | `light`, `dark` | `#f5f0e8`/`#333333` | `#0a0a0f`/`#e0e0e0` |
| `papel` | `sepia` | `#f4e4c1`/`#5b4636` | `#241e17`/`#e0d3bd` |
| `grafite` | `gray` | `#ececec`/`#2b2b2b` | `#2d2d2d`/`#d0d0d0` |
| `floresta` | — | `#eaf3ea`/`#24332a` | `#101a14`/`#cfe3d4` |
| `ameixa` | — | `#f7ecf3`/`#3a2233` | `#1b1020`/`#e5d6ec` |
| `amoled` | — | `#000000`/`#e8e8e8` | `#000000`/`#e8e8e8` |

`amoled` é preto em **ambas** as variantes, sem camadas de elevação — é isso que
o distingue de `grafite` no escuro.

Os aliases legado são resolvidos em `PreferenceAllowlists.sanitizeReaderTheme`,
que devolve o par canônico (`indigo:light`, `papel:light`, …). Nenhuma migração
de dados: quem já usava Sépia ou Cinza mantém exatamente as mesmas cores.

## 3. Acento

Acento do app entra como `primary`; `onPrimary` sai de `contrastOn`, que escolhe
entre `#1a1a1a`, branco e preto a primeira cor que atinge 4.5:1.

O hex final de uma escolha do usuário vem de `accentHexFor(hue, saturação,
fundo)`: busca binária na *lightness* HSL até atingir 5:1 contra a cor de fundo
da paleta ativa. Mapeamento linear de HSV não serve — azul saturado em fundo
escuro e amarelo em fundo claro ficam abaixo de 4.5:1 de qualquer jeito.

Assim, qualquer matiz/saturação que o usuário escolha continua legível; o teste
`the derived accent stays readable over every palette background` cobre 6
paletas × 2 variantes × 24 matizes × 3 saturações.

A escolha é feita numa **roda** (`HueWheel`): o ângulo escolhe a matiz (0° à
direita, crescendo no sentido horário, igual ao `Brush.sweepGradient` que a
desenha) e a distância do centro escolhe a saturação. Como a *lightness* é
derivada, a roda só expõe os dois eixos que existem de fato: um terceiro eixo
deixaria o usuário escolher contraste baixo. O handle segue a mesma função que o
tap/arrasto (`wheelSelectionAt`/`wheelPointFor`, inversas uma da outra), o
centro é a saturação 0 aproximada pelo fundo da paleta e a linha "Resultado"
mostra o hex derivado — que não é o mesmo que a posição do handle, já que a
lightness entra depois. Sendo um `Canvas`, expõe quatro ações de acessibilidade
(matiz e saturação, ±10) e `contentDescription` com matiz/saturação atuais; como
todos os controles desta feature, grava ao soltar.

## 4. Preferências (sem DataStore novo)

`app_prefs`: `app_palette`, `accent_color`, `wallpaper_home`, `wallpaper_home_blur`
(mais `dynamic_color_enabled`, mantido espelhado para compatibilidade de backup).

`reader_prefs`: `theme`, `reader_accent_color`, `reader_wallpaper`,
`reader_wallpaper_blur`, `reader_veil`.

### Modelo do tema do leitor

`theme` é guardado como `auto` | `<paleta>` | `<paleta>:light|dark`:

- `auto` → segue paleta **e** variante do app;
- `<paleta>` → usa a paleta, variante do app;
- `<paleta>:light|dark` → paleta com variante fixa.

`ReaderTheme` faz parse/compose (`paletteId`, `variant`, `withPalette`,
`withVariant`) e `resolve` devolve o par resolvido. `ReaderConfig` carrega o par
(`theme` = id canônico, `themeDark` = variante) que chega em `themeVars`.

## 5. Wallpaper

Fundos embutidos: 8 gradientes (`amanhecer`, `aurora`, `bosque`, `carvao`,
`crepusculo`, `noite`, `oceano`, `pergaminho`) e 3 cores sólidas (`areia`,
`ardosia`, `musgo`), estas últimas para quem quer um fundo liso. `BuiltinWallpapersTest`
garante que a allowlist de `PreferenceAllowlists` não divirja dos fundos que o
renderer conhece, e que as três sólidas são planas.

Referência única por slot: `none` | `builtin:<id>` | `file:<nome.ext>`.

`WallpaperStorage`: `importFromUri` valida MIME/extensão, copia com teto de
20 MiB para `filesDir/wallpapers/<slot>_<timestamp>.<ext>` e apaga o arquivo
anterior do slot; `resolveFile` exige caminho canônico dentro do diretório e
arquivo existente (mesma regra F3 aplicada ao `photoPath`); `clearSlot` só apaga
arquivos do slot.

Render: `WallpaperBackground` não desenha nada quando a referência não resolve,
então uma referência pendurada degrada para "sem wallpaper" em vez de quebrar.
Blur usa `Modifier.blur` (API 31+) e, abaixo disso, um request reduzido do Coil
com `FilterQuality.Low` — sem `RenderEffect` no API < 31.

## 6. Recorte antes de aplicar

Não existe API padrão de recorte no Android (`com.android.camera.action.CROP` é
acordo não-oficial, ausente em AOSP/Pixel), então o recorte é **no app**:

1. O seletor SAF devolve a imagem; o slot entra em `pendingCrop` (nada é copiado).
2. `WallpaperCropOverlay` mostra a prévia na proporção da tela, com arrasto e
   zoom, mais a moldura do app simulada — barra de cima translúcida e barra de
   contagem na cor de `barColorFor`, com o véu quando é o leitor. Assim dá para
   ver o contraste real do texto sobre a imagem escolhida.
3. "Aplicar" grava `WallpaperCrop(zoom, panX, panY)`; "Usar sem ajustar" cai no
   `importFromUri` de antes; "Cancelar" não muda nada.

`panX`/`panY` são **frações do espaço disponível** (-1..1), não pixels, então o
mesmo recorte significa a mesma coisa em qualquer tamanho de moldura. A
matemática vive em `data/storage/WallpaperCrop.kt` (camada de dados, sem Compose,
testada como função pura): `coverScale`, `cropSlack`, `cropRectFor`, `clampCrop`,
`decodeSampleSize`.

- O tamanho de destino vem da **tela** (`screenWidthDp × densidade`), informado
  pelo overlay junto com o transform: a prévia pode ser menor, o arquivo sai na
  resolução da tela. Gravar na resolução da prévia seria um wallpaper borrado.
- `WallpaperStorage.saveCropped` decodifica com `inSampleSize` limitado a
  4096px no maior lado, recorta, escala para o alvo e grava JPEG 92.
- A decodificação é também a validação: os bytes precisam decodificar como
  imagem, senão nada é gravado (o `importFromUri` só confia em MIME/extensão).
- Rotação: o recorte é feito na proporção da tela atual; girando, o app re-corta
  o centro da mesma imagem (a barra de cima e as abas continuam corretas).

## 7. Render

### Barras

Chave única (`app_prefs.wallpaper_behind_bars`, padrão **ligada**) decide se o
wallpaper aparece atrás das barras. Vale para as duas superfícies — opção
escolhida na sessão: biblioteca e leitor juntos, editável nos dois lugares.

- Desligada: barra na cor sólida da superfície do tema (comportamento anterior à
  feature).
- Ligada (padrão): `barColorFor` devolve a superfície com `BAR_VEIL_ALPHA` (0.8)
  de opacidade. Wallpaper cru atrás de título/ícones/abas foi descartado: com
  fundo claro (amanhecer, papel) o texto praticamente sumia.
- O `Scaffold` da biblioteca continua transparente quando há wallpaper; só as
  barras recebem o véu.
- O inset da **barra de navegação do sistema** (gestos ou botões) também recebe o
  véu (`NavigationBarVeil`), senão o wallpaper aparecia cru atrás da barra do
  Android enquanto a barra de cima estava escurecida. A faixa é desenhada entre o
  wallpaper e o `Scaffold`, com a mesma cor da barra de cima, e só existe quando
  há wallpaper.

**Home**: `Scaffold` transparente enquanto há wallpaper, `TopAppBar` e `TabRow`
com `barColorFor`. Sem wallpaper, idêntico ao anterior.

**Leitor**: `themeVars` devolve `bgColor = transparent` quando há wallpaper; o
`ReaderWebView` já pinta o próprio fundo como transparente. O wallpaper + véu
(cor de fundo da paleta do leitor, alpha = `veil/100`) passam a cobrir a **tela
inteira**, atrás do `Scaffold`, para que a barra do topo, a barra de opções e a
faixa de bateria fiquem sobre o mesmo véu. `ReaderTopBar` e `ReaderStatusBar`
recebem `containerColor` (default = superfície do tema, mantendo os testes
existentes). Véu 80% é o default; 0% deixa o wallpaper cru, por escolha do
usuário.

## 8. Temas salvos (5 slots)

Um tema salvo guarda **só cores e tema do leitor** — nunca wallpaper, blur ou
véu (decisão explícita: trocar de tema não deve mexer no fundo de quem gosta do
fundo). Campos:

```kotlin
SavedTheme(name, palette, accentColor?, readerTheme, readerAccentColor?)
```

- Persistido em `app_prefs.saved_themes` como JSON, via `SavedThemeCodec`.
- Máximo de `SavedThemeCodec.MAX_THEMES` (5). Nome repetido (ignorando caixa)
  sobrescreve o slot em vez de criar outro; com os 5 cheios, um nome novo é
  recusado (`saveCurrent` devolve `false`) e a UI esconde a ação de salvar.
- Nome é saneado (espaços colapsados, controles removidos, teto de 40 chars) e
  todo valor passa pelos sanitizers de paleta/acento/tema na leitura **e** na
  escrita — o JSON também chega por backup, então não se confia nele.
- `VisualThemeUseCase` é a única porta: `saveCurrent`, `apply`, `delete`,
  `resetToDefaults`. Aplicar escreve as quatro chaves de preferência e nada mais.
- Exposto nas duas superfícies (Configurações e sheet do leitor) pelo mesmo
  `SavedThemesSection`.

## 9. Restaurar aparência padrão

`VisualThemeUseCase.resetToDefaults` volta paleta (dinâmica no Android 12+,
indigo abaixo disso), acentos, papéis de parede (apagando os arquivos dos dois
slots), desfoques, véu e a opção de barras (`wallpaper_behind_bars` → ligada) ao
padrão. **Os temas salvos são preservados** — o texto
do diálogo de confirmação diz isso explicitamente.

## 10. Sliders

Sliders gravam **uma vez, ao soltar** (`onValueChangeFinished`), com o valor
local durante o arrasto. Antes, cada frame do arrasto gravava no DataStore e
`ReaderScreen` reagia a qualquer mudança de `state.config` com
`evaluateJavascript(applyConfigJs(...))` — ou seja, ~60 writes em disco e ~60
avaliações de JS por segundo, e o controle "prendia"/pulava.

A prévia ao vivo continua onde é barata (desfoque e véu alimentam
`WallpaperBackground` por um estado de tela em `ReaderScreen`, sem persistir);
para fonte, espaçamento, rolagem e cor de acento a página/tema só muda ao
soltar. A roda de cores segue a mesma regra pelo caminho `onPreview`/`onCommit`:
durante o arrasto só o estado local muda; o DataStore é escrito no `onDragEnd`.

## 11. Backup v3

`settings` ganha `appPalette`, `accentColor`, `wallpaperHome`,
`wallpaperHomeBlur`, `wallpaperBehindBars`, `savedThemes` e, dentro de `reader`,
`accentColor`, `wallpaper`, `wallpaperBlur`, `veil`. **Os bytes da imagem não entram no
backup.**

Na importação, ref `file:` cujo arquivo não existe no aparelho vira `none`
(`WallpaperStorage.exists`); `builtin:` restaura por completo; ref inválida é
descartada pelo sanitizer. Acento inválido é descartado e volta o padrão da
paleta.

## 12. Correção de bug encontrada no caminho

O sheet do leitor não tinha scroll. Com as seções novas (paleta, variante,
acento, fonte, espaçamento, tela ligada, swipe, wallpaper, desfoque, véu,
rolagem automática), a metade de baixo ficava inalcançável em celular. O teste
de UI pegou o problema porque o nó do wallpaper não estava "displayed";
`verticalScroll` na Column resolveu.

## 13. Organização das Configurações

A tela é uma pilha de seções colapsáveis (`ui/settings/components/SettingsSection`),
todas fechadas por padrão e cada uma com **resumo do estado atual** no cabeçalho
("Claro · Índigo", "Amanhecer", "2/5", "Português"), de modo que a tela fechada já
informa o que está configurado:

1. **Cores e tema** — Modo (sistema/claro/escuro), Paleta, Cor de destaque.
2. **Papel de parede** — Fundo da biblioteca, Desfoque, Fundo também nas barras.
3. **Meus temas** — os 5 slots.
4. `ResetAppearanceRow` — ação isolada, com confirmação que diz que os temas salvos ficam.
5. **Backup e restauração** — exportar/importar.
6. **Idioma** — pt/en.
7. Card **Sobre** (navegação, sem seção).

Nomes voltados ao usuário em vez de jargão: "Cor de acento" → **"Cor de destaque"**
(com a descrição "Botões, ícones e seleções do app"), "Papel de parede da
biblioteca" → **"Fundo da biblioteca"**. Cada controle tem um `OptionLabel` com
título e descrição de uma linha; o sheet do leitor usa os mesmos rótulos.

## 14. Containers opacos

`primaryContainer` é **opaco** (mistura de `primary` com o fundo da variante via
`lerp`), nunca `primary.copy(alpha = …)`. Um container translúcido deixa o
wallpaper — e a sombra do `Surface` — aparecerem através do FAB, que é como o
"quadrado branco atrás do botão +" apareceu. `AppPaletteTest` asserta
`alpha == 1f` para os quatro containers e contraste ≥ 4.5 do `on*Container` sobre
o container, inclusive com acento personalizado.

## 15. Fora de escopo

- Wallpaper por novel/coleção (exigiria Room v13).
- Paleta derivada da capa do livro.
- Wallpaper animado/vídeo.
- Embeddar a imagem no backup.
- Wallpaper dentro de um tema salvo.
- Renomear um tema salvo existente (hoje: salvar com o mesmo nome sobrescreve).

## 16. Limitações de teste que valem saber

- Com um `TextField` na tela, testes Robolectric+Compose **nunca** ficam idle (o
  cursor piscando anima para sempre) e qualquer `waitForIdle` estoura em 60s.
  Por isso o diálogo de nome do tema não é exercitado na UI: o caminho
  nome → `SavedTheme` é coberto em `VisualThemeUseCaseTest` e
  `SavedThemeCodecTest`.
- Clique por coordenada não alcança nós dentro das fileiras com scroll
  horizontal (em `ModalBottomSheet` ou já rolados); use
  `performSemanticsAction(SemanticsActions.OnClick)`.
- `performScrollTo` só funciona se houver ancestral rolável — em harness sem
  scroll ele falha com "no parent layout with a Scroll SemanticsAction".
- A suíte de JVM precisa de heap maior que o padrão do Gradle (2 GiB): o
  registro nativo do Robolectric (`ShadowLineBreaker`) cresce a cada layout de
  texto e estoura 512 MiB.
- Bitmap de verdade no Robolectric exige `@GraphicsMode(NATIVE)`
  (`WallpaperCropStorageTest`). Sem isso o `BitmapFactory` falso "decodifica"
  qualquer byte como imagem, e o teste de recorte não prova nada.
- `BitmapFactory.decodeStream` com `inJustDecodeBounds` retorna **null de
  propósito** (a resposta está no `Options`). Tratar esse null como erro foi o bug
  que só apareceu porque o teste do caminho feliz do recorte existia: o crop nunca
  gravava nada.

## 17. Containers seguem o tom do wallpaper (issue #17)

Escolha do usuário, fechada na sessão: **os containers que carregam texto seguem o
wallpaper, não a paleta**. Paleta escura (Grafite) sobre wallpaper claro (Areia)
mostrava a biblioteca com dois temas na mesma tela — barra de cima, tab row, chips,
card, FAB e barra de contadores escuros sobre um fundo claro. O caminho oposto
(paleta clara + wallpaper escuro) tinha o mesmo defeito invertido.

Três decisões:

1. **Como descobrir o tom.** Wallpapers embutidos são classificados pelas próprias
   cores; imagem do usuário é **amostrada** (`WallpaperStorage.wallpaperLuminance`,
   e `uriLuminance` para a imagem ainda não copiada do recorte), com `inSampleSize`
   de 64px no maior lado — decodificar uma foto de 4000px inteira para tirar um
   booleano custaria dezenas de MB. Referência que não resolve (arquivo ausente,
   arquivo que não é imagem) não dá tom.
2. **Como seguir o tom.** Troca da **variante** da paleta ativa (Grafite escuro +
   Areia claro → Grafite claro), não um container tingido com a cor do wallpaper:
   os pares de contraste já desenhados e testados em `AppPaletteTest` continuam
   valendo, e o acento do usuário sobrevive via `withAccent`.
3. **Alcance.** Biblioteca **e** a moldura simulada do recorte, que precisa mostrar
   o tom que a imagem vai realmente receber — senão a prévia mente sobre o
   contraste. O leitor fica de fora: lá o tema é escolhido pelo usuário e há véu.

O limiar é `LIGHT_WALLPAPER_LUMINANCE = 0.5f` sobre a luminância WCAG, que **não é
linear**: `#808080` vale 0.22. Logo só wallpaper genuinamente claro troca os
containers, e tons médios ficam escuros — "amanhecer" termina num amarelo forte
mas tem média 0.42, e um fundo de tom médio está mais perto do `surface` escuro do
que do claro.

O seam é `WallpaperVariantTheme(isLightWallpaper)` (`ui/customization`), com
`LocalAppVisuals` (`ui/theme/Theme.kt`) carregando paleta/acento/variante do app e
`appColorScheme` montando o esquema (inclusive `dynamic`). `barColorFor` tem de ser
chamado **dentro** do wrapper, senão o véu continua saindo do tom da paleta. O
wrapper também ajusta os ícones da status bar e os restaura no `onDispose`, porque
`NovelReaderTheme` não recompõe numa navegação.
