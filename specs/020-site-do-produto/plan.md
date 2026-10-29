# 020 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado

## Abordagem

Divisão: o front e os documentos são do Claude; o backend é do Gustavo, e o Claude revisa.

Fatos do código que guiam o plano:

- `static/index.html` usa caminhos relativos (`css/app.css`, `js/app.js`, `image/...`). Hoje `/` é servido pela welcome page padrão do Spring Boot. Não há `WebMvcConfigurer` nem view controller.
- Onze strings `'image/money-pilot-logo.png'` no JS (`app.js`, `auth.js` ×4, `verify-email.js`, `support.js`, `reset-password.js`, `ai-guide.js`, `confirm-email.js`) deixariam de funcionar em `/app/`.
- Os links `#/...` do app são só hash e continuam valendo em `/app/`. `api.js` usa `BASE = '/api/v1'` absoluto. `google.js` não depende do caminho.
- O `localStorage` (`moneypilot.*`) é por origem e o cookie `refresh_token` tem `path=/api/v1/auth`, então a sessão sobrevive (CA4).
- `SecurityConfig` libera `/`, `/index.html`, `/css/**`, `/js/**`, `/image/**`, `/data/**` e `/favicon.ico`, mas não `/app/**` nem `/ajuda/**`.
- Links `/#/` são gerados em `EmailVerificationService`, `PasswordResetService` e `EmailChangeService`. O texto `#/usando-com-ia` aparece em `UserController` e em `OpenApiConfig`.
- `fake-dom.mjs` tem `window.location = { origin, hash }`, sem `pathname` e sem `replace`.

### A. Mover o app (front)

- `git mv static/index.html static/app/index.html`, trocando os caminhos para absolutos: `/css/app.css`, `/js/app.js`, `/image/money-pilot-logo.png`. `css/`, `js/` e `image/` não mudam de lugar, e os imports relativos entre módulos JS continuam válidos.
- Trocar as 11 strings `'image/money-pilot-logo.png'` por `'/image/money-pilot-logo.png'`.
- Não usar `<base href="/">`: faria `href="#/bills"` apontar para `/#/bills`, ou seja, para o site.

### B. Site na raiz (front)

- `static/index.html` novo, HTML semântico e estático:
  - `<header>` fixo com a logo e a navegação de sessão;
  - `<main>` com "O que é", "Recursos" (3 a 4 prints), "Como começar" e "Perguntas frequentes" (`<details>`), nesta ordem;
  - `<footer>` com Suporte (`/app/#/suporte`), Apoie o projeto (`/app/#/apoie`), GitHub, Ajuda (`/ajuda/`) e Privacidade e termos (`/privacidade/`, sem destino até a 024).
- `<head>`: `<title>`, `meta description`, `og:title`/`og:description`/`og:image`/`og:type`, `twitter:card`, `link rel=icon` e a mesma fonte Manrope do app.
- `css/site.css`: reaproveita as variáveis de tema de `app.css` (importadas ou copiadas, decidido ao ler o arquivo), mobile-first, mesmos breakpoints.
- `js/site.js` (módulo) com funções puras testáveis:
  - `legacyAppUrl(hash)`: devolve `'/app/' + hash` quando o hash começa com `#/` e `null` nos outros casos (CA3). No carregamento, `location.replace(...)` quando não for `null`.
  - `sessionNav(hasToken)`: monta "Entrar | Criar conta" ou "Abrir o app" (CA1, CA2). Lê a chave `moneypilot.token` pela constante de `api.js` (exportada, se ainda não for).
  - Sem JS, o topo mostra "Entrar | Criar conta" em HTML puro e o JS só troca para "Abrir o app".
- Prints em `static/image/site/` (WebP ou PNG, até ~150 KB cada), com conta de demonstração e dados fictícios. Se não der para capturar daqui, a tarefa passa ao Gustavo, com o roteiro de dados de exemplo pronto.

### C. Backend (Gustavo; Claude revisa)

