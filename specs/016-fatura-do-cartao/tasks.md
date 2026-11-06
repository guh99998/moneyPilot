# 016 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende. Testes primeiro.

- [x] **T1** — `InvoiceCycle` (cálculo puro de ciclo e vencimento), com teste unitário (CA3)
- [x] **T2** — Migration V20 + campos em `Account`/`Transaction`, `AccountRequest`/`AccountResponse` e regras 1 e 2 em `AccountService` (CA1, CA2)
- [x] **T3** — `InvoiceService` e `GET /accounts/{id}/invoices` (total, estorno, `invoicesFrom`, pagamento parcial/a mais/atraso, `409` sem dias, `404`) (CA4, CA5, CA9)
- [x] **T4** — Compra parcelada: `POST /transactions/installments`, `409` na edição, apagar grupo (CA6, CA10)
- [x] **T5** — Faturas nas duas previsões (CA7, CA8)
- [ ] **T6** — Contrato OpenAPI, `MoneyScaleIntegrationTest`, isolamento entre usuários (CA9, CA10)
- [ ] **T7** — Conta: fechamento e vencimento no formulário de cartão (CA11)
- [ ] **T8** — Tela Faturas (`views/invoices.js`), menu, pagar fatura e compra parcelada (CA11)
- [ ] **T9** — Dashboard sem cartão em "Guardado" e linha de estado do Perfil (CA8)
- [ ] **T10** — Aviso em Novidades e página na ajuda
- [ ] **T11** — Verificação manual local (desktop e mobile)
- [ ] **T12** — Skills: `web-design-guidelines`, `accessibility-compliance`, `responsive-design`, `api-design-principles`; anotar a revisão aqui
- [ ] **T13** — README, `./mvnw verify` e `node --test`, atualizar o quadro

## Notas de implementação

Descobertas durante a implementação que afetaram a spec ou o plano.

- **Colunas `INTEGER` na V20:** o plano dizia `SMALLINT`, mas o `ddl-auto: validate` do Hibernate recusa `int2` para `Integer`. Usei `INTEGER` (custo desprezível, V20 ainda não aplicada em lugar nenhum).
- **Parcelas (T4):** o limite de parcelas é 2 a 60 (o plano citava "como no plano de títulos", mas o de títulos aceita até 360; 60 basta para cartão). Reaproveitei `InstallmentGroupNotFoundException` (404), que já existe para os títulos.
