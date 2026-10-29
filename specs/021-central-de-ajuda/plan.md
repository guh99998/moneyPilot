# 021 — Plano técnico (Central de ajuda)

**Status:** aprovado

**Spec:** `specs/021-central-de-ajuda/spec.md` (aprovada)
**Branch:** `feature/021-central-de-ajuda`, a partir da `develop` atualizada

## Contexto

Não existe explicação de uso do moneyPilot. A única ajuda, "Usando com IA", ficou técnica demais. A 020 já deixou `/ajuda/**` liberado no `SecurityConfig` e tirou o link "Ajuda" do rodapé até esta spec existir. O objetivo é ter tutoriais em texto, públicos, um por área, e um "?" em cada tela do app que abre o tutorial daquela tela.

Fatos do código que guiam o plano:

- Rotas do app (`js/app.js`): `dashboard`, `transactions`, `bills`, `forecast`, `accounts`, `categories`, `budgets`, mais `profile`, `suporte`, `usando-com-ia`, `apoie` e `admin/chamados`. Os relatórios aparecem na Visão geral (`dashboard.js` usa `api.reports.*`).
- Toda tela logada monta o título com `pageHead()` (`js/ui.js:248`). Login, cadastro e as telas soltas sem sessão não usam essa função, e isso já cobre a exceção do CA3.
- `WebConfig` só tem `/app` e `/app/`. O Spring não serve `index.html` de subpasta sozinho, então `/ajuda/` precisa de um forward. `/ajuda/<pagina>.html` é servido como arquivo estático.
- `GlobalExceptionHandler` já responde 404 para recurso público inexistente (020).
- `site.js` só roda com `body[data-page="site"]` e tem `sessionLinks()` ("Entrar | Criar conta" ou "Abrir o app").
- `ai-guide.js` exporta `assistantPrompt(origin)`, já coberto por `ai-guide-view.test.mjs`.
- `OpenApiConfig` e `UserController` citam `/app/#/usando-com-ia`. A 020 combinou que a 021 troca esse texto.

## Abordagem

### A. Páginas da ajuda (HTML estático, sem build)

- `static/ajuda/index.html`: o índice, com um link e uma frase curta por área.
- Um arquivo por tutorial, `static/ajuda/<slug>.html`:

| Área (spec) | Arquivo |
|---|---|
| Primeiros passos | `primeiros-passos.html` |
| Contas | `contas.html` |
| Categorias | `categorias.html` |
| Lançamentos e transferências | `lancamentos.html` |
| Orçamento | `orcamentos.html` |
| A pagar e receber (parcelas, baixa, recorrência) | `a-pagar-e-receber.html` |
| Previsão | `previsao.html` |
| Relatórios (Visão geral) | `visao-geral.html` |
| Alertas (o sino) | `alertas.html` |
| Perfil e segurança | `perfil-e-seguranca.html` |
| Suporte | `suporte.html` |
| Usar com IA | `usando-com-ia.html` |

- **Estrutura de cada tutorial:**
  - O mesmo topo do site, com a logo e a navegação de sessão.
  - Uma trilha "Ajuda › <área>", que leva de volta ao índice.
  - O `h1`, seguido de três `h2` nesta ordem: "Para que serve", "Passo a passo" (`<ol>`) e "Dúvidas comuns" (`<details>`, como o FAQ do site).
  - O rodapé do site.
  - Nenhuma `<img>` (CA2).
  - `<title>` e `meta description` próprios, para buscadores.
- **Textos:** escrevo durante a implementação, conferindo cada regra no README e no código da tela. Não invento nenhuma regra. Você revisa os textos no merge. A spec já previa que eu os proponho e você ajusta.
- **"Usando com IA" (CA4):**
  1. Primeiro o caminho simples: o que um assistente consegue fazer e o texto pronto para colar, com o botão "Copiar texto".
  2. Por último a seção "Para desenvolvedores": entrar, renovar, sair, cuidados, Swagger e OpenAPI. O conteúdo vem de `ai-guide.js`.
  - O texto para colar é gerado por `assistantPrompt(location.origin)`, para não duplicar a fonte.
  - Sem JS, aparece o mesmo texto com um endereço de exemplo.
- **Estilo:** um bloco `.help-*` em `css/site.css`, mobile-first, com a mesma identidade da 020. Linha de até cerca de 70 caracteres, sem rolagem horizontal em 390px e 1280px (CA7). Os blocos de código têm `overflow-x: auto` dentro da caixa e `tabindex="0"`.
- **`js/site.js`:**
  - A navegação de sessão passa a rodar também em `data-page="help"`.
  - O redirecionamento de hash antigo continua só no site (`data-page="site"`).
  - Na página "Usando com IA", ele preenche o texto e liga o botão de copiar, com o mesmo aviso de falha da tela atual.
- **Rodapé:** o link "Ajuda" (`/ajuda/`) volta ao rodapé do site e entra no das páginas da ajuda. A spec 020 (CA10) e o `tasks.md` dela registram isso. "Privacidade" continua de fora até a 024.

### B. Botão "?" nas telas do app

