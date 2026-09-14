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
| Origem | Imagem do usuário (SAF) + 8 fundos embutidos (gradientes) |
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

## 6. Render

**Home**: `Scaffold`, `TopAppBar` e `TabRow` ficam transparentes enquanto há
wallpaper; o wallpaper é desenhado atrás. Sem wallpaper, o comportamento é
idêntico ao anterior.

**Leitor**: `themeVars` devolve `bgColor = transparent` quando há wallpaper; o
`ReaderWebView` já pinta o próprio fundo como transparente. `ReaderScreen`
desenha wallpaper + véu (cor de fundo da paleta do leitor, alpha = `veil/100`)
atrás do WebView. Véu 80% é o default; 0% deixa o wallpaper cru, por escolha do
usuário.

## 7. Backup v3

`settings` ganha `appPalette`, `accentColor`, `wallpaperHome`,
`wallpaperHomeBlur` e, dentro de `reader`, `accentColor`, `wallpaper`,
`wallpaperBlur`, `veil`. **Os bytes da imagem não entram no backup.**

Na importação, ref `file:` cujo arquivo não existe no aparelho vira `none`
(`WallpaperStorage.exists`); `builtin:` restaura por completo; ref inválida é
descartada pelo sanitizer. Acento inválido é descartado e volta o padrão da
paleta.

## 8. Correção de bug encontrada no caminho

O sheet do leitor não tinha scroll. Com as seções novas (paleta, variante,
acento, fonte, espaçamento, tela ligada, swipe, wallpaper, desfoque, véu,
rolagem automática), a metade de baixo ficava inalcançável em celular. O teste
de UI pegou o problema porque o nó do wallpaper não estava "displayed";
`verticalScroll` na Column resolveu.

## 9. Fora de escopo

- Wallpaper por novel/coleção (exigiria Room v13).
- Paleta derivada da capa do livro.
- Wallpaper animado/vídeo.
- Embeddar a imagem no backup.
