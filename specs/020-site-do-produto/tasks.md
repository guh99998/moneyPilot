# 020 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende.

- [x] **T1** — Mover o app para `static/app/index.html` com caminhos absolutos (CA4, CA5) — Claude
- [x] **T2** — Caminhos absolutos da logo nas views (CA4) — Claude
- [x] **T3** — `WebConfig` com os forwards de `/app` e `SecurityConfig` com as rotas públicas (CA7) — Gustavo
- [x] **T4** — Links de e-mail `/app/#/`, as duas descrições do OpenAPI e os asserts dos testes (CA6) — Gustavo
- [x] **T5** — `SitePagesIntegrationTest` (CA7) — Gustavo
- [x] **T6** — `static/index.html` do site, com as seções e as meta tags (CA1, CA8, CA10) — Claude (os recursos estão em cartões de texto; os prints entram na T9)
- [x] **T7** — `css/site.css`, mobile-first (CA9) — Claude
- [x] **T8** — `js/site.js`, com o redirecionamento de hash e o topo por sessão (CA1, CA2, CA3) — Claude
- [x] **T9** — Prints com dados fictícios (CA8) — Claude (WebP em `static/image/site/`, de uma conta de demonstração local)
- [x] **T10** — Testes JS: `site.test.mjs`, `static-pages.test.mjs` e o ajuste do `fake-dom` (CA1–CA3, CA8, CA10) — Claude
- [x] **T11** — README: o app em `/app/`, o site na raiz, o formato dos links de e-mail e as rotas públicas (todos os CA) — Claude
- [ ] **T12** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`, a checagem manual e as skills do projeto (`web-design-guidelines`, `accessibility-compliance`, `responsive-design`), e anotar a revisão aqui — Claude, com a parte em produção do Gustavo
- [x] **T13** — Checklist de deploy e de rollback — Claude prepara, Gustavo executa

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

## Revisão (T12)

- `./mvnw verify`: 461 testes, nenhuma falha. `node --test "src/test/js/*.test.mjs"`: 172 testes, nenhuma falha.
- **Conferido no navegador (Edge headless, app local):** CA1, CA2 (com um token no `localStorage`), CA3 (`/#/transacoes` termina em `/app/#/transacoes`; com `?token=` o redirect preserva o token e a tela de verificação o tira da barra de propósito), CA8 e CA9 (390, 900, 1280 e 1440px, sem rolagem horizontal).
- **Pendente (Gustavo):** CA4 (sessão aberta na versão antiga e depois na nova) e CA5 (login com Google em `/app/`), que exigem sessão e credenciais reais.
- `web-design-guidelines` e `accessibility-compliance`: acrescentados o link "Pular para o conteúdo", `scroll-padding-top` por causa do topo fixo, `fetchpriority` nos prints da abertura, `text-wrap: balance` nos títulos, `translate="no"` na marca, número dos passos com contraste de 5:1 e ícones decorativos com `aria-hidden`. A linha verde só se anima com `prefers-reduced-motion: no-preference`.
- `responsive-design`: mobile-first, alvos de toque de 44px no topo e no rodapé, print do computador só a partir de 700px (no celular entra o print do celular), linha de projeção só a partir de 1100px.
- **Achado fora da 020:** os campos de data dos filtros do app têm problemas de acessibilidade (por exemplo, o campo "até" sem contexto). Ficou registrado como pendência separada.
- **Corrigido junto:** caminho público inexistente (`/ajuda/`, `/privacidade/`) respondia 500; agora responde 404.

## Checklist de deploy e rollback (T13)

Para o Gustavo executar, junto com a troca do e-mail de envio:

1. Sem migration, então o `pg_dump` não é obrigatório (faça mesmo assim, como de costume).
2. Conferir o Caddy: ele precisa repassar todos os caminhos ao app, sem regra de path que prenda `/` ou `/app/`.
3. Subir a imagem (merge em `main` só com o seu ok).
4. Antes de subir, deixar um navegador logado na versão antiga. Depois do deploy, abrir `/app/` nele (CA4) e testar o login com Google em `/app/` (CA5).
5. Abrir `/`, `/app`, `/#/transacoes` e um link de e-mail novo (`/app/#/verificar-email?token=...`).
6. `/ajuda/` e `/privacidade/` só existirão com as specs 021 e 024; por isso os dois links **saíram do rodapé** por enquanto (decisão do Gustavo). Quando a 021 e a 024 entrarem, voltam ao rodapé, junto com a menção à política de privacidade na pergunta "Meus dados ficam protegidos?".
7. **Rollback:** voltar a imagem anterior. E-mails enviados depois do deploy levam `/app/#/...`, e a versão antiga responde 401 em `/app/`. Se isso importar, subir antes um forward temporário de `/app/` na versão antiga, ou aceitar que esses links falhem.
