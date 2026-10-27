# 007 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec aprovada, plano e tarefas
- [ ] **T2** — Migration `V10__create_dismissed_alerts_table.sql`, entidade `DismissedAlert` e repositório
- [ ] **T3** — Queries de títulos em aberto por tipo (a vencer e vencidos) em `BillRepository` (CA1, CA3)
- [ ] **T4** — `AlertService` e `GET /api/v1/alerts`: alertas de títulos (CA1, CA2, CA3, CA7, CA8)
- [ ] **T5** — Alertas de orçamento (80% e estourado) (CA4)
- [ ] **T6** — Dispensar: `POST /api/v1/alerts/dismiss`, fingerprint, volta ao mudar e esquecimento de dispensas obsoletas (CA5, CA8)
- [ ] **T7** — Testes de integração (CA1–CA5, CA7, CA8 e bordas)
- [ ] **T8** — `api.js`: `api.alerts`, evento `api:changed`, com teste JS
- [ ] **T9** — `js/alerts.js` (sino, contador, painel, dispensar) e CSS; sino ao lado do logo em `app.js` (CA1, CA7)
- [ ] **T10** — Clique no alerta leva à tela filtrada: `bills.js` e `budgets.js`, com teste JS (CA6)
- [ ] **T11** — Verificação no Chrome real: desktop e celular
- [ ] **T12** — Atualizar README (API e decisões de domínio)

## Notas de implementação

Descobertas durante a implementação que afetaram a spec ou o plano.
