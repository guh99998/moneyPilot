# 019 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende.

- [ ] **T1** — `PixProperties` (`app.pix.key`, `app.pix.qr-file`) registrada em `PropertiesConfig`, com `enabled()` (CA4, CA6)
- [ ] **T2** — `ContributionNotConfiguredException` (`404` no `GlobalExceptionHandler`), `PixResponse` e `ContributionService` (chave e QR code como data URI, com limite de 512 KB e `null` se faltar) (CA2, CA4)
- [ ] **T3** — `ContributionController`: `GET /contribution/pix` com OpenAPI no padrão da 011, e `contributionEnabled` no `ProfileResponse` (CA1, CA4)
- [ ] **T4** — Configuração: `PIX_KEY` e `PIX_QR_FILE` em `application-prod.properties`, `.env.production.example` e `docker-compose.prod.yml`, sem `:?` e sem valor real (CA4, CA6)
- [ ] **T5** — `ContributionIntegrationTest` e a rota nova no `OpenApiContractIntegrationTest` (CA1, CA2, CA4)
- [ ] **T6** — Cliente: `session.contribution`, `api.contribution.pix` e a busca do perfil em `app.js` (CA1, CA4)
- [ ] **T7** — Tela `js/views/contribution.js` (gratuito primeiro, chave, "Copiar chave", QR code) e rota `#/apoie` que cai na Visão geral sem a configuração (CA2, CA3, CA4)
- [ ] **T8** — Link "Pagar um cafezinho?" sob o "Sair" e estilo `.foot-link`, só com a configuração (CA1, CA4, CA5)
- [ ] **T9** — Testes JS: `contribution-view.test.mjs` e `session.contribution` em `api.test.mjs` (CA2, CA3, CA4)
- [ ] **T10** — README: tabela da API, decisão de domínio (apoio voluntário por Pix, nada guardado), `PIX_KEY`, `PIX_QR_FILE` e cliente web (CA1–CA6)
- [ ] **T11** — Conferir que nenhuma chave Pix nem imagem de QR code entrou no repositório (CA6)
- [ ] **T12** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`, e a checagem manual do plano (CA1–CA5)
- [ ] **T13** — Validação com as skills do projeto (`api-design-principles`, `web-design-guidelines`, `accessibility-compliance`, `responsive-design`) e anotar a revisão aqui
- [ ] **T14** — Deixar para o Gustavo o passo a passo do deploy: colocar a imagem no servidor, montar o volume e definir `PIX_KEY` e `PIX_QR_FILE`
