# 008 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec com as decisões, plano e tarefas
- [x] **T2** — JWT com id no subject, provider e filtro (pré-requisito do CA2)
- [x] **T3** — Extrair `OpaqueTokens` de `RefreshTokenService`
- [x] **T4** — Migration V11 + entidade e repositório `EmailChangeToken`
- [x] **T5** — Exceções, handler e `POST /api/v1/profile/email` com limites (CA1, CA3, CA4)
- [x] **T6** — `POST /api/v1/auth/email/confirm`, sessões e aviso (CA2, CA5)
- [x] **T7** — `pendingEmail` no `GET /profile` (CA1)
- [x] **T8** — Testes de integração (CA1–CA6, bordas, JWT)
- [x] **T9** — `api.js` + teste JS
- [x] **T10** — `views/confirm-email.js`, rota pública e formulário no perfil
- [x] **T11** — README (API, decisões de domínio, JWT por id)

## Notas de implementação

- T2–T4 não dependem da 001 e foram feitos primeiro; T5 em diante veio depois da 001.
- Falta a verificação manual no navegador (1280px e 390px), a cargo do Gustavo.
- CA6: só o login com o e-mail antigo é testado; o reset de senha (003) ainda não existe e deve conferir este critério quando for entregue.
