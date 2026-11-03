# 012 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Exceção `GoogleAccountMismatchException` + `GlobalExceptionHandler` (CA3)
- [x] **T2** — Queries `@Modifying` de remoção por usuário nos repositórios de bills, recorrências, transações, orçamentos, alertas dispensados, chamados, categorias, contas e tokens (CA1)
- [x] **T3** — `DeleteAccountRequest` e `AccountDeletionService` (senha, Google, transação, aviso depois do commit) (CA1–CA6)
- [x] **T4** — Rota `POST /api/v1/profile/deletion` com OpenAPI completo e limpeza do cookie de refresh (CA1, CA2, CA5)
- [x] **T5** — Testes de integração (CA1–CA6, isolamento) e `OpenApiContractIntegrationTest`
- [ ] **T6** — `api.profile.deleteAccount` e cartão "Excluir conta" em `profile.js` com despedida, aviso e campo "EXCLUIR" (CA7)
- [ ] **T7** — Saída da sessão e mensagem "Sua conta foi excluída" no login (CA1)
- [ ] **T8** — Testes JS (CA7 e cliente da API)
- [ ] **T9** — Atualizar `ajuda/perfil-e-seguranca.html`
- [ ] **T10** — README: API e decisão de domínio
- [ ] **T11** — Validação com as skills do projeto (`api-design-principles`, `web-design-guidelines`, `accessibility-compliance`, `responsive-design`)

## Notas de implementação

Descobertas durante a implementação que afetaram a spec ou o plano.
