# 017 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende.

- [x] **T1** — Migration `V15__create_support_tickets_table.sql` (CA1, CA7)
- [x] **T2** — `SupportTicketStatus`, entidade `SupportTicket` e `SupportTicketRepository` (CA1, CA3)
- [x] **T3** — `SupportService.send` grava o chamado `OPEN`, marca `emailSent` e segue se o e-mail falhar; referência única com nova tentativa; limite conta sempre que grava (CA1, CA7)
- [x] **T4** — `AdminProperties` (`app.admin.emails`), `AdminAccess`, `AdminOnlyException` (`404` no `GlobalExceptionHandler`) e `admin` no `ProfileResponse` (CA4, CA5)
- [x] **T5** — `AdminSupportTicketController`: listar com filtro e paginação, detalhe e `PATCH` de status, com OpenAPI no padrão da 011 e rotas protegidas no `SecurityConfig` (CA2, CA3, CA4)
- [x] **T6** — Configuração: `ADMIN_EMAILS` em `application-prod.properties`, `.env.production.example` e `docker-compose.prod.yml`, sem `:?` (CA5)
- [x] **T7** — Ajustar `SupportIntegrationTest`: `sendFailure_isBadGateway_andDoesNotSpendTheQuota` passa a provar o CA7 (CA7)
- [x] **T8** — `SupportTicketIntegrationTest` e rotas novas no `OpenApiContractIntegrationTest` (CA1–CA5, CA7)
- [x] **T9** — Cliente: `session.admin`, `api.admin.tickets` e busca do perfil em `app.js` (CA4, CA5)
- [x] **T10** — Tela `js/views/admin-tickets.js`, rota `#/admin/chamados` e item "Chamados" no menu só para administrador (CA2, CA3, CA4, CA6, CA7)
- [x] **T11** — Testes JS: `admin-tickets-view.test.mjs` e menu só para administrador (CA2, CA3, CA4, CA6, CA7)
- [x] **T12** — Exclusão de conta apaga `support_tickets`: a 012 ainda não foi implementada, então fica registrado nas notas e na spec 012 (LGPD)
- [x] **T13** — README: tabela da API, decisão de domínio (admin por e-mail, chamado gravado), `ADMIN_EMAILS` e cliente web
- [x] **T14** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`, e a checagem manual do plano
- [x] **T15** — Validação com as skills do projeto (`api-design-principles`, `web-design-guidelines`, `accessibility-compliance`, `responsive-design`) e anotar a revisão aqui

## Verificação

- `./mvnw verify`: 436 testes, nenhuma falha. `node --test "src/test/js/*.test.mjs"`: 146 testes, nenhuma falha.
- Checagem manual com o app local e `ADMIN_EMAILS` apontando para uma conta de teste: chamado aberto sem login e com login (`200`, gravados como `OPEN`); o administrador lista os abertos, mais recentes primeiro, e muda o status (`200`, com a hora da mudança); `admin` no `/profile` é `true` só para o administrador; outra conta recebe `404` nas rotas do painel e, sem login, `401`.
- No navegador, o item "Chamados" aparece para o administrador e não aparece para a outra conta.

## Notas de implementação

Nenhuma descoberta alterou a spec. Detalhes de implementação:

- `SupportService` continua sem transação de propósito: o chamado é gravado antes do envio e o `emailSent` é atualizado depois, para a falha do provedor não desfazer o chamado.
- O `502` saiu da documentação OpenAPI de `POST /support/tickets`, porque a falha de e-mail não chega mais ao cliente.
- `EmailAddress` passou a ser público para o `AdminProperties` (pacote `config`) reusar a normalização.
- **Menu só para administrador (JS):** o plano previa um teste JS do menu, mas o `app.js` roda `render()` ao ser importado e o DOM falso dos testes não o suporta. O que o menu usa (`session.admin` preenchido pelo `/profile`) tem teste; a ausência do item e a queda da rota para a Visão geral ficam na checagem manual (T14).
- **Exclusão de conta (012):** quando a 012 for implementada, a exclusão precisa apagar `support_tickets` da conta antes de apagar o usuário (a FK `user_id` não tem cascade). A spec 012 já lista isso.

**Revisão com as skills do projeto**

- `api-design-principles`: recursos em plural sob `/api/v1/admin/support/tickets`; `GET` lista paginada (`page`/`size`, máximo de 100 por página, ordem fixa mais recentes primeiro) com filtro `status`; `PATCH` com corpo parcial `{status}` e `200` com o recurso; `404` para inexistente e para quem não é administrador, `401` sem login, `400` status inválido, tudo no `ErrorResponse`; contrato OpenAPI coberto pelo `OpenApiContractIntegrationTest`.
- `web-design-guidelines` e `accessibility-compliance`: já atendidos: `label` ligado ao seletor de status, botão "Abrir" com `aria-label` com a referência, tabela semântica, `h1`/`h2` em ordem, mensagem como texto, badge com texto (não só cor), toast em região `aria-live`. Corrigidos: `autocomplete="off"` nos seletores, foco no título do chamado ao abrir e ao salvar, cabeçalho "Ações" na última coluna.
- `responsive-design`: lista no padrão `table-stack` (vira cartões no celular), filtro no `.filters` existente, mensagem com `overflow-wrap: anywhere` e `pre-wrap`, formulário de status com `flex-wrap`, sem largura fixa. Falta conferir visualmente em navegador (celular e desktop).
