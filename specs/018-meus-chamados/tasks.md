# 018 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende.

- [ ] **T1** — Migration `V16__add_user_index_to_support_tickets.sql` (CA1)
- [ ] **T2** — Repositório: `findAllByUserId`, `findByReferenceAndUserId` e `findAllByUserIdAndStatusChangedAtGreaterThanEqual`; construtor por referência em `SupportTicketNotFoundException` (CA1, CA3, CA4, CA5)
- [ ] **T3** — `MySupportTicketSummaryResponse`, `MySupportTicketDetailResponse` e `MySupportTicketService` (CA1, CA2, CA3, CA4)
- [ ] **T4** — `GET /support/tickets` e `GET /support/tickets/{reference}` no `SupportController`, com OpenAPI no padrão da 011 (CA1, CA3, CA4)
- [ ] **T5** — `MySupportTicketIntegrationTest` (CA1–CA4)
- [ ] **T6** — `AlertKind.SUPPORT_TICKET_UPDATED`, campos novos em `AlertResponse` e alertas de chamado em `AlertService.compute` (CA5, CA6, CA7)
- [ ] **T7** — Testes em `AlertIntegrationTest` e conferência do `OpenApiContractIntegrationTest` (CA5–CA7)
- [ ] **T8** — Cliente: `api.support.mine` e `api.support.get` em `js/api.js` (CA1, CA3)
- [ ] **T9** — Tela `js/views/support.js`: card "Meus chamados", estado vazio, detalhe e recarga após envio (CA1, CA2, CA3)
- [ ] **T10** — `js/alerts.js`: frase, destino, `presetOpenTicket` e `dismissIfActive`; detalhe dispensa o alerta ao abrir (CA6, CA8)
- [ ] **T11** — Testes JS: `support-view.test.mjs` e `alert-targets.test.mjs` (CA1, CA3, CA8)
- [ ] **T12** — README: rotas novas e alerta novo na tabela da 007 (CA1–CA8)
- [ ] **T13** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`, e a checagem manual do plano
- [ ] **T14** — Validação com as skills do projeto (`api-design-principles`, `web-design-guidelines`, `accessibility-compliance`, `responsive-design`) e anotar a revisão aqui
