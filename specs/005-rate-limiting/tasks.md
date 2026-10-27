# 005 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec aprovada, plano e tarefas
- [x] **T2** — `AttemptLimiter`, `RateLimitProperties`, `TooManyAttemptsException` e resposta `429` (CA3)
- [x] **T3** — Limite no login por IP e por e-mail (CA1, CA4, CA5)
- [x] **T4** — Limite no cadastro por IP (CA2, CA5)
- [x] **T5** — Testes de integração (CA1–CA5)
- [x] **T6** — Mensagem de espera no cliente web, com teste JS
- [x] **T7** — Atualizar README

## Notas de implementação

- `AttemptLimiter.check(key, limit)` não recebe a janela (o plano previa `check(key, limit, window)`): a duração fica gravada na janela, passada em `record`.
- Os commits 3 e 4 do plano (login e cadastro) saíram juntos.
- Testes Java escritos mas ainda não executados: Docker Desktop estava desligado.

