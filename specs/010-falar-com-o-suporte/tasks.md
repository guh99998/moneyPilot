# 010 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — `EmailMessage.replyTo`; `ResendEmailSender` envia `reply_to`; `LoggingEmailSender` registra (CA1, CA2)
- [x] **T2** — Pacote `support/`: categoria, request/response, propriedades, referência, exceção, service e controller; limites; `AuthenticatedUserProvider.findCurrentUserId`; `SecurityConfig` (CA1–CA5)
- [x] **T3** — Configuração: `SUPPORT_EMAIL` em prod, `.env.production.example`, `docker-compose.prod.yml` (CA1)
- [x] **T4** — Testes de integração e de configuração (CA1–CA5)
- [x] **T5** — Tela `#/suporte`, rota, item no menu, `api.sendSupportTicket` (CA1, CA3, CA5)
- [x] **T6** — Link na tela de login (CA2)
- [x] **T7** — Testes JS (CA1–CA5)
- [x] **T8** — README: API, variável e cliente web
- [x] **T9** — Validação com as skills do projeto (`web-design-guidelines`, `accessibility-compliance`, `responsive-design`, `api-design-principles`)

## Notas de implementação

Nenhuma descoberta alterou a spec. Detalhes de implementação:

- `SupportService` não é transacional de propósito: o limite só é registrado depois do envio, para a falha (CA5) não gastar a cota.
- O teste de Bearer recusado usa um token inválido (mesmo caminho do expirado: `JwtException` no filtro).
- Os seletores de `profile:updated` no `app.js` ganharam classes próprias (`profile-chip-*`), porque o menu agora tem dois `user-chip`.

**Revisão com as skills do projeto**

- `web-design-guidelines` e `accessibility-compliance`: corrigidos foco no primeiro campo com erro, `name` nos campos, `autocomplete="off"` e placeholder na mensagem. Já atendidos: labels ligados aos campos, `aria-describedby`/`aria-invalid`, erro geral com `role="alert"`, confirmação com `role="status"` focada, ícone decorativo com `aria-hidden`, botão desabilitado com "Enviando…".
- `responsive-design`: tela mobile-first, cartão com `width: min(560px, 100%)`, campos em 16px (sem zoom no iOS), alvos de toque de 44px reaproveitados de `.auth-switch button` e `.user-chip`. Sem largura fixa. Falta conferir visualmente em navegador (celular e desktop).
- `api-design-principles`: recurso em plural sob `/api/v1`, `POST` com `200` e corpo `{reference, replyTo}` (não cria recurso persistido, então não é `201`), erros no `ErrorResponse`, `429` com `Retry-After`, `502` para falha do provedor.
