# 006 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec aprovada, plano e tarefas
- [x] **T2** — `revokeAllActiveByUserIdExcept` e `RefreshTokenService.revokeAllExcept` (CA6)
- [x] **T3** — `GET /api/v1/profile` (CA1)
- [x] **T4** — `PUT /api/v1/profile` com strip e validação do nome (CA2, CA3)
- [x] **T5** — Exceções, handler e `POST /api/v1/auth/password` (CA4, CA5)
- [x] **T6** — Encerrar as outras sessões na troca de senha (CA6)
- [x] **T7** — Limite de senha atual errada: props e limiter (borda)
- [x] **T8** — Testes de integração (CA1–CA6 e bordas)
- [x] **T9** — `api.js` (`session.name`, chamadas) com teste JS
- [ ] **T10** — `views/profile.js`, rota e nome clicável no rodapé (CA1, CA2)
- [x] **T11** — Atualizar README (API e decisões de domínio)

## Notas de implementação

- As outras sessões são encerradas **apagando** os refresh tokens, não revogando (o plano previa revogar): o `rotate` trata token revogado reutilizado como roubo e revoga a família inteira, o que derrubaria a sessão que trocou a senha. Sem migration.
- `ProfileService.changePassword` usa `saveAndFlush`: a query de exclusão tem `clearAutomatically` e descartaria a alteração de senha ainda não gravada.
- Os itens de limite (T7) e de rota (T3 a T5) saíram junto com o commit do backend.