- `config/WebConfig.java` (`WebMvcConfigurer.addViewControllers`): `/app` → `redirect:/app/` e `/app/` → `forward:/app/index.html`. A welcome page continua servindo `/` com o novo `static/index.html`. Com `server.forward-headers-strategy=framework`, o redirect sai com https em produção.
- `SecurityConfig`: liberar `/app`, `/app/`, `/app/index.html`, `/ajuda`, `/ajuda/**`, `/privacidade/**` e `/termos/**` (deixa a 024 pronta). O Spring Security 6 também autoriza o dispatch FORWARD, por isso o destino do forward entra na lista (CA7).
- Links de e-mail: `"/#/"` → `"/app/#/"` nos três services (CA6).
- Descrições do OpenAPI em `UserController` e `OpenApiConfig`: até a 021, citar `/app/#/usando-com-ia`; a 021 troca para `/ajuda/usando-com-ia.html`.

## Banco de dados

Nenhuma migration. **Impacto em dados existentes:** nenhum.

## API

| Método | Rota | Mudança |
|---|---|---|
| — | — | Nenhuma rota nova. Só o texto de duas descrições do OpenAPI muda. |

Mudanças incompatíveis com o cliente atual: nenhuma na API. Muda o endereço do app (`/` → `/app/`), coberto pelo redirecionamento de hash (CA3). Não há campo de dinheiro. `OpenApiContractIntegrationTest` e `PUBLIC_PATHS` não mudam, porque cobrem só a API.

## Cliente web

- `app/index.html` (movido), `index.html` (site), `css/site.css`, `js/site.js` e as 11 strings da logo nas views.
- O login, o cadastro e a sessão não mudam.

## Testes

- **Java (Gustavo):** `SitePagesIntegrationTest`, que estende `AbstractIntegrationTest`, sem autenticação:
  - `GET /` → 200 com o marcador do site;
  - `GET /app` → 3xx para `/app/`;
  - `GET /app/` → 200 com o marcador do app (`id="app"`);
  - `GET /ajuda/` → não pode dar 401.

  Atualizar os quatro asserts de e-mail (`EmailVerificationIntegrationTest` ×2, `PasswordResetIntegrationTest`, `EmailChangeIntegrationTest`) para `/app/#/` (CA6, CA7).
- **JS (Claude):**
  - `site.test.mjs`: `legacyAppUrl` com `#/transacoes`, `#/verificar-email?token=abc`, `''`, `#secao` e `#` (CA3); `sessionNav` com e sem token (CA1, CA2).
  - `static-pages.test.mjs`, lendo o HTML via `fs`: meta tags e ordem das seções (CA8); links do rodapé (CA10); nenhum caminho relativo `css/`/`js/`/`image/` em `app/index.html`; nenhuma string `'image/money-pilot-logo.png'` relativa no JS.
  - `fake-dom.mjs` ganha `location.pathname` e `location.replace`, sem quebrar os testes atuais.
- **Manual:** CA4, CA5 e CA9 (ver Verificação em `tasks.md`).

## Riscos e alternativas descartadas

- **Raiz decidida pela sessão** (app na raiz para quem tem sessão): descartada pela spec.
- **`<base href>`:** quebra os links de hash.
- **Rollback:** e-mails enviados depois do deploy levam `/app/#/`. Se a versão anterior voltar, `/app/` dá 401. O rollback precisa de um forward temporário de `/app/` (ou do aceite de que esses links falham) e entra no checklist.
- **`og:image` relativo:** alguns leitores de prévia exigem URL absoluta. O domínio não pode ir ao repositório público, então fica relativo e anotado como limitação.
- **Proxy Caddy (fora do repo):** o Gustavo confere se ele repassa todos os caminhos para o app sem regra de path.

## Deploy

1. Sem migration, então sem `pg_dump` obrigatório.
2. Conferir o Caddy.
3. Subir a imagem.
4. Rodar a verificação manual em produção, com um navegador já logado antes do deploy (CA4).

Rollback: voltar a imagem anterior. E-mails enviados depois do deploy com `/app/#/` só funcionam se `/app/` for atendido (ver Riscos).
