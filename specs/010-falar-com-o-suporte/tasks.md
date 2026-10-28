# 010 — Tarefas

**Plano:** [plan.md](plan.md)

- [ ] **T1** — `EmailMessage.replyTo`; `ResendEmailSender` envia `reply_to`; `LoggingEmailSender` registra (CA1, CA2)
- [ ] **T2** — Pacote `support/`: categoria, request/response, propriedades, referência, exceção, service e controller; limites; `AuthenticatedUserProvider.findCurrentUserId`; `SecurityConfig` (CA1–CA5)
- [ ] **T3** — Configuração: `SUPPORT_EMAIL` em prod, `.env.production.example`, `docker-compose.prod.yml` (CA1)
- [ ] **T4** — Testes de integração e de configuração (CA1–CA5)
- [ ] **T5** — Tela `#/suporte`, rota, item no menu, `api.sendSupportTicket` (CA1, CA3, CA5)
- [ ] **T6** — Link na tela de login (CA2)
- [ ] **T7** — Testes JS (CA1–CA5)
- [ ] **T8** — README: API, variável e cliente web
- [ ] **T9** — Validação com as skills do projeto (`web-design-guidelines`, `accessibility-compliance`, `responsive-design`, `api-design-principles`)

## Notas de implementação

Descobertas durante a implementação que afetaram a spec ou o plano.
