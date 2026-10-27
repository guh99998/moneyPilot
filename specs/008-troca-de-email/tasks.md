# 008 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec com as decisões, plano e tarefas
- [ ] **T2** — JWT com id no subject, provider e filtro (pré-requisito do CA2)
- [ ] **T3** — Extrair `OpaqueTokens` de `RefreshTokenService`
- [ ] **T4** — Migration V11 + entidade e repositório `EmailChangeToken`
- [ ] **T5** — Exceções, handler e `POST /api/v1/profile/email` com limites (CA1, CA3, CA4) — **depende da 001**
- [ ] **T6** — `POST /api/v1/auth/email/confirm`, sessões e aviso (CA2, CA5) — **depende da 001**
- [ ] **T7** — `pendingEmail` no `GET /profile` (CA1)
- [ ] **T8** — Testes de integração (CA1–CA6, bordas, JWT)
- [ ] **T9** — `api.js` + teste JS
- [ ] **T10** — `views/confirm-email.js`, rota pública e formulário no perfil
- [ ] **T11** — README (API, decisões de domínio, JWT por id)

## Notas de implementação

- T2–T4 não dependem da 001 e foram feitos primeiro. T5 em diante aguarda o serviço de envio da 001.
- CA6: o reset de senha (003) só é testado se a 003 já tiver sido entregue.
