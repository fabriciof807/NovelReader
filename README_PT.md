# NovelReader

**Um leitor de novels para Android que mantém sua biblioteca no seu dispositivo.**

Importe uma novel uma vez e leia offline em qualquer lugar. O NovelReader não tem conta, analytics, rastreamento nem serviço de nuvem próprio.

> English version: [README.md](README.md)

## Download

Baixe o APK mais recente em [GitHub Releases](https://github.com/fabriciof807/NovelReader/releases).

Requer Android 8.0 ou mais recente.

## O que dá para fazer

### Importar e continuar lendo offline

- Importe uma novel de um site suportado ou de arquivos HTML/MHT que já estão no seu dispositivo.
- Baixe capítulos em segundo plano e acompanhe o progresso pela notificação.
- Opcionalmente verifique novels importadas da web em busca de capítulos novos.
- Mantenha seus capítulos mesmo se o site original mudar, sair do ar ou colocar paywall.

### Ler do seu jeito

- Retome cada capítulo de onde você parou.
- Pesquise palavras e frases em todos os capítulos de uma novel.
- Adicione bookmarks com notas.
- Ajuste tamanho da fonte, altura da linha, scroll automático, paleta, tema do leitor e cor de destaque.
- Escolha um papel de parede ou um gradiente pronto separadamente para a biblioteca e para o leitor; recorte, desfoque e aplique o véu para ler com conforto.
- Salve até cinco temas de cor sem mudar seus papéis de parede.

### Organize sua biblioteca

- Ordene e filtre novels por título, data, estado de leitura, favoritas, capítulos e bookmarks.
- Crie coleções para suas novels.
- Mantenha fichas de personagens com anotações, fotos e favoritos.
- Faça backup e restaure biblioteca, bookmarks, personagens, coleções e configurações em JSON.

### Recupere importações que falharam

Se um capítulo não puder ser importado, o NovelReader registra a falha e permite tentar a URL de novo, importar um arquivo MHT manualmente ou descartar o item. Ele também encontra números de capítulo faltando e capítulos com conteúdo vazio.

## Como usar

1. Instale o APK em [Releases](https://github.com/fabriciof807/NovelReader/releases).
2. Abra o app e toque em **+** na biblioteca.
3. Escolha **Web** para colar a URL de uma novel, ou **Arquivos** para selecionar arquivos HTML/MHT.
4. Selecione os capítulos a importar.
5. Abra a novel na biblioteca e escolha um capítulo para ler.

A aba **Personagens** fica disponível dentro de cada novel. Seus bookmarks ficam reunidos em **Favoritos**.

## Privacidade

- Sem conta, analytics, telemetria, rastreamento ou servidor próprio.
- Biblioteca, capítulos, bookmarks, dados de personagens, fotos e configurações ficam no seu dispositivo.
- O app acessa um site apenas quando você escolhe uma importação da web ou uma verificação de atualização.
- O importador opcional de personagens do MVLEMPYR acessa esse serviço apenas quando você inicia uma importação por ele.

## Idiomas

Português (padrão) e inglês. Troque o idioma em **Configurações**.

## Sites suportados para importação da web

- [FreeWebNovel](https://freewebnovel.com/)
- [ReadNovelFull](https://readnovelfull.com/)
- Outras páginas HTML pelo parser genérico, quando a estrutura é compatível

## Última versão

### v2.11.0

- Os controles da biblioteca acompanham automaticamente um papel de parede claro ou escuro, para um texto legível e consistente.
- Os capítulos aparecem na biblioteca enquanto uma importação da web ainda está rodando.
- Importações da web mais seguras e confiáveis.

Veja [Releases](https://github.com/fabriciof807/NovelReader/releases) para o changelog completo.

## Contribuindo

Quer ajudar a desenvolver o NovelReader? Leia o [CONTRIBUTING.md](CONTRIBUTING.md).

## Licença

[MIT](LICENSE)
