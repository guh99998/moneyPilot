# 003 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec com as decisões, plano e tarefas
- [x] **T2** — Migration V13 (`password_reset_tokens`), entidade e repositório
- [x] **T3** — Pedido de reset com resposta genérica, link e limites (CA1, CA2)
- [x] **T4** — Redefinição: senha nova, verifica a conta, encerra sessões, desbloqueia login e avisa (CA3, CA4, CA6, CA7)
- [x] **T5** — Testes de integração (CA1–CA7 e bordas)
- [x] **T6** — `api.js` + teste JS
- [x] **T7** — "Esqueci minha senha" em `auth.js` e `views/reset-password.js` com rota pública, + testes de tela (CA5)
- [x] **T8** — README (API, decisões de domínio, migration)
- [ ] **T9** — Backup antes do deploy (Gustavo)

## Notas de implementação
- T7: revisão com as skills de acessibilidade, responsividade e Web Interface Guidelines: foco levado à mensagem de resultado e ao primeiro campo com erro, estado "Enviando…"/"Salvando…", e-mail sem corretor e sem maiúscula automática, mensagem de senha curta em português, `.auth-tagline` quebrando e-mail longo, botões de `.auth-switch` com 44px de alvo de toque e `.auth-wrap` em `100dvh`.
