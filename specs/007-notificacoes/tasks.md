# 007 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec aprovada, plano e tarefas
- [x] **T2** — Migration `V10__create_dismissed_alerts_table.sql`, entidade `DismissedAlert` e repositório
- [x] **T3** — Queries de títulos em aberto por tipo (a vencer e vencidos) em `BillRepository` (CA1, CA3)
- [x] **T4** — `AlertService` e `GET /api/v1/alerts`: alertas de títulos (CA1, CA2, CA3, CA7, CA8)
- [x] **T5** — Alertas de orçamento (80% e estourado) (CA4)
- [x] **T6** — Dispensar: `POST /api/v1/alerts/dismiss`, fingerprint, volta ao mudar e esquecimento de dispensas obsoletas (CA5, CA8)
- [x] **T7** — Testes de integração (CA1–CA5, CA7, CA8 e bordas)
- [x] **T8** — `api.js`: `api.alerts`, evento `api:changed`, com teste JS
- [x] **T9** — `js/alerts.js` (sino, contador, painel, dispensar) e CSS; sino ao lado do logo em `app.js` (CA1, CA7)
- [x] **T10** — Clique no alerta leva à tela filtrada: `bills.js` e `budgets.js`, com teste JS (CA6)
- [x] **T11** — Verificação no Chrome real: desktop e celular
- [x] **T12** — Atualizar README (API e decisões de domínio)

## Notas de implementação

Descobertas durante a implementação que afetaram a spec ou o plano.
- As queries de título (T3), o cálculo (T4, T5) e a dispensa (T6) saíram juntos no mesmo commit do `AlertService`; os testes cobrem as três.
- O mapeamento alerta → filtros ficou em `js/alert-targets.js`, sem DOM nem imports, para ser testado direto no Node (`alert-targets.test.mjs`).
- `svgIcon` foi movido de `app.js` para `ui.js`, porque o sino e o menu usam o mesmo ícone de traço.
- O painel é anexado ao `body` e posicionado com `position: fixed` a partir do sino, para a sidebar não cortá-lo.
- O evento `api:changed` só dispara em requisição que não é `GET`, que deu certo e que não é anônima (login e cadastro não contam).
- Verificado no Chrome real (desktop 1280px e celular 390px) com dois usuários fictícios: contador, painel, Esc e clique fora, dispensar (e continuar escondido após recarregar), clique levando à tela filtrada (vencidas e a vencer), orçamento, baixa de título atualizando o sino e o estado "Tudo em dia". A cor do ícone do sino precisou de uma regra mais específica, porque `.btn-ghost` a sobrescrevia.