- **Módulo `js/help.js`, puro e testável:**
  - `HELP_PAGES`: o mapa de rota para arquivo. `dashboard` → `visao-geral.html`, `transactions` → `lancamentos.html`, `bills` → `a-pagar-e-receber.html`, `forecast` → `previsao.html`, `accounts` → `contas.html`, `categories` → `categorias.html`, `budgets` → `orcamentos.html`, `profile` → `perfil-e-seguranca.html`, `suporte` → `suporte.html`, `usando-com-ia` → `usando-com-ia.html`.
  - `helpUrl(routePath)`: devolve `/ajuda/<arquivo>`, ou `/ajuda/` para rota sem mapa, como `apoie`, `admin/chamados` ou uma tela nova (CA8).
  - `helpLink(routePath)`: monta o link.
- **Onde fica:** `pageHead()` em `ui.js` põe o "?" ao lado do `h1`, usando a rota do hash atual. Assim, toda tela logada ganha o botão sem mexer em cada view. As telas sem `pageHead`, como login e cadastro, ficam sem ele.
- **Acessibilidade (CA6):**
  - É um `<a>`, não um `<button>`, porque navega: `href`, `target="_blank"` e `rel="noopener"`.
  - Nome acessível "Ajuda sobre esta tela (abre em nova aba)". A spec pede "Ajuda sobre esta tela"; acrescentei o aviso de nova aba porque as diretrizes mandam avisar. O "?" visível fica `aria-hidden`.
  - Círculo de 44px no celular, menor no desktop, mas com área de toque de 44px. Usa o foco visível global e tem `title` igual ao nome acessível.

### C. Tela `#/usando-com-ia` no app (CA5)

- `ai-guide.js` passa a mostrar:
  - um parágrafo do que dá para fazer;
  - o texto para colar, com o botão "Copiar texto", que já existe;
  - o link "Guia completo na central de ajuda" (`/ajuda/usando-com-ia.html`).
- Saem os passos técnicos (entrar, renovar, sair, cuidados, Swagger), que agora ficam só na ajuda.
- O layout com e sem sessão não muda.

### D. Backend (mínimo)

- `WebConfig`: `addRedirectViewController("/ajuda", "/ajuda/")` e `addViewController("/ajuda/").setViewName("forward:/ajuda/index.html")`, como foi feito com `/app/`. `SecurityConfig` já libera `/ajuda` e `/ajuda/**`.
- `OpenApiConfig` e `UserController`: o texto passa de `/app/#/usando-com-ia` para `/ajuda/usando-com-ia.html`.

## Banco de dados

Nenhuma migration. **Impacto em dados existentes:** nenhum.

## API

Nenhuma rota de API nova ou alterada; só o texto de duas descrições do OpenAPI muda. `PUBLIC_PATHS` não muda, porque `/ajuda` não é API. As rotas de página novas são `/ajuda`, que redireciona para `/ajuda/`, `/ajuda/`, que abre o índice, e `/ajuda/*.html`, os arquivos estáticos.

## Testes

- **Java:** `SitePagesIntegrationTest` ganha três casos:
  - `/ajuda` responde 3xx para `/ajuda/`;
  - `/ajuda/` dá `forwardedUrl("/ajuda/index.html")`;
  - `/ajuda/contas.html` responde 200 sem login (CA1).
- **JS:**
  - `help.test.mjs`:
    - `helpUrl` para cada rota mapeada e para uma rota desconhecida (CA3, CA8);
    - todo arquivo do mapa existe em `static/ajuda/`;
    - o link tem nome acessível, `target="_blank"` e `rel="noopener"` (CA6);
    - `pageHead` passa a ter o "?".
  - `help-pages.test.mjs`, que lê o HTML com `fs`:
    - o índice tem um link para cada uma das 12 áreas, e todos os destinos existem (CA1);
    - cada tutorial tem os três `h2` na ordem, nenhuma `<img>`, `<title>`, `meta description` e a trilha para o índice (CA2);
    - em "Usando com IA", o texto para colar vem antes de "Para desenvolvedores", que é o último `h2` (CA4);
    - os caminhos são absolutos (`/css`, `/js`).
  - `static-pages.test.mjs`: o rodapé do site volta a ter `/ajuda/`.
  - `ai-guide-view.test.mjs`: a tela tem o link para a ajuda e não tem mais os passos técnicos nem o Swagger (CA5). Os testes de copiar e do texto continuam.
- **Manual (Edge headless, como na 020):**
  - índice e um tutorial em 390px e 1280px, sem rolagem horizontal (CA7);
  - o "?" em todas as telas, em 390px e 1280px.

## Verificação

1. `./mvnw verify` e `node --test "src/test/js/*.test.mjs"` passando.
2. Prints das páginas e do "?" com o Edge headless no app local, com `/ajuda/` aberto sem login.
3. Revisão com as skills `web-design-guidelines`, `accessibility-compliance` e `responsive-design`, anotada em `tasks.md`.
4. README: a seção "Cliente web" cita a central de ajuda, o `/ajuda/` e o "?".

## Riscos e decisões

- **Repetição de topo e rodapé em 13 arquivos:** sem build não há include. Um teste confere que todos têm os mesmos links. É o mesmo custo que a spec aceita para os textos.
- **URL com `.html`:** é mais simples que um view controller por página. As URLs ficam estáveis para a 013 (boas-vindas) apontar.
- **Tutorial desatualizado quando uma tela muda:** está na spec como custo aceito. Na `tasks.md` fica anotado que mudança de tela revisa o tutorial.

## Deploy

Sem migration e sem variável nova: só subir a imagem. Rollback é voltar à imagem anterior. Nada depende de dado.
