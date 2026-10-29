# 020 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende.

- [ ] **T1** — Mover o app para `static/app/index.html` com caminhos absolutos (CA4, CA5) — Claude
- [ ] **T2** — Caminhos absolutos da logo nas views (CA4) — Claude
- [ ] **T3** — `WebConfig` com os forwards de `/app` e `SecurityConfig` com as rotas públicas (CA7) — Gustavo
- [ ] **T4** — Links de e-mail `/app/#/`, as duas descrições do OpenAPI e os asserts dos testes (CA6) — Gustavo
- [ ] **T5** — `SitePagesIntegrationTest` (CA7) — Gustavo
- [ ] **T6** — `static/index.html` do site, com as seções e as meta tags (CA1, CA8, CA10) — Claude
- [ ] **T7** — `css/site.css`, mobile-first (CA9) — Claude
- [ ] **T8** — `js/site.js`, com o redirecionamento de hash e o topo por sessão (CA1, CA2, CA3) — Claude
- [ ] **T9** — Prints com dados fictícios (CA8) — Claude ou Gustavo
- [ ] **T10** — Testes JS: `site.test.mjs`, `static-pages.test.mjs` e o ajuste do `fake-dom` (CA1–CA3, CA8, CA10) — Claude
- [ ] **T11** — README: o app em `/app/`, o site na raiz, o formato dos links de e-mail e as rotas públicas (todos os CA) — Claude
- [ ] **T12** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`, a checagem manual e as skills do projeto (`web-design-guidelines`, `accessibility-compliance`, `responsive-design`), e anotar a revisão aqui — Claude, com a parte em produção do Gustavo
- [ ] **T13** — Checklist de deploy e de rollback — Claude prepara, Gustavo executa

## Verificação

- `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`.
- Manual, local:
  - `/` sem sessão e com sessão (CA1, CA2);
  - `/#/transacoes` e `/#/verificar-email?token=x` → `/app/#/...` (CA3);
  - login com e-mail e com Google em `/app/` (CA5);
  - sessão aberta na versão antiga e depois a versão nova (CA4);
  - 390px e 1280px sem rolagem horizontal (CA9).

## Notas de implementação

Descobertas durante a implementação que afetaram a spec ou o plano.
