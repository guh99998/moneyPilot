# 002 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec com as decisões, plano e tarefas
- [x] **T2** — Migration V12 (verificados, minúsculas, CHECK, tabela de tokens) + `MigrationV12Test` (CA5, CA8)
- [x] **T3** — `EmailAddress.normalize` nos requests de cadastro, login, reenvio e troca de e-mail (CA8)
- [x] **T4** — Helper `markVerified` e ajuste dos testes existentes que cadastram e logam
- [x] **T5** — Cadastro com resposta genérica, link, reaproveitamento de não verificada e aviso ao dono (CA1, CA7)
- [x] **T6** — Login recusa conta não verificada com `403` (CA2)
- [x] **T7** — Confirmação e reenvio do link, com limites (CA3, CA4, CA6)
- [x] **T8** — Testes de integração (CA1–CA8 e bordas)
- [x] **T9** — `api.js` + teste JS
- [x] **T10** — `auth.js` (pós-cadastro, login `403`, reenviar) e `views/verify-email.js` com rota pública
- [x] **T11** — README (API, decisões de domínio, migration)
- [ ] **T12** — Consulta de duplicatas e backup antes do deploy (Gustavo)

## Notas de implementação

- T2: o `MigrationV12Test` confere os dados depois da V12 (verificados, e-mails normalizados, `CHECK` ativo) e que duplicatas por maiúsculas fazem a migration falhar sem alterar nada. Que esses usuários conseguem logar fica para o teste de integração da T8, porque o app só enxerga o schema principal.
